package com.example.chariiptv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.chariiptv.domain.Channel
import com.example.chariiptv.domain.GhibliData
import com.example.chariiptv.domain.GhibliPlaybackStore
import com.example.chariiptv.presentation.components.ResumePlaybackDialog
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEntryPoint {
    fun localHlsProxyServer(): LocalHlsProxyServer
    @com.example.chariiptv.di.MediaClient fun okHttpClient(): OkHttpClient
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    channel: Channel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onSendToTv: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerDependencies = remember(context) {
        try {
            EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
        } catch (_: Exception) {
            null
        }
    }
    val okHttpClient = playerDependencies?.okHttpClient()
    val localProxyServer = playerDependencies?.localHlsProxyServer()

    val sources = remember(channel) { channel.allSources() }
    var currentSourceIndex by remember(channel.url) { mutableIntStateOf(0) }
    val activeSource = sources.getOrElse(currentSourceIndex) { sources.first() }

    val isGhibli = remember(channel.name, channel.category) {
        channel.category.equals("ghibli", ignoreCase = true) ||
        GhibliData.getChronologicalIndex(channel.name) in 1..25
    }

    val savedPlaybackInfo = remember(channel.name, isGhibli) {
        if (isGhibli) {
            GhibliPlaybackStore.getSavedPosition(context, channel.name)
        } else null
    }

    var showResumeDialog by remember(channel.name, isGhibli) {
        mutableStateOf(savedPlaybackInfo != null && savedPlaybackInfo.first >= GhibliPlaybackStore.MIN_RESUME_THRESHOLD_MS)
    }

    val resumePositionMs = remember(savedPlaybackInfo) { savedPlaybackInfo?.first ?: 0L }
    val resumeDurationMs = remember(savedPlaybackInfo) { savedPlaybackInfo?.second ?: 0L }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var autoRetryCount by remember(channel.url, currentSourceIndex) { mutableIntStateOf(0) }
    var useProxyFallback by remember(channel.url, currentSourceIndex) { mutableStateOf(false) }

    // Estado del HUD y controles
    var isHudVisible by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Modos de aspecto (FIT, FILL, ZOOM)
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    val resizeLabels = listOf("Original (16:9)", "Estirar", "Zoom (Llenar)")
    var resizeModeIndex by remember { mutableIntStateOf(0) }

    // Feedback táctil visual (Volumen y Brillo)
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    // Auto-ocultar HUD tras 5 segundos
    LaunchedEffect(isHudVisible, isPlaying) {
        if (isHudVisible && isPlaying) {
            delay(5000)
            isHudVisible = false
        }
    }

    // Actualización de posición VOD periódica y guardado de Ghibli
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            exoPlayer?.let { player ->
                if (!isSeeking) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    currentPositionMs = pos
                    durationMs = dur
                    if (isGhibli && pos > 0L && dur > 0L) {
                        GhibliPlaybackStore.savePosition(context, channel.name, pos, dur)
                    }
                }
            }
            delay(1000)
        }
    }

    // Inicialización y ciclo de vida del ExoPlayer
    DisposableEffect(lifecycleOwner, activeSource, useProxyFallback) {
        val streamUrl: String = if (useProxyFallback && localProxyServer != null) {
            localProxyServer.buildProxyUrl(
                streamUrl = activeSource.url,
                referer = if (activeSource.referer.isNotBlank()) activeSource.referer else channel.referer,
                userAgent = if (activeSource.userAgent.isNotBlank()) activeSource.userAgent else channel.userAgent
            )
        } else {
            activeSource.url
        }

        val player = PlayerBuilder.build(
            context = context,
            okHttpClient = okHttpClient
        ).apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.parse(streamUrl)))
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = (state == Player.STATE_BUFFERING)
                    isPlaying = (state == Player.STATE_READY && playWhenReady)
                    if (state == Player.STATE_READY) {
                        isError = false
                        errorMessage = ""
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    isPlaying = false
                    if (!useProxyFallback && localProxyServer != null) {
                        useProxyFallback = true
                    } else if (autoRetryCount < 2) {
                        autoRetryCount++
                        seekToDefaultPosition()
                        prepare()
                        play()
                    } else if (sources.size > 1 && currentSourceIndex < sources.size - 1) {
                        currentSourceIndex++
                        useProxyFallback = false
                        autoRetryCount = 0
                    } else {
                        isError = true
                        errorMessage = "Error de señal: ${error.localizedMessage ?: "Transmisión no disponible"}"
                    }
                }
            })
        }

        exoPlayer = player

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                        activity.isInPictureInPictureMode
                    } else false
                    if (!inPip) {
                        player.playWhenReady = false
                        player.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                        activity.isInPictureInPictureMode
                    } else false
                    if (!inPip) {
                        player.playWhenReady = false
                        player.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!player.isPlaying) {
                        player.play()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    player.playWhenReady = false
                    player.stop()
                    player.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.playWhenReady = false
            player.stop()
            player.release()
            exoPlayer = null
        }
    }

    BackHandler {
        onClose()
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isHudVisible = !isHudVisible },
                    onDoubleTap = { offset ->
                        exoPlayer?.let { player ->
                            val currentPos = player.currentPosition
                            if (offset.x < size.width / 2) {
                                player.seekTo((currentPos - 10000L).coerceAtLeast(0L))
                                gestureOverlayText = "-10s ⏪"
                            } else {
                                player.seekTo((currentPos + 10000L).coerceAtMost(player.duration))
                                gestureOverlayText = "+10s ⏩"
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val isLeftSide = change.position.x < (size.width / 2)
                        if (isLeftSide && activity != null) {
                            // Brillo en el lado izquierdo
                            val lp = activity.window.attributes
                            val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            val newBrightness = (currentBrightness - (dragAmount / 500f)).coerceIn(0.01f, 1.0f)
                            lp.screenBrightness = newBrightness
                            activity.window.attributes = lp
                            gestureOverlayText = "☀️ Brillo: ${(newBrightness * 100).toInt()}%"
                        } else if (audioManager != null) {
                            // Volumen en el lado derecho
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val delta = if (dragAmount < -10) 1 else if (dragAmount > 10) -1 else 0
                            if (delta != 0) {
                                val newVol = (currentVol + delta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                gestureOverlayText = "🔊 Volumen: ${(newVol.toFloat() / maxVolume * 100).toInt()}%"
                            }
                        }
                    },
                    onDragEnd = {
                        gestureOverlayText = null
                    }
                )
            }
    ) {
        // Superficie del Reproductor ExoPlayer
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    resizeMode = resizeModes[resizeModeIndex]
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeModes[resizeModeIndex]
            }
        )

        // Indicador de Carga / Buffering
        if (isBuffering && !isError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MobilePrimary, strokeWidth = 3.dp)
            }
        }

        // Overlay de Gesto (Volumen, Brillo, Salto de tiempo)
        if (gestureOverlayText != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = gestureOverlayText ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay de Error con opción de reintentar
        if (isError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xDD1E293B))
                        .padding(24.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Transmisión Interrumpida", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = errorMessage, color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                isError = false
                                useProxyFallback = true
                                exoPlayer?.seekToDefaultPosition()
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
                        ) {
                            Text("Reintentar con Proxy", color = Color.White)
                        }
                        OutlinedButton(onClick = onClose) {
                            Text("Salir", color = Color.White)
                        }
                    }
                }
            }
        }

        val inPipMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
            activity.isInPictureInPictureMode
        } else false

        // HUD Interactivo
        AnimatedVisibility(
            visible = isHudVisible && !isError && !inPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Barra Superior del HUD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xAA000000))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isGhibli) {
                            Text("Studio Ghibli • Colección Completa", color = MobilePrimary, fontSize = 11.sp)
                        }
                    }

                    // Botón de Aspect Ratio (Ajustar a pantalla)
                    IconButton(onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                        gestureOverlayText = resizeLabels[resizeModeIndex]
                    }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }

                    // Botón de Picture-in-Picture
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            activity.enterPictureInPictureMode(params)
                            isHudVisible = false
                        }
                    }) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }

                    // Botón de Enviar a TV nativa por LAN
                    if (onSendToTv != null) {
                        IconButton(onClick = onSendToTv) {
                            Icon(Icons.Filled.Tv, contentDescription = "Enviar a TV", tint = Color.White)
                        }
                    }

                    // Botón de Google Cast
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

                    // Botón de Favoritos (Estrella dorada)
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFFFD700) else Color.White
                        )
                    }
                }

                // Barra Inferior del HUD (Para VOD o IPTV en vivo)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color(0xAA000000))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (durationMs > 0L) {
                        // Modo VOD (Studio Ghibli o Películas VOD)
                        fun formatTime(ms: Long): String {
                            val totalSecs = (ms / 1000).coerceAtLeast(0)
                            val hours = totalSecs / 3600
                            val mins = (totalSecs % 3600) / 60
                            val secs = totalSecs % 60
                            return if (hours > 0) String.format("%02d:%02d:%02d", hours, mins, secs)
                            else String.format("%02d:%02d", mins, secs)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatTime(if (isSeeking) (seekProgress * durationMs).toLong() else currentPositionMs), color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(formatTime(durationMs), color = Color.LightGray, fontSize = 12.sp)
                        }

                        Slider(
                            value = if (isSeeking) seekProgress else if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = {
                                isSeeking = true
                                seekProgress = it
                            },
                            onValueChangeFinished = {
                                val targetMs = (seekProgress * durationMs).toLong()
                                exoPlayer?.seekTo(targetMs)
                                currentPositionMs = targetMs
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MobilePrimary,
                                activeTrackColor = MobilePrimary,
                                inactiveTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }

                    // Botón Central Play / Pause y Fuentes Alternativas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sources.size > 1) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MobileSurface)
                                    .clickable {
                                        currentSourceIndex = (currentSourceIndex + 1) % sources.size
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Fuente ${currentSourceIndex + 1}/${sources.size}",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (durationMs > 0L) Color.LightGray else Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (durationMs > 0L) "VOD" else "EN VIVO",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Espaciador para balance visual
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }
        }

        // Diálogo de Continuar Reproducción para Studio Ghibli
        if (showResumeDialog) {
            ResumePlaybackDialog(
                movieTitle = channel.name,
                savedPositionMs = resumePositionMs,
                totalDurationMs = resumeDurationMs,
                onResume = {
                    showResumeDialog = false
                    exoPlayer?.seekTo(resumePositionMs)
                    exoPlayer?.play()
                },
                onRestart = {
                    showResumeDialog = false
                    GhibliPlaybackStore.clearPosition(context, channel.name)
                    exoPlayer?.seekTo(0L)
                    exoPlayer?.play()
                },
                onDismiss = {
                    showResumeDialog = false
                    exoPlayer?.play()
                }
            )
        }
    }
}
