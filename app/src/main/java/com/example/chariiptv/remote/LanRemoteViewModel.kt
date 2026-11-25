package com.example.chariiptv.remote

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chariiptv.domain.Channel
import com.example.chariiptv.domain.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanRemoteViewModel @Inject constructor(
    private val discovery: LanDeviceDiscovery,
    private val client: TvLanClient,
    private val repository: ChannelRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val discoveredTvs: StateFlow<List<DiscoveredTv>> = discovery.discoveredTvs
    val isScanning: StateFlow<Boolean> = discovery.isScanning

    private val _selectedTv = MutableStateFlow<DiscoveredTv?>(null)
    val selectedTv: StateFlow<DiscoveredTv?> = _selectedTv.asStateFlow()

    private val _isRemoteSheetVisible = MutableStateFlow(false)
    val isRemoteSheetVisible: StateFlow<Boolean> = _isRemoteSheetVisible.asStateFlow()

    private val _tvStatus = MutableStateFlow<TvStatus?>(null)
    val tvStatus: StateFlow<TvStatus?> = _tvStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var statusPollingJob: Job? = null

    init {
        // Auto-seleccionar televisor cuando se descubre o actualizar si cambia de IP
        viewModelScope.launch {
            discovery.discoveredTvs.collect { tvs ->
                val current = _selectedTv.value
                if (current == null && tvs.isNotEmpty()) {
                    _selectedTv.value = tvs.first()
                    startStatusPolling()
                } else if (current != null) {
                    val matching = tvs.find { 
                        (it.name.isNotBlank() && it.name == current.name) || (it.host == current.host && it.port == current.port)
                    }
                    if (matching != null && (matching.host != current.host || matching.port != current.port)) {
                        _selectedTv.value = matching
                        refreshTvStatus()
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        discovery.startDiscovery()
    }

    fun stopDiscovery() {
        discovery.stopDiscovery()
    }

    fun rescan() {
        discovery.stopDiscovery()
        discovery.startDiscovery()
        discovery.probeSubnetForTvs()
    }

    fun selectTv(tv: DiscoveredTv) {
        _selectedTv.value = tv
        startStatusPolling()
    }

    fun setRemoteSheetVisible(visible: Boolean) {
        _isRemoteSheetVisible.value = visible
        if (visible) {
            discovery.startDiscovery()
            startStatusPolling()
        } else {
            statusPollingJob?.cancel()
        }
    }

    fun playChannelOnTv(channel: Channel, onComplete: ((Boolean) -> Unit)? = null) {
        val tv = _selectedTv.value ?: discovery.discoveredTvs.value.firstOrNull()
        if (tv == null) {
            _userMessage.value = "No se encontró ninguna televisión en la red Wi-Fi"
            onComplete?.invoke(false)
            return
        }

        viewModelScope.launch {
            _selectedTv.value = tv
            val result = client.playChannelOnTv(tv, channel)
            if (result.isSuccess) {
                _userMessage.value = "Transmitiendo ${channel.name} en ${tv.displayName}"
                onComplete?.invoke(true)
                delay(600)
                refreshTvStatus()
            } else {
                _userMessage.value = "Error al enviar señal a la televisión"
                onComplete?.invoke(false)
            }
        }
    }

    fun sendKey(key: String) {
        val tv = _selectedTv.value ?: return
        viewModelScope.launch {
            val result = client.sendRemoteKey(tv, key)
            if (result.isFailure) {
                refreshTvStatus()
            }
        }
    }

    fun stopTvPlayback() {
        val tv = _selectedTv.value ?: return
        viewModelScope.launch {
            client.stopTvPlayback(tv)
            refreshTvStatus()
        }
    }

    fun sendSearchText(text: String) {
        val tv = _selectedTv.value ?: return
        viewModelScope.launch {
            client.sendSearchQuery(tv, text)
            _userMessage.value = "Búsqueda enviada a la TV"
        }
    }

    fun syncFavoritesToTv() {
        val tv = _selectedTv.value ?: return
        Log.i("LanRemoteViewModel", "syncFavoritesToTv() invocado para ${tv.displayName} (${tv.baseUrl})")
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // 1. Obtener favoritos locales del celular
                val favChannels = repository.getFavoriteChannels()
                Log.i("LanRemoteViewModel", "Favoritos locales en celular: ${favChannels.size}")
                // 2. Intercambio bidireccional con la TV
                val result = client.syncFavorites(tv, favChannels)
                if (result.isSuccess) {
                    val mergedList = result.getOrNull() ?: emptyList()
                    Log.i("LanRemoteViewModel", "Fusión exitosa con TV: ${mergedList.size} favoritos devueltos")
                    // 3. Guardar en el celular la lista fusionada proveniente de la TV
                    repository.syncFavorites(mergedList)
                    _userMessage.value = "¡Favoritos mezclados! ${mergedList.size} canales activos en Celular y TV"
                } else {
                    Log.e("LanRemoteViewModel", "Fallo al sincronizar: ${result.exceptionOrNull()?.message}")
                    _userMessage.value = "Error al sincronizar favoritos con la TV"
                }
            } catch (e: Exception) {
                Log.e("LanRemoteViewModel", "Excepción en syncFavoritesToTv", e)
                _userMessage.value = "Error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            while (true) {
                refreshTvStatus()
                delay(2500)
            }
        }
    }

    private suspend fun refreshTvStatus() {
        val tv = _selectedTv.value ?: return
        val result = client.getTvStatus(tv)
        if (result.isSuccess) {
            _tvStatus.value = result.getOrNull()
        } else {
            _tvStatus.value = null
            // Si la TV seleccionada no responde, sondear subred para verificar si cambió de IP
            discovery.probeSubnetForTvs()
        }
    }
}
