package com.example.chariiptv.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHlsProxyServer @Inject constructor(
    @com.example.chariiptv.di.MediaClient private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "LocalHlsProxyServer"
        private const val STREAM_BUFFER_SIZE = 32 * 1024
        private val TAG_URI_REGEX = Regex("URI=\"([^\"]+)\"")
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val PRIVATE_IP_REGEX = Regex("^(?:127\\.|10\\.|192\\.168\\.|169\\.254\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.)")

        fun getLocalWifiIp(context: Context? = null): String? {
            // 1. Si se provee Context, consultar ConnectivityManager para la interfaz Wi-Fi activa
            if (context != null) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    cm?.allNetworks?.forEach { net ->
                        val caps = cm.getNetworkCapabilities(net)
                        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                        ) {
                            val lp = cm.getLinkProperties(net)
                            for (la in lp?.linkAddresses ?: emptyList()) {
                                val addr = la.address
                                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                    val host = addr.hostAddress
                                    if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                                        return host
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Prioridad en interfaces de red Wi-Fi / Ethernet (wlan*, eth*, wifi*)
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val name = intf.name.lowercase()
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.contains("wifi")) {
                        val addrs = Collections.list(intf.inetAddresses)
                        for (addr in addrs) {
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                val host = addr.hostAddress
                                if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                                    return host
                                }
                            }
                        }
                    }
                }

                // 3. Fallback: buscar subredes locales (192.168.x.x o 172.x.x.x) descartando redes móviles
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val name = intf.name.lowercase()
                    if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") || name.startsWith("wwan")) {
                        continue
                    }
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank() && (host.startsWith("192.168.") || host.startsWith("172."))) {
                                return host
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    var port: Int = 0
        private set

    // Pool elástico sin riesgo de bloqueo del hilo de aceptación y sin límite rígido
    private val workerExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "hls-proxy-worker").apply { isDaemon = true }
    }

    @Volatile
    private var isRunning = false

    init {
        start()
    }

    @Synchronized
    fun start() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        try {
            serverSocket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
            port = serverSocket!!.localPort
            isRunning = true
            Log.i(TAG, "LocalHlsProxyServer iniciado en puerto $port")

            Thread({
                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        try {
                            workerExecutor.execute { handleConnection(clientSocket) }
                        } catch (_: Exception) {
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }, "hls-proxy-acceptor").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando LocalHlsProxyServer", e)
        }
    }

    fun buildProxyUrl(
        streamUrl: String,
        referer: String = "",
        origin: String = "",
        userAgent: String = "",
        autoReferer: Boolean = false
    ): String {
        return buildCastProxyUrl("127.0.0.1", streamUrl, referer, origin, userAgent, autoReferer)
    }

    fun buildCastProxyUrl(
        hostIp: String,
        streamUrl: String,
        referer: String = "",
        origin: String = "",
        userAgent: String = "",
        autoReferer: Boolean = false
    ): String {
        if (!isRunning || port == 0) start()
        val encodedUrl = Uri.encode(streamUrl)
        val encodedReferer = if (referer.isNotBlank()) Uri.encode(referer) else ""
        val encodedOrigin = if (origin.isNotBlank()) Uri.encode(origin) else ""
        val encodedUa = if (userAgent.isNotBlank()) Uri.encode(userAgent) else ""

        var proxyUrl = "http://$hostIp:$port/stream?url=$encodedUrl"
        if (encodedReferer.isNotBlank()) proxyUrl += "&referer=$encodedReferer"
        if (encodedOrigin.isNotBlank()) proxyUrl += "&origin=$encodedOrigin"
        if (encodedUa.isNotBlank()) proxyUrl += "&ua=$encodedUa"
        if (autoReferer) proxyUrl += "&autoRef=1"
        return proxyUrl
    }

    private fun isLocalClientAddress(socket: Socket): Boolean {
        val addr = socket.inetAddress ?: return false
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) return true
        val host = addr.hostAddress ?: ""
        val cleanHost = if (host.startsWith("::ffff:")) host.removePrefix("::ffff:") else host
        return cleanHost.matches(PRIVATE_IP_REGEX)
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 15000
                if (!isLocalClientAddress(s)) {
                    sendForbidden(s)
                    return
                }
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                // Responder de inmediato a peticiones preflight CORS OPTIONS de Chromecast / navegadores
                if (parts[0].equals("OPTIONS", ignoreCase = true)) {
                    val output = s.getOutputStream()
                    val writer = PrintWriter(output)
                    writer.print("HTTP/1.1 204 No Content\r\n")
                    writer.print("Access-Control-Allow-Origin: *\r\n")
                    writer.print("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                    writer.print("Access-Control-Allow-Headers: *\r\n")
                    writer.print("Access-Control-Max-Age: 86400\r\n")
                    writer.print("Connection: close\r\n\r\n")
                    writer.flush()
                    return
                }

                if (!parts[0].equals("GET", ignoreCase = true) && !parts[0].equals("HEAD", ignoreCase = true)) {
                    return
                }

                // Leer cabeceras del cliente y reenviar Range si ExoPlayer lo solicita
                var rangeHeader: String? = null
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    if (headerLine.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = headerLine.substringAfter(":").trim()
                    }
                    headerLine = reader.readLine()
                }

                val rawUri = parts[1]
                val uri = Uri.parse("http://127.0.0.1$rawUri")
                val targetUrl = uri.getQueryParameter("url") ?: run {
                    sendNotFound(s)
                    return
                }

                if (!isSafeStreamUrl(targetUrl)) {
                    Log.w(TAG, "Petición bloqueada por regla Anti-SSRF: $targetUrl")
                    sendForbidden(s)
                    return
                }

                val explicitReferer = uri.getQueryParameter("referer") ?: ""
                val explicitOrigin = uri.getQueryParameter("origin") ?: ""
                val autoReferer = uri.getQueryParameter("autoRef") == "1"
                val userAgent = uri.getQueryParameter("ua")?.ifBlank { DEFAULT_UA } ?: DEFAULT_UA

                val effectiveReferer = explicitReferer.ifBlank {
                    if (autoReferer) deriveReferer(targetUrl) else ""
                }
                val effectiveOrigin = explicitOrigin.ifBlank {
                    if (effectiveReferer.isNotBlank()) deriveOrigin(effectiveReferer) else ""
                }

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")

                if (!rangeHeader.isNullOrBlank()) {
                    reqBuilder.header("Range", rangeHeader)
                }
                if (effectiveReferer.isNotBlank()) {
                    reqBuilder.header("Referer", effectiveReferer)
                }
                if (effectiveOrigin.isNotBlank()) {
                    reqBuilder.header("Origin", effectiveOrigin)
                }

                val rawHost = s.localAddress?.hostAddress?.substringBefore("%") ?: "127.0.0.1"
                val localHost = if (rawHost == "0.0.0.0" || rawHost.startsWith("::") || rawHost.isBlank()) {
                    getLocalWifiIp() ?: "127.0.0.1"
                } else {
                    rawHost
                }

                // Garantiza el cierre de ResponseBody incluso ante Broken Pipe al cambiar de canal
                client.newCall(reqBuilder.build()).execute().use { response ->
                    val output = s.getOutputStream()
                    val writer = PrintWriter(output)

                    val finalUrl = response.request.url.toString()
                    val contentType = response.header("Content-Type") ?: ""
                    val isM3u8 = finalUrl.contains(".m3u8", ignoreCase = true) ||
                            targetUrl.contains(".m3u8", ignoreCase = true) ||
                            contentType.contains("mpegurl", ignoreCase = true)

                    if (response.isSuccessful) {
                        if (isM3u8) {
                            val bodyString = response.body?.string() ?: ""
                            val rewritten = rewriteM3u8(
                                content = bodyString,
                                manifestUrl = finalUrl,
                                referer = explicitReferer,
                                origin = explicitOrigin,
                                ua = userAgent,
                                autoReferer = autoReferer,
                                hostIp = localHost
                            )
                            val bodyBytes = rewritten.toByteArray(Charsets.UTF_8)

                            writer.print("HTTP/1.1 200 OK\r\n")
                            writer.print("Content-Type: application/vnd.apple.mpegurl\r\n")
                            writer.print("Content-Length: ${bodyBytes.size}\r\n")
                            writer.print("Access-Control-Allow-Origin: *\r\n")
                            writer.print("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                            writer.print("Access-Control-Allow-Headers: *\r\n")
                            writer.print("Connection: close\r\n\r\n")
                            writer.flush()
                            output.write(bodyBytes)
                            output.flush()
                        } else {
                            writer.print("HTTP/1.1 ${response.code} ${response.message}\r\n")
                            response.headers.forEach { pair ->
                                val name = pair.first
                                if (!name.equals("Transfer-Encoding", ignoreCase = true) &&
                                    !name.equals("Connection", ignoreCase = true) &&
                                    !name.startsWith("Access-Control-", ignoreCase = true)
                                ) {
                                    writer.print("$name: ${pair.second}\r\n")
                                }
                            }
                            writer.print("Access-Control-Allow-Origin: *\r\n")
                            writer.print("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                            writer.print("Access-Control-Allow-Headers: *\r\n")
                            writer.print("Connection: close\r\n\r\n")
                            writer.flush()
                            response.body?.byteStream()?.use { input ->
                                input.copyTo(output, bufferSize = STREAM_BUFFER_SIZE)
                            }
                            output.flush()
                        }
                    } else {
                        writer.print("HTTP/1.1 ${response.code} ${response.message}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
                        writer.flush()
                    }
                }
            }
        } catch (_: Exception) {
            // Socket y ResponseBody ya quedan cerrados por los bloques use {}
        }
    }

    private fun rewriteM3u8(
        content: String,
        manifestUrl: String,
        referer: String,
        origin: String,
        ua: String,
        autoReferer: Boolean,
        hostIp: String = "127.0.0.1"
    ): String {
        val baseUri = Uri.parse(manifestUrl)
        val sb = StringBuilder(content.length + 512)

        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                sb.append("\n")
                return@forEach
            }
            if (trimmed.startsWith("#")) {
                val hasRewriteableUri = (
                    trimmed.startsWith("#EXT-X-KEY:") ||
                    trimmed.startsWith("#EXT-X-MAP:") ||
                    trimmed.startsWith("#EXT-X-MEDIA:") ||
                    trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF:")
                ) && trimmed.contains("URI=\"")

                if (hasRewriteableUri) {
                    val rewrittenTag = TAG_URI_REGEX.replace(trimmed) { match ->
                        val rawTagUri = match.groupValues[1]
                        val resolvedTagUri = resolveUrl(baseUri, rawTagUri)
                        val proxyTagUri = buildCastProxyUrl(hostIp, resolvedTagUri, referer, origin, ua, autoReferer)
                        "URI=\"$proxyTagUri\""
                    }
                    sb.append(rewrittenTag).append("\n")
                    return@forEach
                }
                sb.append(trimmed).append("\n")
            } else {
                val resolvedUrl = resolveUrl(baseUri, trimmed)
                val proxySegmentUrl = buildCastProxyUrl(hostIp, resolvedUrl, referer, origin, ua, autoReferer)
                sb.append(proxySegmentUrl).append("\n")
            }
        }
        return sb.toString()
    }

    private fun resolveUrl(base: Uri, relativeOrAbsolute: String): String {
        return try {
            val baseUri = java.net.URI(base.toString())
            baseUri.resolve(relativeOrAbsolute).toString()
        } catch (_: Exception) {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else if (relativeOrAbsolute.startsWith("/")) {
                "${base.scheme}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$relativeOrAbsolute"
            } else {
                val lastSlash = base.toString().lastIndexOf('/')
                val basePath = if (lastSlash != -1) base.toString().substring(0, lastSlash + 1) else "$base/"
                basePath + relativeOrAbsolute
            }
        }
    }

    private fun deriveReferer(targetUrl: String): String {
        return try {
            val uri = Uri.parse(targetUrl)
            "${uri.scheme}://${uri.host}/"
        } catch (_: Exception) {
            ""
        }
    }

    private fun deriveOrigin(referer: String): String {
        return try {
            if (referer.isBlank()) return ""
            val uri = Uri.parse(referer)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            ""
        }
    }

    private fun isSafeStreamUrl(rawUrl: String): Boolean {
        return try {
            val parsed = Uri.parse(rawUrl)
            val scheme = parsed.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            val host = parsed.host?.lowercase() ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") return false
            if (host.matches(PRIVATE_IP_REGEX)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendForbidden(socket: Socket) {
        try {
            val writer = PrintWriter(socket.getOutputStream())
            writer.print("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\nAcceso bloqueado por regla Anti-SSRF")
            writer.flush()
            socket.close()
        } catch (_: Exception) {}
    }

    private fun sendNotFound(socket: Socket) {
        try {
            val writer = PrintWriter(socket.getOutputStream())
            writer.print("HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
            writer.flush()
            socket.close()
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}
