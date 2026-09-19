package com.example.chariiptv

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.chariiptv.domain.Channel
import com.example.chariiptv.presentation.TvViewModel
import com.example.chariiptv.theme.TVDROIDTheme
import com.example.chariiptv.theme.MobileBackground
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface
import com.example.chariiptv.theme.MobileSurfaceFocused
import com.example.chariiptv.util.Constants
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
                
                var notificationMessage by remember { mutableStateOf<String?>(null) }
                
                LaunchedEffect(notificationMessage) {
                    if (notificationMessage != null) {
                        delay(2500)
                        notificationMessage = null
                    }
                }
                
                Scaffold(
                    topBar = {
                        if (uiState.currentChannel == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MobileBackground)
                                    .statusBarsPadding()
                                    .height(48.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.rssNews.isNotEmpty()) {
                                    Text(
                                        text = uiState.rssNews, 
                                        color = Color.LightGray, 
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f).basicMarquee(),
                                        maxLines = 1
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                AndroidView(
                                    factory = { context ->
                                        MediaRouteButton(context).apply {
                                            CastButtonFactory.setUpMediaRouteButton(context, this)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    snackbarHost = {
                        if (notificationMessage != null) {
                            Snackbar(
                                modifier = Modifier.padding(16.dp),
                                containerColor = MobileSurfaceFocused,
                                contentColor = Color.White
                            ) {
                                Text(notificationMessage ?: "", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
                        ChannelGrid(
                            selectedCountryCode = uiState.selectedCountryCode,
                            rssNews = uiState.rssNews,
                            onCountrySelected = { code, forceRefresh -> viewModel.selectCountry(code, forceRefresh) },
                            channels = uiState.channels,
                            favorites = uiState.favorites,
                            isLoading = uiState.isLoading,
                            onChannelClick = { viewModel.playChannel(it) },
                            onToggleFavorite = { 
                                viewModel.toggleFavorite(it) 
                                notificationMessage = if (uiState.favorites.contains(it.url)) {
                                    getString(R.string.removed_from_favorites)
                                } else {
                                    getString(R.string.added_to_favorites)
                                }
                            }
                        )

                        if (uiState.currentChannel != null) {
                            DisposableEffect(Unit) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                onDispose {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                            
                            BackHandler {
                                viewModel.playChannel(null)
                            }
                            
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                CastOrLocalPlayerView(
                                    channel = uiState.currentChannel!!,
                                    onError = {
                                        notificationMessage = getString(R.string.playback_error)
                                    }
                                )
                            }
                        }
                        
                        if (uiState.errorMessage != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.clearError() },
                                title = { Text("Error", color = Color.White) },
                                text = { Text(uiState.errorMessage!!, color = Color.LightGray) },
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
}

@Composable
fun CastOrLocalPlayerView(
    channel: Channel,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val castContext = remember {
        try { CastContext.getSharedInstance(context) } catch (e: Exception) { null }
    }
    val castSession = castContext?.sessionManager?.currentCastSession

    if (castSession != null && castSession.isConnected) {
        // Casting
        LaunchedEffect(channel) {
            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, channel.name)
            }
            if (channel.logo.isNotEmpty()) {
                movieMetadata.addImage(com.google.android.gms.common.images.WebImage(Uri.parse(channel.logo)))
            }
            
            val mediaInfo = MediaInfo.Builder(channel.url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("video/mp4")
                .setMetadata(movieMetadata)
                .build()
                
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()
                
            castSession.remoteMediaClient?.load(request)
        }
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Transmitiendo a la TV...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(channel.name, color = Color.LightGray, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = MobilePrimary)
            }
        }
    } else {
        // Local Playback
        ExoPlayerView(channel = channel, onError = onError)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGrid(
    selectedCountryCode: String,
    rssNews: String,
    onCountrySelected: (String, Boolean) -> Unit,
    channels: List<Channel>,
    favorites: List<String>,
    isLoading: Boolean,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    val sortedChannels = channels.sortedBy { it.name }
    val countryKeys = Constants.SUPPORTED_COUNTRIES.keys.toList()
    var countryToRefresh by remember { mutableStateOf<String?>(null) }
    val selectedIndex = countryKeys.indexOf(selectedCountryCode).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Color.White,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = MobilePrimary
                    )
                }
            }
        ) {
            countryKeys.forEachIndexed { index, code ->
                val name = Constants.SUPPORTED_COUNTRIES[code]!!
                val isSelected = code == selectedCountryCode
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (code == selectedCountryCode && code != "fav") {
                            countryToRefresh = code
                        } else {
                            onCountrySelected(code, false)
                        }
                    },
                    text = {
                        Text(
                            text = name,
                            color = if (isSelected) MobilePrimary else Color.LightGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MobilePrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.searching_channels, Constants.SUPPORTED_COUNTRIES[selectedCountryCode] ?: ""),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_channels, channels.size, Constants.SUPPORTED_COUNTRIES[selectedCountryCode] ?: ""), 
                        color = Color.Gray, 
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = sortedChannels, key = { it.url }) { channel ->
                        val isFav = favorites.contains(channel.url)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = { onChannelClick(channel) },
                                    onLongClick = { onToggleFavorite(channel) }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MobileSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (channel.logo.isNotEmpty()) {
                                        SubcomposeAsyncImage(
                                            model = channel.logo,
                                            contentDescription = channel.name,
                                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                                            contentScale = ContentScale.Fit
                                        ) {
                                            val state = painter.state
                                            if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("📺", fontSize = 24.sp)
                                                }
                                            } else {
                                                SubcomposeAsyncImageContent()
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Text("📺", fontSize = 24.sp)
                                        }
                                    }
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        lineHeight = 14.sp
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x88000000))
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = if (isFav) "❤️" else "🤍",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (countryToRefresh != null) {
            val countryName = Constants.SUPPORTED_COUNTRIES[countryToRefresh] ?: ""
            AlertDialog(
                onDismissRequest = { countryToRefresh = null },
                title = { Text(stringResource(R.string.scan_channels_title), color = Color.White) },
                text = { Text(stringResource(R.string.scan_channels_desc, countryName), color = Color.LightGray) },
                containerColor = MobileSurface,
                confirmButton = {
                    Button(
                        onClick = {
                            onCountrySelected(countryToRefresh!!, true)
                            countryToRefresh = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
                    ) {
                        Text(stringResource(R.string.scan), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { countryToRefresh = null }
                    ) {
                        Text(stringResource(R.string.cancel), color = Color.LightGray)
                    }
                }
            )
        }
    }
}

@Composable
fun ExoPlayerView(
    channel: Channel,
    onError: () -> Unit
) {
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer?.play()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer?.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = ExoPlayer.Builder(context).build().also {
                        exoPlayer = it
                        it.setMediaItem(MediaItem.fromUri(channel.url))
                        it.prepare()
                        it.playWhenReady = true
                        it.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                super.onPlayerError(error)
                                onError()
                            }
                        })
                    }
                    useController = true 
                }
            },
            onRelease = {
                exoPlayer?.release()
            }
        )
    }
}
