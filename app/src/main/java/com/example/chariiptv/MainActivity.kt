package com.example.chariiptv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.example.chariiptv.domain.Channel
import com.example.chariiptv.domain.GhibliPlaybackStore
import com.example.chariiptv.player.ExoPlayerView
import com.example.chariiptv.player.LocalHlsProxyServer
import com.example.chariiptv.player.PlayerEntryPoint
import com.example.chariiptv.presentation.TvViewModel
import com.example.chariiptv.presentation.components.ImportantModalDialog
import com.example.chariiptv.presentation.components.MobileChannelCard
import com.example.chariiptv.presentation.components.MobileGhibliCard
import com.example.chariiptv.presentation.components.MobileTopBar
import com.example.chariiptv.theme.*
import com.example.chariiptv.util.Constants
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import com.example.chariiptv.remote.LanRemoteViewModel
import com.example.chariiptv.presentation.components.VirtualRemoteSheet

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: TvViewModel by viewModels()
    private val lanRemoteViewModel: LanRemoteViewModel by viewModels()
    private var isPlayingChannel = false

    override fun onStart() {
        super.onStart()
        lanRemoteViewModel.startDiscovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        try {
            CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            TVDROIDTheme {
                val uiState by viewModel.uiState.collectAsState()

                val discoveredTvs by lanRemoteViewModel.discoveredTvs.collectAsState()
                val selectedTv by lanRemoteViewModel.selectedTv.collectAsState()
                val isRemoteSheetVisible by lanRemoteViewModel.isRemoteSheetVisible.collectAsState()
                val isScanning by lanRemoteViewModel.isScanning.collectAsState()
                val tvStatus by lanRemoteViewModel.tvStatus.collectAsState()
                val isSyncing by lanRemoteViewModel.isSyncing.collectAsState()
                val remoteUserMessage by lanRemoteViewModel.userMessage.collectAsState()

                var notificationMessage by remember { mutableStateOf<String?>(null) }
                var showImportantDialog by remember { mutableStateOf(false) }

                LaunchedEffect(remoteUserMessage) {
                    if (remoteUserMessage != null) {
                        notificationMessage = remoteUserMessage
                        lanRemoteViewModel.clearUserMessage()
                    }
                }

                isPlayingChannel = (uiState.currentChannel != null)

                val gridState = rememberLazyGridState()

                // Reiniciar el scroll arriba solo cuando cambia la categoría o el país seleccionado
                LaunchedEffect(uiState.selectedFilterId, uiState.selectedCountryCode) {
                    gridState.scrollToItem(0)
                }

                BackHandler(enabled = uiState.isSearchActive) {
                    viewModel.setSearchActive(false)
                }

                LaunchedEffect(notificationMessage) {
                    if (notificationMessage != null) {
                        delay(2000)
                        notificationMessage = null
                    }
                }

                Scaffold(
                    topBar = {
                        if (uiState.currentChannel == null) {
                            MobileTopBar(
                                searchQuery = uiState.searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                isSearchActive = uiState.isSearchActive,
                                onSearchActiveChange = { viewModel.setSearchActive(it) },
                                selectedFilterId = uiState.selectedFilterId,
                                onFilterSelected = { viewModel.selectFilter(it) },
                                selectedCountryCode = uiState.selectedCountryCode,
                                onCountrySelected = { viewModel.selectCountry(it) },
                                onImportantClick = { showImportantDialog = true },
                                onOpenRemote = { lanRemoteViewModel.setRemoteSheetVisible(true) },
                                hasDiscoveredTv = discoveredTvs.isNotEmpty(),
                                rssNews = uiState.rssNews
                            )
                        }
                    },
                    snackbarHost = {
                        if (notificationMessage != null) {
                            Snackbar(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .navigationBarsPadding(),
                                containerColor = MobileSurfaceFocused,
                                contentColor = Color.White
                            ) {
                                Text(notificationMessage ?: "", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MobileBackground)
                    ) {
                        // Cuadrícula Principal de Canales / Películas (siempre montada para conservar la posición exacta de scroll)
                        val channels = uiState.filteredChannels
                        val isGhibli = uiState.selectedFilterId == "ghibli"

                        if (uiState.isLoading || (uiState.isSearchActive && uiState.isSearching && channels.isEmpty())) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MobilePrimary, strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (uiState.isSearchActive) "Buscando en toda la app..." else "Cargando catálogo...",
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else if (channels.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = if (uiState.isSearchActive) "🔍" else "📺",
                                        fontSize = 48.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (uiState.isSearchActive && uiState.searchQuery.isNotEmpty()) {
                                            "No se encontraron resultados para '${uiState.searchQuery}' en toda la app."
                                        } else if (uiState.isSearchActive) {
                                            "Escribe el nombre de un canal o película para buscar en toda la app."
                                        } else if (uiState.selectedFilterId == "fav") {
                                            "Aún no tienes canales favoritos.\nMantén presionado cualquier canal para añadirlo."
                                        } else {
                                            "No hay canales disponibles en esta sección."
                                        },
                                        color = Color.LightGray,
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isSearchActive && uiState.searchQuery.isNotEmpty()) {
                                            "${channels.size} resultados encontrados en toda la app"
                                        } else {
                                            "${channels.size} ${if (isGhibli) "obras cinematográficas" else "canales activos"}"
                                        },
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (!uiState.isSearchActive && uiState.selectedFilterId == "countries") {
                                        Text(
                                            text = Constants.COUNTRY_FLAGS[uiState.selectedCountryCode] ?: "",
                                            fontSize = 16.sp
                                        )
                                    }
                                }

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Adaptive(minSize = if (isGhibli) 110.dp else 95.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    itemsIndexed(
                                        items = channels,
                                        key = { index, channel -> "${channel.name}_${channel.url}_$index" }
                                    ) { _, channel ->
                                        val isFav = uiState.favorites.contains(channel.url)

                                        if (isGhibli) {
                                            MobileGhibliCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onClick = { viewModel.playChannel(channel) },
                                                onLongClick = {
                                                    viewModel.toggleFavorite(channel)
                                                    notificationMessage = if (isFav) {
                                                        getString(R.string.removed_from_favorites)
                                                    } else {
                                                        getString(R.string.added_to_favorites)
                                                    }
                                                },
                                                onPlayOnTv = {
                                                    viewModel.playChannel(null)
                                                    lanRemoteViewModel.playChannelOnTv(channel) { success ->
                                                        if (success) {
                                                            lanRemoteViewModel.setRemoteSheetVisible(true)
                                                        }
                                                    }
                                                }
                                            )
                                        } else {
                                            MobileChannelCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onClick = { viewModel.playChannel(channel) },
                                                onLongClick = {
                                                    viewModel.toggleFavorite(channel)
                                                    notificationMessage = if (isFav) {
                                                        getString(R.string.removed_from_favorites)
                                                    } else {
                                                        getString(R.string.added_to_favorites)
                                                    }
                                                },
                                                onPlayOnTv = {
                                                    viewModel.playChannel(null)
                                                    lanRemoteViewModel.playChannelOnTv(channel) { success ->
                                                        if (success) {
                                                            lanRemoteViewModel.setRemoteSheetVisible(true)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Vista de Reproducción en Capa Superior (ExoPlayer Móvil o Google Cast)
                        if (uiState.currentChannel != null) {
                            val activeChannel = uiState.currentChannel!!

                            DisposableEffect(Unit) {
                                // Permitir rotación libre y natural: solo cambia si el usuario gira físicamente el teléfono
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                onDispose {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }

                            BackHandler {
                                viewModel.playChannel(null)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                CastOrLocalPlayerView(
                                    channel = activeChannel,
                                    isFavorite = uiState.favorites.contains(activeChannel.url),
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(activeChannel)
                                        val isFav = uiState.favorites.contains(activeChannel.url)
                                        notificationMessage = if (isFav) {
                                            getString(R.string.removed_from_favorites)
                                        } else {
                                            getString(R.string.added_to_favorites)
                                        }
                                    },
                                    onClose = { viewModel.playChannel(null) },
                                    onSendToTv = {
                                        viewModel.playChannel(null)
                                        lanRemoteViewModel.playChannelOnTv(activeChannel) { success ->
                                            if (success) {
                                                lanRemoteViewModel.setRemoteSheetVisible(true)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Panel de Control Remoto Virtual LAN
                        if (isRemoteSheetVisible) {
                            VirtualRemoteSheet(
                                selectedTv = selectedTv,
                                discoveredTvs = discoveredTvs,
                                isScanning = isScanning,
                                tvStatus = tvStatus,
                                isSyncing = isSyncing,
                                onSelectTv = { lanRemoteViewModel.selectTv(it) },
                                onRescan = { lanRemoteViewModel.rescan() },
                                onSendKey = { lanRemoteViewModel.sendKey(it) },
                                onStopPlayback = { lanRemoteViewModel.stopTvPlayback() },
                                onSyncFavorites = { lanRemoteViewModel.syncFavoritesToTv() },
                                onSendSearchText = { lanRemoteViewModel.sendSearchText(it) },
                                onDismiss = { lanRemoteViewModel.setRemoteSheetVisible(false) }
                            )
                        }

                        // Diálogo modal "Importante" (Donaciones PayPal, Yape y Web Oficial)
                        if (showImportantDialog) {
                            ImportantModalDialog(onDismiss = { showImportantDialog = false })
                        }

                        // Diálogo de error global
                        if (uiState.errorMessage != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.clearError() },
                                title = { Text("Aviso", color = Color.White) },
                                text = { Text(uiState.errorMessage ?: "", color = Color.LightGray) },
                                containerColor = MobileSurface,
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.clearError() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
                                    ) {
                                        Text("Aceptar", color = Color.White)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingChannel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lanRemoteViewModel.stopDiscovery()
        val isCasting = try {
            CastContext.getSharedInstance(this).sessionManager.currentCastSession?.isConnected == true
        } catch (_: Exception) { false }

        // Si está transmitiendo a Chromecast en la TV, la transmisión debe continuar en la TV
        // aunque el usuario bloquee el celular o cambie de aplicación.
        if (isCasting) {
            return
        }

        // Si no está activamente en modo Picture-in-Picture, detener inmediatamente la reproducción local
        // para evitar fugas de audio en segundo plano al salir o minimizar la app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Permitir continuidad de reproducción en ventana flotante PiP
        } else {
            viewModel.playChannel(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.playChannel(null)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Si el usuario cerró la ventana flotante PiP (deslizada hacia la X)
        if (!isInPictureInPictureMode && lifecycle.currentState < androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.playChannel(null)
        }
    }
}

@Composable
fun CastOrLocalPlayerView(
    channel: Channel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onSendToTv: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val localProxyServer = remember(context) {
        try {
            EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java).localHlsProxyServer()
        } catch (_: Exception) {
            null
        }
    }
    val castContext = remember {
        try { CastContext.getSharedInstance(context) } catch (e: Exception) { null }
    }

    var currentCastSession by remember {
        mutableStateOf(castContext?.sessionManager?.currentCastSession)
    }

    DisposableEffect(castContext) {
        val sessionManager = castContext?.sessionManager
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                currentCastSession = session
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                currentCastSession = session
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                currentCastSession = null
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                currentCastSession = null
            }
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                currentCastSession = null
            }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                currentCastSession = null
            }
        }
        try {
            sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        } catch (_: Exception) {}

        onDispose {
            try {
                sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
            } catch (_: Exception) {}
        }
    }

    val isCasting = currentCastSession?.isConnected == true

    if (isCasting && currentCastSession != null) {
        val session = currentCastSession!!
        val remoteMediaClient = session.remoteMediaClient
        var isPlayingOnCast by remember { mutableStateOf(remoteMediaClient?.isPlaying == true) }
        var isBufferingOnCast by remember { mutableStateOf(remoteMediaClient?.isBuffering == true) }

        var castErrorMessage by remember { mutableStateOf<String?>(null) }

        DisposableEffect(remoteMediaClient) {
            val callback = object : RemoteMediaClient.Callback() {
                override fun onStatusUpdated() {
                    isPlayingOnCast = remoteMediaClient?.isPlaying == true
                    isBufferingOnCast = remoteMediaClient?.isBuffering == true
                    val mediaStatus = remoteMediaClient?.mediaStatus
                    if (mediaStatus != null && mediaStatus.idleReason == com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR) {
                        castErrorMessage = "La televisión no pudo cargar esta señal (formato o enlace no admitido por Chromecast)"
                    } else if (isPlayingOnCast) {
                        castErrorMessage = null
                    }
                }
            }
            remoteMediaClient?.registerCallback(callback)
            onDispose {
                remoteMediaClient?.unregisterCallback(callback)
            }
        }

        // Transmisión activa a Chromecast con detección dinámica de VOD vs En Vivo
        LaunchedEffect(channel, session) {
            castErrorMessage = null
            val isVod = channel.category.equals("ghibli", ignoreCase = true) ||
                    channel.url.endsWith(".mp4", ignoreCase = true) ||
                    channel.url.contains(".mp4?", ignoreCase = true)

            val streamType = if (isVod) MediaInfo.STREAM_TYPE_BUFFERED else MediaInfo.STREAM_TYPE_LIVE
            val contentType = when {
                channel.url.contains(".mp4", ignoreCase = true) -> "video/mp4"
                channel.url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                channel.url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                else -> "application/x-mpegurl"
            }

            // Usar MEDIA_TYPE_GENERIC para IPTV en vivo (evita el error 2001 INVALID_REQUEST de MEDIA_TYPE_TV_SHOW)
            val mediaType = if (isVod) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_GENERIC
            val metadata = MediaMetadata(mediaType).apply {
                putString(MediaMetadata.KEY_TITLE, channel.name)
                putString(MediaMetadata.KEY_SUBTITLE, if (isVod) "Studio Ghibli • Película" else "Emisión en Directo")
            }
            if (channel.logo.isNotEmpty() && !channel.logo.contains(".svg", ignoreCase = true)) {
                try {
                    metadata.addImage(com.google.android.gms.common.images.WebImage(Uri.parse(channel.logo)))
                } catch (_: Exception) {}
            }

            // Para transmisiones en vivo / HLS, transmitimos a través del proxy local del celular
            // en la red Wi-Fi compartida. Esto neutraliza las políticas CORS estrictas (como Pluto TV:
            // 'Access-Control-Allow-Origin: http://pluto.tv') e inyecta Referer / User-Agent correctos
            // garantizando que el receptor Chromecast de la TV cargue los segmentos sin error.
            val wifiIp = LocalHlsProxyServer.getLocalWifiIp(context)
            val effectiveCastUrl = if (!isVod && wifiIp != null && localProxyServer != null) {
                localProxyServer.buildCastProxyUrl(
                    hostIp = wifiIp,
                    streamUrl = channel.url,
                    referer = channel.referer,
                    origin = "",
                    userAgent = channel.userAgent,
                    autoReferer = true
                )
            } else {
                channel.url
            }

            android.util.Log.i("CastPlayer", "Iniciando Cast con URL: $effectiveCastUrl (isVod=$isVod, wifiIp=$wifiIp)")

            val mediaInfo = MediaInfo.Builder(effectiveCastUrl)
                .setStreamType(streamType)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()

            val requestBuilder = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)

            if (isVod) {
                val savedInfo = GhibliPlaybackStore.getSavedPosition(context, channel.name)
                if (savedInfo != null && savedInfo.first >= GhibliPlaybackStore.MIN_RESUME_THRESHOLD_MS) {
                    requestBuilder.setCurrentTime(savedInfo.first)
                }
            }

            // Esperar a que remoteMediaClient esté enlazado al receptor
            var client = session.remoteMediaClient
            var attempts = 0
            while (client == null && attempts < 20) {
                delay(200)
                client = session.remoteMediaClient
                attempts++
            }

            if (client != null) {
                val pending = client.load(requestBuilder.build())
                pending?.setResultCallback { result ->
                    if (!result.status.isSuccess) {
                        castErrorMessage = "Error al iniciar en TV: ${result.status.statusMessage ?: "Señal rechazada por Chromecast"}"
                    }
                }
            } else {
                castErrorMessage = "No se pudo establecer el canal de medios con la TV"
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Barra superior del control de Cast
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver al catálogo",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Botón de gestión de dispositivo Google Cast
                AndroidView(
                    modifier = Modifier.size(40.dp),
                    factory = { ctx ->
                        MediaRouteButton(ctx).apply {
                            try {
                                CastButtonFactory.setUpMediaRouteButton(ctx, this)
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // Contenido central del control de Cast
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Insignia del dispositivo receptor
                val deviceName = session.castDevice?.friendlyName ?: "Google Cast"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MobilePrimary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = MobilePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transmitiendo en $deviceName",
                            color = MobilePrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Logotipo del canal o carátula de película
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x33FFFFFF))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Título del canal
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (channel.category.equals("ghibli", ignoreCase = true)) "Película VOD" else "Canal en vivo",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Estado de reproducción o aviso de error
                if (castErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x33EF4444),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = castErrorMessage ?: "",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    try {
                                        castContext?.sessionManager?.endCurrentSession(true)
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
                            ) {
                                Text("Ver en pantalla del celular", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                } else if (isBufferingOnCast) {
                    CircularProgressIndicator(
                        color = MobilePrimary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Conectando señal...",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = if (isPlayingOnCast) "▶ En reproducción en pantalla grande" else "⏸ En pausa",
                        color = if (isPlayingOnCast) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Botón principal de Reproducción / Pausa en TV
                FilledIconButton(
                    onClick = {
                        if (isPlayingOnCast) {
                            remoteMediaClient?.pause()
                        } else {
                            remoteMediaClient?.play()
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MobilePrimary)
                ) {
                    Icon(
                        imageVector = if (isPlayingOnCast) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlayingOnCast) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Fila de acciones: Detener reproducción y Desconectar Cast
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                remoteMediaClient?.stop()
                            } catch (_: Exception) {}
                            onClose()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Detener transmisión")
                    }

                    Button(
                        onClick = {
                            try {
                                castContext?.sessionManager?.endCurrentSession(true)
                            } catch (_: Exception) {}
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Desconectar TV", color = Color.White)
                    }
                }
            }
        }
    } else {
        // Reproductor nativo móvil con control táctil
        ExoPlayerView(
            channel = channel,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onClose = onClose,
            onSendToTv = onSendToTv
        )
    }
}
