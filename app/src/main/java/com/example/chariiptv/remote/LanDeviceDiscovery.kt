package com.example.chariiptv.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanDeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LanDeviceDiscovery"
        const val SERVICE_TYPE = "_chari-tv._tcp."
        const val DEFAULT_TV_PORT = 8990
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val resolveQueue = CoroutineChannel<NsdServiceInfo>(CoroutineChannel.UNLIMITED)
    private val resolveMutex = Mutex()
    private var resolveWorkerJob: Job? = null
    private var manualProbeJob: Job? = null

    init {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        startResolveWorker()
    }

    private fun startResolveWorker() {
        resolveWorkerJob?.cancel()
        resolveWorkerJob = scope.launch {
            for (serviceInfo in resolveQueue) {
                resolveMutex.withLock {
                    resolveServiceSafely(serviceInfo)
                }
            }
        }
    }

    @Synchronized
    fun startDiscovery() {
        if (_isScanning.value) return

        acquireMulticastLock()
        _isScanning.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Descubrimiento mDNS iniciado: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Servicio mDNS encontrado: ${service.serviceName}, tipo: ${service.serviceType}")
                if (service.serviceType.contains("chari-tv")) {
                    resolveQueue.trySend(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Servicio mDNS perdido: ${service.serviceName}")
                _discoveredTvs.update { list ->
                    list.filter { it.name != service.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Descubrimiento mDNS detenido")
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Fallo al iniciar descubrimiento mDNS: errorCode=$errorCode")
                _isScanning.value = false
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Fallo al detener descubrimiento mDNS: errorCode=$errorCode")
                _isScanning.value = false
                releaseMulticastLock()
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción iniciando NsdManager discovery", e)
            _isScanning.value = false
        }

        // Además del listener mDNS, ejecutamos sondeo silencioso en la subred local
        probeSubnetForTvs()
    }

    private fun resolveServiceSafely(serviceInfo: NsdServiceInfo) {
        val resolverLock = java.lang.Object()
        var isResolved = false

        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Fallo al resolver servicio mDNS ${serviceInfo.serviceName}: errorCode=$errorCode")
                    synchronized(resolverLock) {
                        isResolved = true
                        resolverLock.notifyAll()
                    }
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val hostAddress = resolvedInfo.host?.hostAddress
                    val port = resolvedInfo.port

                    var model = ""
                    var brand = ""
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            val attrs = resolvedInfo.attributes
                            model = attrs["model"]?.let { String(it, Charsets.UTF_8) } ?: ""
                            brand = attrs["brand"]?.let { String(it, Charsets.UTF_8) } ?: ""
                        } catch (_: Exception) {}
                    }

                    if (!hostAddress.isNullOrBlank() && port > 0) {
                        val tv = DiscoveredTv(
                            name = resolvedInfo.serviceName,
                            host = hostAddress,
                            port = port,
                            model = model,
                            brand = brand
                        )
                        addOrUpdateDiscoveredTv(tv)
                    }

                    synchronized(resolverLock) {
                        isResolved = true
                        resolverLock.notifyAll()
                    }
                }
            })

            // Esperar resolución con timeout máximo de 1.5s para no bloquear la cola
            synchronized(resolverLock) {
                if (!isResolved) {
                    resolverLock.wait(1500)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante resolveService", e)
        }
    }

    fun addOrUpdateDiscoveredTv(tv: DiscoveredTv) {
        _discoveredTvs.update { currentList ->
            val index = currentList.indexOfFirst {
                (it.name.isNotBlank() && tv.name.isNotBlank() && it.name == tv.name) ||
                (it.host == tv.host && it.port == tv.port)
            }
            if (index >= 0) {
                currentList.toMutableList().apply { set(index, tv) }
            } else {
                currentList + tv
            }
        }
        Log.i(TAG, "TV Registrada: ${tv.displayName} en http://${tv.host}:${tv.port}")
    }

    /**
     * Sondeo directo por Socket en el puerto 8990 de la subred local Wi-Fi.
     * Garantiza el descubrimiento incluso si el router bloquea paquetes mDNS/IGMP.
     */
    fun probeSubnetForTvs() {
        manualProbeJob?.cancel()
        manualProbeJob = scope.launch(Dispatchers.IO) {
            val localIp = getLocalWifiIp() ?: return@launch
            val prefix = localIp.substringBeforeLast(".") + "."

            // Comprobar IPs comunes y alrededor de la IP local
            val localOctet = localIp.substringAfterLast(".").toIntOrNull() ?: 100
            val candidateOctets = buildList {
                // Rango DHCP doméstico típico: 2..50
                for (i in 2..50) add(i)
                // Octetos contiguos a este teléfono
                for (offset in -15..15) {
                    val target = localOctet + offset
                    if (target in 2..254) add(target)
                }
                // IPs fijas altas típicas
                addAll(listOf(100, 101, 102, 110, 120, 150, 156, 160, 200))
            }.distinct()

            // Lanzar en paralelo para escanear en menos de 500 ms
            candidateOctets.forEach { octet ->
                val targetIp = "$prefix$octet"
                if (targetIp != localIp) {
                    launch {
                        checkIpForTvServer(targetIp, DEFAULT_TV_PORT)
                    }
                }
            }
        }
    }

    private fun checkIpForTvServer(ip: String, port: Int) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 250)
                // Si conecta exitosamente, intentar obtener nombre y modelo
                var model = "TV Box / Smart TV"
                var brand = "Android TV"
                var name = "Chari-TV-$ip"
                try {
                    val url = java.net.URL("http://$ip:$port/api/info")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 350
                    conn.readTimeout = 350
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(text)
                        model = json.optString("model", model)
                        brand = json.optString("brand", brand)
                        name = "Chari-TV-${if (model.isNotBlank()) model else ip}"
                    }
                    conn.disconnect()
                } catch (_: Exception) {}

                val tv = DiscoveredTv(
                    name = name,
                    host = ip,
                    port = port,
                    model = model,
                    brand = brand
                )
                addOrUpdateDiscoveredTv(tv)
            }
        } catch (_: Exception) {}
    }

    private fun getLocalWifiIp(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return null
            val lp: LinkProperties = cm.getLinkProperties(network) ?: return null
            val linkAddress = lp.linkAddresses.firstOrNull {
                val host = it.address.hostAddress ?: ""
                !it.address.isLoopbackAddress && host.contains(".") && !host.startsWith("10.34.")
            }
            linkAddress?.address?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun stopDiscovery() {
        if (!_isScanning.value) return
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo discovery", e)
        } finally {
            _isScanning.value = false
            discoveryListener = null
            releaseMulticastLock()
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("chari_iptv_multicast_lock")?.apply {
                setReferenceCounted(true)
            }
        }
        try {
            multicastLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }
}
