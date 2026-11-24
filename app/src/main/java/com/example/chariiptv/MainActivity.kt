package com.example.chariiptv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chariiptv.domain.Channel
import com.example.chariiptv.player.ExoPlayerView
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
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var isPlayingChannel = false

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
                val viewModel: TvViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                isPlayingChannel = (uiState.currentChannel != null)

                var notificationMessage by remember { mutableStateOf<String?>(null) }
                var showImportantDialog by remember { mutableStateOf(false) }

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
                                selectedFilterId = uiState.selectedFilterId,
                                onFilterSelected = { viewModel.selectFilter(it) },
                                selectedCountryCode = uiState.selectedCountryCode,
                                onCountrySelected = { viewModel.selectCountry(it) },
                                onImportantClick = { showImportantDialog = true },
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
                        if (uiState.currentChannel == null) {
                            // Cuadrícula Principal de Canales / Películas
                            val channels = uiState.filteredChannels
                            val isGhibli = uiState.selectedFilterId == "ghibli"

                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MobilePrimary, strokeWidth = 3.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Cargando catálogo...",
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
                                            text = if (uiState.searchQuery.isNotEmpty()) "🔍" else "📺",
                                            fontSize = 48.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (uiState.searchQuery.isNotEmpty()) {
                                                "No se encontraron resultados para '${uiState.searchQuery}'"
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
                                            text = "${channels.size} ${if (isGhibli) "obras cinematográficas" else "canales activos"}",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (uiState.selectedFilterId == "countries") {
                                            Text(
                                                text = Constants.COUNTRY_FLAGS[uiState.selectedCountryCode] ?: "",
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = if (isGhibli) 110.dp else 95.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(
                                            items = channels,
                                            key = { it.url }
                                        ) { channel ->
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
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Vista de Reproducción (ExoPlayer Móvil o Google Cast)
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
                                onClose = { viewModel.playChannel(null) }
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
}

@Composable
fun CastOrLocalPlayerView(
    channel: Channel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val castContext = remember {
        try { CastContext.getSharedInstance(context) } catch (e: Exception) { null }
    }
    val castSession = castContext?.sessionManager?.currentCastSession

    if (castSession != null && castSession.isConnected) {
        // Transmisión activa a Chromecast
        LaunchedEffect(channel) {
            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, channel.name)
            }
            if (channel.logo.isNotEmpty()) {
                movieMetadata.addImage(com.google.android.gms.common.images.WebImage(Uri.parse(channel.logo)))
            }

            val mediaInfo = MediaInfo.Builder(channel.url)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType("application/x-mpegurl")
                .setMetadata(movieMetadata)
                .build()

            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()

            castSession.remoteMediaClient?.load(request)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Transmitiendo a Google Cast...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(channel.name, color = MobilePrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = MobilePrimary)
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(onClick = onClose) {
                    Text("Detener transmisión", color = Color.White)
                }
            }
        }
    } else {
        // Reproductor nativo móvil con control táctil
        ExoPlayerView(
            channel = channel,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onClose = onClose
        )
    }
}
