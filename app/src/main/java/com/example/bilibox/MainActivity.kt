package com.example.bilibox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.Size
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bilibox.ui.theme.BiliBoxTheme
import com.example.bilibox.bili.BiliClient
import com.example.bilibox.bili.BiliPlayback
import com.example.bilibox.bili.BiliQn
import com.example.bilibox.bili.VideoSearchItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF24313D)
private val Pink = Color(0xFFEF7196)
private val Muted = Color(0xFF82909D)

private data class SearchVideo(
    val title: String,
    val author: String,
    val playCount: String,
    val commentCount: Long,
    val publishTime: Long,
    val duration: String,
    val bvid: String,
    val coverUrl: String
)

private data class LocalVideo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long
)

private data class PlayerGestureFeedbackState(
    val side: PlayerGestureSide,
    val level: Float
)

private fun readScreenBrightness(activity: ComponentActivity): Float {
    val override = activity.window.attributes.screenBrightness
    if (override >= 0f) return override.coerceIn(0f, 1f)
    val systemBrightness = Settings.System.getInt(
        activity.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128
    )
    return systemBrightness.coerceIn(0, 255) / 255f
}

private sealed interface LocalVideoScanState {
    data object NeedsPermission : LocalVideoScanState
    data object Loading : LocalVideoScanState
    data class Ready(val videos: List<LocalVideo>) : LocalVideoScanState
    data class Error(val message: String) : LocalVideoScanState
}

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(
        val items: List<SearchVideo>,
        val page: Int,
        val hasMore: Boolean,
        val order: SearchOrder,
    ) : SearchState
    data class Error(val message: String, val page: Int = 1, val order: SearchOrder = SearchOrder.RELEVANCE) : SearchState
}

private enum class SearchOrder(val apiValue: String, val label: String) {
    RELEVANCE(BiliClient.SEARCH_ORDER_TOTALRANK, "综合排序"),
    MOST_PLAYED(BiliClient.SEARCH_ORDER_CLICK, "最多播放"),
    NEWEST(BiliClient.SEARCH_ORDER_PUBDATE, "最新发布"),
    MOST_DANMAKU(BiliClient.SEARCH_ORDER_DM, "最多弹幕"),
    MOST_SAVED(BiliClient.SEARCH_ORDER_STOW, "最多收藏"),
}

private sealed interface PlaybackState {
    data object Loading : PlaybackState
    data object Ready : PlaybackState
    data class Error(val message: String) : PlaybackState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BiliBoxTheme { BiliBoxApp() } }
    }
}

@Composable
private fun BiliBoxApp() {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var librarySource by rememberSaveable { mutableIntStateOf(0) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var session by remember { mutableStateOf<BiliSession?>(BiliSessionStore.load(context)) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var searchOrder by rememberSaveable { mutableStateOf(SearchOrder.RELEVANCE) }
    val scope = rememberCoroutineScope()
    val searchClient = remember(session?.cookie) {
        BiliClient().also { it.cookieHeader = session?.cookie }
    }
    val runSearch: (Int, SearchOrder) -> Unit = { page, order ->
        val keyword = searchQuery.trim()
        if (keyword.isNotEmpty()) {
            searchOrder = order
            searchState = SearchState.Loading
            scope.launch {
                searchState = try {
                    val result = searchClient.searchVideos(keyword, page, order.apiValue)
                    SearchState.Success(
                        items = result.items.map(::toSearchVideo),
                        page = result.page,
                        hasMore = result.hasMore,
                        order = order,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    SearchState.Error(error.message ?: "搜索失败，请稍后重试", page, order)
                }
            }
        }
    }
    var selectedVideo by remember { mutableStateOf<SearchVideo?>(null) }
    var selectedLocalVideo by remember { mutableStateOf<LocalVideo?>(null) }
    var localPlaylist by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    selectedLocalVideo?.let { video ->
        LocalVideoPlayer(video, localPlaylist, onVideoSelected = { selectedLocalVideo = it },
            onBack = { selectedLocalVideo = null })
        return
    }
    selectedVideo?.let { video ->
        BiliVideoDetail(video, session) { selectedVideo = null }
        return
    }
    val backdrop = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F6FA))) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }) {
            // The scrollable page is captured once and shared by both glass surfaces.
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().padding(horizontal = 16.dp)
            ) {
                Header()
                when (selected) {
                    0 -> {
                        VideoSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { runSearch(1, searchOrder) }
                        )
                        Spacer(Modifier.height(26.dp))
                        when (val state = searchState) {
                            SearchState.Idle -> HomeContent(saved) { saved = !saved }
                            SearchState.Loading -> SearchLoading()
                            is SearchState.Error -> SearchError(state.message) { runSearch(state.page, state.order) }
                            is SearchState.Success -> SearchResults(
                                state = state,
                                onVideoClick = { selectedVideo = it },
                                onOrderChange = { runSearch(1, it) },
                                onPageChange = { runSearch(it, state.order) },
                            )
                        }
                    }
                    1 -> CollectionPage("动态", "关注的人，分享的日常", "今天的灵感", "沿着海岸线，收集一整个夏天")
                    2 -> {
                        LibraryPage(
                            source = librarySource,
                            onSourceChange = { librarySource = it },
                            hasSavedVideo = saved,
                            onVideoSelected = { video, playlist ->
                                localPlaylist = playlist
                                selectedLocalVideo = video
                            },
                        )
                    }
                    else -> MyPage(if (saved) 1 else 0, session) { session = it }
                }
                Spacer(Modifier.height(150.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
        if (selectedVideo == null) {
            LiquidNavigation(selected, { selected = it }, backdrop,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun LibraryPage(
    source: Int,
    onSourceChange: (Int) -> Unit,
    hasSavedVideo: Boolean,
    onVideoSelected: (LocalVideo, List<LocalVideo>) -> Unit,
) {
    LibrarySourceSwitcher(source, onSourceChange)
    Spacer(Modifier.height(24.dp))
    if (source == 0) {
        if (hasSavedVideo) {
            VideoCard("日落之前，去海边走走", "哔哩哔哩下载 · 1 个视频", 0)
        } else {
            EmptyCard("还没有哔哩哔哩下载", "从哔哩哔哩下载的视频会出现在这里。")
        }
    } else {
        LocalVideoPage(onVideoSelected)
    }
}

@Composable
private fun LocalVideoPage(onVideoSelected: (LocalVideo, List<LocalVideo>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanState by remember { mutableStateOf<LocalVideoScanState>(LocalVideoScanState.NeedsPermission) }

    val scanLocalVideos: () -> Unit = {
        scanState = LocalVideoScanState.Loading
        scope.launch {
            scanState = try {
                LocalVideoScanState.Ready(withContext(Dispatchers.IO) { queryLocalVideos(context) })
            } catch (error: Exception) {
                LocalVideoScanState.Error(error.message ?: "扫描本地视频失败")
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) scanLocalVideos()
        else scanState = LocalVideoScanState.NeedsPermission
    }

    LaunchedEffect(Unit) {
        if (hasLocalVideoPermission(context)) scanLocalVideos()
    }

    when (val state = scanState) {
        LocalVideoScanState.NeedsPermission -> {
            EmptyCard("扫描本地视频", "允许访问本机视频后，BiliBox 会读取设备中的视频文件。")
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { permissionLauncher.launch(localVideoPermissions()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) { Text("允许访问并扫描") }
        }
        LocalVideoScanState.Loading -> {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
                Spacer(Modifier.height(14.dp))
                Text("正在扫描本地视频…", color = Muted, fontSize = 13.sp)
            }
        }
        is LocalVideoScanState.Error -> {
            EmptyCard("扫描失败", state.message)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = scanLocalVideos) { Text("重新扫描", color = Pink) }
            }
        }
        is LocalVideoScanState.Ready -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本地视频", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${state.videos.size} 个", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = scanLocalVideos, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("重新扫描", color = Pink, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.videos.isEmpty()) {
                EmptyCard("没有找到本地视频", "把视频保存到本机后，点击“重新扫描”即可显示。")
            } else {
                state.videos.forEachIndexed { index, video ->
                    LocalVideoRow(context, video, onClick = { onVideoSelected(video, state.videos) })
                    if (index != state.videos.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalVideoRow(context: Context, video: LocalVideo, onClick: () -> Unit) {
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(context, video.uri) {
        thumbnail = withContext(Dispatchers.IO) {
            loadLocalVideoThumbnail(context, video.uri, video.durationMs)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .9f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(116.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE7EBF0))
        ) {
            thumbnail?.let { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("▶", color = Color(0xFF8492A0), fontSize = 22.sp)
            }
            Text(
                formatVideoDuration(video.durationMs),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = .78f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.name,
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                Formatter.formatFileSize(context, video.sizeBytes),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

private fun loadLocalVideoThumbnail(context: Context, uri: Uri, durationMs: Long): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(480, 270), null)
        }.getOrNull()?.let { return it }
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frameTimeUs = (durationMs.coerceAtLeast(0L) / 5L) * 1_000L
        retriever.getScaledFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun LocalVideoPlayer(
    video: LocalVideo,
    playlist: List<LocalVideo>,
    onVideoSelected: (LocalVideo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val progressStore = remember(context) { PlaybackProgressStore(context) }
    // File size also distinguishes a replacement file that reuses a MediaStore URI.
    val progressKey = remember(video.uri, video.sizeBytes) { "${video.uri}|${video.sizeBytes}" }
    val resumePosition = remember(progressKey) { progressStore.load(progressKey, video.durationMs) }
    val player = remember(video.uri) { createCompatiblePlayer(context) }
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    var playbackState by remember(player) { mutableIntStateOf(Player.STATE_IDLE) }
    var liked by remember(video.uri) { mutableStateOf(false) }
    var saved by remember(video.uri) { mutableStateOf(false) }
    fun savePlaybackProgress() {
        // Ignore the empty/idle player during setup or a failed preparation.
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) return
        progressStore.save(
            progressKey,
            player.currentPosition,
            player.duration.takeIf { it > 0L } ?: video.durationMs,
            completed = player.playbackState == Player.STATE_ENDED
        )
    }

    DisposableEffect(player, activity) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val detail = (error.cause?.message ?: error.message)?.takeIf { it.isNotBlank() }
                playbackError = "${error.errorCodeName}${detail?.let { "：$it" }.orEmpty()}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                    savePlaybackProgress()
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                savePlaybackProgress()
            }
        }
        player.addListener(listener)
        activity?.lifecycle?.addObserver(lifecycleObserver)
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.uri), resumePosition)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            savePlaybackProgress()
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            savePlaybackProgress()
        }
    }
    LaunchedEffect(player, playbackState) {
        if (playbackState == Player.STATE_BUFFERING) {
            delay(12_000)
            if (player.playbackState == Player.STATE_BUFFERING && playbackError == null) {
                playbackError = "加载超时，请检查文件是否可读取或编码是否受支持"
            }
        }
    }
    VideoPlayerScreen(
        player = player,
        title = video.name,
        defaultDurationMs = video.durationMs,
        playbackError = playbackError,
        sourceLabel = "本地视频",
        commentMessage = "本地视频暂时没有评论",
        onBack = onBack,
        onRetry = {
            playbackError = null
            val retryPosition = player.currentPosition.coerceAtLeast(0L)
            player.stop()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.uri), retryPosition)
            player.prepare()
            player.playWhenReady = true
        },
        episodes = { dismiss ->
            LocalVideoEpisodes(playlist, video, onDismiss = dismiss, onVideoSelected = {
                dismiss()
                onVideoSelected(it)
            })
        },
        details = { duration ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(video.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "本地视频 · ${Formatter.formatFileSize(context, video.sizeBytes)} · ${formatVideoDuration(duration)}",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VideoAction(0, if (liked) "已点赞" else "点赞", liked) { liked = !liked }
                    VideoAction(1, "投币", false, enabled = false) { }
                    VideoAction(2, if (saved) "已收藏" else "收藏", saved) { saved = !saved }
                    VideoAction(3, "分享", false) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, video.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享视频"))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFFE9EAEC), thickness = 1.dp)
                Spacer(Modifier.height(14.dp))
                Text("视频信息", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("${video.name}\n${Formatter.formatFileSize(context, video.sizeBytes)}", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayerScreen(
    player: ExoPlayer,
    title: String,
    defaultDurationMs: Long = 0L,
    playbackError: String? = null,
    loadingMessage: String? = null,
    sourceLabel: String,
    commentMessage: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    episodes: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    details: @Composable (duration: Long) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val fullScreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var position by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember(player) { mutableLongStateOf(defaultDurationMs) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var scrubPosition by remember(player) { mutableFloatStateOf(0f) }
    var speed by remember(player) { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember(player) { mutableStateOf(false) }
    var episodesExpanded by remember(player) { mutableStateOf(false) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var detailsTab by remember(player) { mutableIntStateOf(0) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var screenLocked by remember(player) { mutableStateOf(false) }
    var orientationBeforeLock by remember(player) { mutableStateOf<Int?>(null) }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    var hasVideoFrame by remember(player) { mutableStateOf(false) }
    var savingFrame by remember { mutableStateOf(false) }
    var screenBrightness by remember(activity) {
        mutableFloatStateOf(activity?.let(::readScreenBrightness) ?: .5f)
    }
    var volumeGestureRemainder by remember(player) { mutableFloatStateOf(0f) }
    var gestureFeedback by remember(player) { mutableStateOf<PlayerGestureFeedbackState?>(null) }
    var verticalGestureActive by remember(player) { mutableStateOf(false) }
    val captureScope = rememberCoroutineScope()

    val originalWindowBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness }
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        val barsBehavior = controller?.systemBarsBehavior
        onDispose {
            if (originalOrientation != null) activity?.requestedOrientation = originalOrientation
            if (originalWindowBrightness != null) activity?.window?.let { window ->
                val attributes = window.attributes
                attributes.screenBrightness = originalWindowBrightness
                window.attributes = attributes
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (lightStatusBars != null) controller?.isAppearanceLightStatusBars = lightStatusBars
            if (lightNavigationBars != null) controller?.isAppearanceLightNavigationBars = lightNavigationBars
            if (barsBehavior != null) controller?.systemBarsBehavior = barsBehavior
        }
    }
    LaunchedEffect(activity, fullScreen) {
        speedMenuExpanded = false
        episodesExpanded = false
        if (!fullScreen) screenLocked = false
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !fullScreen
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullScreen) hide(WindowInsetsCompat.Type.systemBars())
                else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun toggleOrientation() {
        controlsVisible = true
        activity?.requestedOrientation = if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun navigateBack() {
        if (fullScreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else onBack()
    }

    fun togglePlayback() {
        if (screenLocked) return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        player.playWhenReady = !player.isPlaying
        controlsVisible = true
    }

    fun toggleScreenLock() {
        screenLocked = !screenLocked
        controlsVisible = true
        speedMenuExpanded = false
        episodesExpanded = false
        if (screenLocked) {
            orientationBeforeLock = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity?.requestedOrientation = orientationBeforeLock ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            orientationBeforeLock = null
        }
    }

    fun updateGestureFeedback(side: PlayerGestureSide) {
        if (side == PlayerGestureSide.LEFT) {
            val level = screenBrightness.coerceIn(0f, 1f)
            gestureFeedback = PlayerGestureFeedbackState(side, level)
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            gestureFeedback = PlayerGestureFeedbackState(side, volume.toFloat() / maxVolume)
        }
    }

    fun startVerticalGesture(side: PlayerGestureSide) {
        verticalGestureActive = true
        if (side == PlayerGestureSide.LEFT) {
            screenBrightness = activity?.let(::readScreenBrightness) ?: screenBrightness
        } else {
            volumeGestureRemainder = 0f
        }
        updateGestureFeedback(side)
    }

    fun adjustFromVerticalGesture(side: PlayerGestureSide, deltaFraction: Float) {
        if (side == PlayerGestureSide.LEFT) {
            val newBrightness = (screenBrightness - deltaFraction).coerceIn(.02f, 1f)
            screenBrightness = newBrightness
            activity?.window?.let { setPlayerWindowBrightness(it, newBrightness) }
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            volumeGestureRemainder += -deltaFraction * maxVolume
            val volumeSteps = volumeGestureRemainder.toInt()
            if (volumeSteps != 0) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (current + volumeSteps).coerceIn(0, maxVolume),
                    0
                )
                volumeGestureRemainder -= volumeSteps
            }
        }
        updateGestureFeedback(side)
    }

    fun takeVideoScreenshot() {
        if (savingFrame || screenLocked || !hasVideoFrame || playbackError != null) return
        val frame = try {
            playerView?.let(::captureVideoFrame)
        } catch (_: Exception) { null }
        if (frame == null) {
            Toast.makeText(context, "暂时无法截取画面，请等待视频显示后重试", Toast.LENGTH_SHORT).show()
            return
        }
        savingFrame = true
        captureScope.launch {
            try {
                withContext(Dispatchers.IO) { saveVideoFrame(context, frame) }
                Toast.makeText(context, "已保存到相册 Pictures/BiliBox", Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "截帧保存失败，请检查存储空间或权限后重试", Toast.LENGTH_SHORT).show()
            } finally {
                frame.recycle()
                savingFrame = false
            }
        }
    }

    val screenshotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takeVideoScreenshot()
        else Toast.makeText(context, "需要存储权限才能保存视频画面", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { hasVideoFrame = true }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_IDLE) hasVideoFrame = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            duration = player.duration.takeIf { it > 0 } ?: defaultDurationMs
            if (!scrubbing) position = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            speed = player.playbackParameters.speed
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, screenLocked, scrubbing, speedMenuExpanded, episodesExpanded) {
        if (controlsVisible && (isPlaying || screenLocked) && !scrubbing && !speedMenuExpanded && !episodesExpanded) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureFeedback, verticalGestureActive) {
        if (gestureFeedback != null && !verticalGestureActive) {
            delay(900)
            gestureFeedback = null
        }
    }
    BackHandler {
        when {
            screenLocked -> controlsVisible = true
            episodesExpanded -> episodesExpanded = false
            speedMenuExpanded -> speedMenuExpanded = false
            else -> navigateBack()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)
        .then(if (fullScreen) Modifier else Modifier.statusBarsPadding())) {
        Box(
            (if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { viewContext -> createTexturePlayerView(viewContext, player).also { playerView = it } },
                update = { it.player = player; playerView = it },
                modifier = Modifier.fillMaxSize()
            )
            PlayerTouchSurface(
                locked = screenLocked,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlayback = { togglePlayback() },
                verticalGesturesEnabled = !screenLocked,
                onVerticalGestureStart = { startVerticalGesture(it) },
                onVerticalSwipe = { side, delta -> adjustFromVerticalGesture(side, delta) },
                onVerticalGestureEnd = { verticalGestureActive = false },
                modifier = Modifier.fillMaxSize()
            )
            if (playbackError != null) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂时无法播放", color = Color.White, fontSize = 14.sp)
                    TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                }
            } else if (loadingMessage != null || playbackState != Player.STATE_READY) {
                Text(
                    loadingMessage ?: if (playbackState == Player.STATE_BUFFERING) "正在加载视频…" else "等待视频加载",
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 13.sp
                )
            }
            if (controlsVisible && !screenLocked) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f))))
                        .then(if (fullScreen) Modifier.displayCutoutPadding().navigationBarsPadding()
                            .padding(start = 18.dp, end = 18.dp, top = 12.dp)
                            else Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp))
                ) {
                    val seekBar: @Composable (Modifier) -> Unit = { modifier ->
                        VideoSeekBar(
                            position = if (scrubbing) scrubPosition else position.toFloat(),
                            duration = duration,
                            onSeek = { scrubbing = true; scrubPosition = it },
                            onSeekFinished = {
                                player.seekTo(scrubPosition.toLong())
                                position = scrubPosition.toLong()
                                scrubbing = false
                            },
                            modifier = modifier
                        )
                    }
                    if (fullScreen) {
                        Text(
                            "${formatPlaybackTime(if (scrubbing) scrubPosition.toLong() else position)}/${formatPlaybackTime(duration, unknownWhenZero = true)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 13.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        // The track adds half the 18dp thumb width to this inset:
                        // 4 + 9 = 13dp, matching the play triangle's leading edge.
                        seekBar(Modifier.fillMaxWidth().padding(start = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { togglePlayback() },
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = if (isPlaying) "暂停" else "播放"
                            }
                        ) {
                            PlaybackIcon(isPlaying)
                        }
                        if (fullScreen) {
                            Spacer(Modifier.weight(1f))
                            if (episodes != null) {
                                TextButton(onClick = { episodesExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                                    Text("选集", color = Color.White, fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                            }
                            Box {
                                TextButton(onClick = { speedMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                                    Text(if (speed == 1f) "倍速" else "${speed}x", color = Color.White,
                                        fontSize = 14.sp, modifier = Modifier.semantics { contentDescription = "倍速" })
                                }
                                PlaybackSpeedMenu(speedMenuExpanded, speed, dark = true,
                                    onDismiss = { speedMenuExpanded = false }, onSpeedSelected = {
                                        player.setPlaybackSpeed(it)
                                        speed = it
                                        speedMenuExpanded = false
                                    })
                            }
                        } else {
                            seekBar(Modifier.weight(1f))
                            Text(
                                "${formatPlaybackTime(if (scrubbing) scrubPosition.toLong() else position)}/${formatPlaybackTime(duration, unknownWhenZero = true)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                            )
                            IconButton(
                                onClick = { toggleOrientation() },
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "切换横屏" }
                            ) {
                                FullscreenIcon(fullScreen = false)
                            }
                        }
                    }
                }
            }
            if (controlsVisible && !screenLocked) Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .48f), Color.Transparent)))
                    .then(if (fullScreen) Modifier.displayCutoutPadding() else Modifier)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateBack() }, modifier = Modifier.semantics { contentDescription = "返回" }) {
                    Canvas(Modifier.size(24.dp)) {
                        drawPath(Path().apply {
                            moveTo(size.width * .63f, size.height * .15f)
                            lineTo(size.width * .28f, size.height * .5f)
                            lineTo(size.width * .63f, size.height * .85f)
                        }, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Text(if (fullScreen) title else "", color = Color.White, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!fullScreen) Box {
                    IconButton(onClick = { speedMenuExpanded = true }, modifier = Modifier.semantics { contentDescription = "更多播放设置" }) {
                        Canvas(Modifier.size(24.dp)) {
                            listOf(.22f, .5f, .78f).forEach { x ->
                                drawCircle(Color.White, 1.6.dp.toPx(), Offset(size.width * x, size.height * .5f))
                            }
                        }
                    }
                    PlaybackSpeedMenu(speedMenuExpanded, speed, dark = false,
                        onDismiss = { speedMenuExpanded = false }, onSpeedSelected = {
                            player.setPlaybackSpeed(it)
                            speed = it
                            speedMenuExpanded = false
                        })
                }
            }
            if (fullScreen && controlsVisible) {
                PlayerSideTools(
                    locked = screenLocked,
                    screenshotEnabled = hasVideoFrame && playbackError == null && !savingFrame,
                    onScreenshot = {
                        if (Build.VERSION.SDK_INT == 28 && context.checkSelfPermission(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED) {
                            screenshotPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else takeVideoScreenshot()
                    },
                    onToggleLock = { toggleScreenLock() },
                    modifier = Modifier.align(Alignment.CenterEnd).displayCutoutPadding()
                        .navigationBarsPadding().padding(end = 10.dp).offset(y = (-36).dp)
                )
            }
            gestureFeedback?.let { feedback ->
                PlayerGestureFeedback(
                    side = feedback.side,
                    level = feedback.level,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (fullScreen && episodesExpanded && !screenLocked) {
                episodes?.invoke {
                    episodesExpanded = false
                    controlsVisible = true
                }
            }
        }

        if (!fullScreen) Column(
            Modifier.weight(1f).fillMaxWidth().background(Color.White)
                .verticalScroll(rememberScrollState()).navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("简介", "评论").forEachIndexed { index, label ->
                    Column(
                        Modifier.fillMaxHeight().clickable { detailsTab = index }.padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(label, color = if (detailsTab == index) Pink else Muted, fontSize = 15.sp,
                            fontWeight = if (detailsTab == index) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(30.dp).height(2.dp).background(if (detailsTab == index) Pink else Color.Transparent, CircleShape))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(sourceLabel, color = Muted, fontSize = 12.sp)
            }
            HorizontalDivider(color = Color(0xFFE9EAEC), thickness = 1.dp)

            if (detailsTab == 0) {
                details(duration)
                playbackError?.let { message ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        Text("播放失败：$message", color = Color(0xFFB42318), fontSize = 12.sp)
                        TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Text("评论", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(commentMessage, color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSeekBar(
    position: Float,
    duration: Long,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the visual layout compact; Compose still expands the touch target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = duration > 0,
            modifier = modifier.height(24.dp).semantics { contentDescription = "播放进度" },
            thumb = { TelevisionSeekThumb() },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.dp),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFFF6699),
                        inactiveTrackColor = Color.White.copy(alpha = .45f)
                    )
                )
            }
        )
    }
}

@Composable
private fun PlaybackSpeedMenu(
    expanded: Boolean,
    speed: Float,
    dark: Boolean,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        containerColor = if (dark) Color(0xFF242424) else Color.White) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
            DropdownMenuItem(
                text = { Text("倍速 ${option}x", color = when {
                    speed == option -> Pink
                    dark -> Color.White
                    else -> Ink
                }) },
                onClick = { onSpeedSelected(option) }
            )
        }
    }
}

@Composable
private fun LocalVideoEpisodes(
    playlist: List<LocalVideo>,
    currentVideo: LocalVideo,
    onDismiss: () -> Unit,
    onVideoSelected: (LocalVideo) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = playlist.indexOfFirst { it.uri == currentVideo.uri }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxHeight()
                .background(Color(0xF5181818))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .displayCutoutPadding().navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("选集", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("  ${playlist.size} 个视频", color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .7f), fontSize = 26.sp)
                }
            }
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(playlist, key = { _, item -> item.uri.toString() }) { index, item ->
                    val selected = item.uri == currentVideo.uri
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Pink.copy(alpha = .14f) else Color.White.copy(alpha = .05f))
                            .selectable(selected = selected, role = Role.RadioButton,
                                onClick = { onVideoSelected(item) }).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", color = if (selected) Pink else Color.White.copy(alpha = .5f),
                            fontSize = 13.sp, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = if (selected) Pink else Color.White, fontSize = 14.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(if (selected) "正在播放" else formatVideoDuration(item.durationMs),
                                color = if (selected) Pink else Color.White.copy(alpha = .45f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackIcon(playing: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val u = size.width / 26f
        if (playing) {
            listOf(5f, 16f).forEach { x ->
                drawRoundRect(Color.White, Offset(x * u, 3f * u),
                    androidx.compose.ui.geometry.Size(5f * u, 20f * u),
                    androidx.compose.ui.geometry.CornerRadius(1.2f * u))
            }
        } else {
            scale(scaleX = 1f, scaleY = .88f, pivot = center) {
                drawPath(Path().apply {
                    moveTo(5f * u, 2f * u)
                    quadraticTo(2f * u, .5f * u, 2f * u, 4f * u)
                    lineTo(2f * u, 22f * u)
                    quadraticTo(2f * u, 25.5f * u, 5f * u, 24f * u)
                    lineTo(23f * u, 14.8f * u)
                    quadraticTo(26f * u, 13f * u, 23f * u, 11.2f * u)
                    close()
                }, Color.White)
            }
        }
    }
}

@Composable
private fun TelevisionSeekThumb() {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 24f
        val outline = Color(0xFF161616)
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Center the television body on the track, independently of its antennae.
        drawLine(outline, Offset(7f*u, 1f*u), Offset(10f*u, 4f*u), 2f*u, StrokeCap.Round)
        drawLine(outline, Offset(17f*u, 1f*u), Offset(14f*u, 4f*u), 2f*u, StrokeCap.Round)
        drawRoundRect(Color.White, Offset(2f*u, 4f*u), androidx.compose.ui.geometry.Size(20f*u, 16f*u),
            androidx.compose.ui.geometry.CornerRadius(4f*u))
        drawRoundRect(outline, Offset(2f*u, 4f*u), androidx.compose.ui.geometry.Size(20f*u, 16f*u),
            androidx.compose.ui.geometry.CornerRadius(4f*u), style = stroke)
        drawLine(outline, Offset(8f*u, 9.5f*u), Offset(8f*u, 14.5f*u), 2.4f*u, StrokeCap.Round)
        drawLine(outline, Offset(16f*u, 9.5f*u), Offset(16f*u, 14.5f*u), 2.4f*u, StrokeCap.Round)
    }
}

@Composable
private fun FullscreenIcon(fullScreen: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val corners = Path().apply {
            if (fullScreen) {
                moveTo(2f*u, 8f*u); lineTo(8f*u, 8f*u); lineTo(8f*u, 2f*u)
                moveTo(16f*u, 2f*u); lineTo(16f*u, 8f*u); lineTo(22f*u, 8f*u)
                moveTo(22f*u, 16f*u); lineTo(16f*u, 16f*u); lineTo(16f*u, 22f*u)
                moveTo(8f*u, 22f*u); lineTo(8f*u, 16f*u); lineTo(2f*u, 16f*u)
            } else {
                moveTo(2f*u, 9f*u); lineTo(2f*u, 3f*u); lineTo(8f*u, 3f*u)
                moveTo(16f*u, 3f*u); lineTo(22f*u, 3f*u); lineTo(22f*u, 9f*u)
                moveTo(22f*u, 15f*u); lineTo(22f*u, 21f*u); lineTo(16f*u, 21f*u)
                moveTo(8f*u, 21f*u); lineTo(2f*u, 21f*u); lineTo(2f*u, 15f*u)
            }
        }
        drawPath(corners, Color.White, style = Stroke(2.2f*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun RowScope.VideoAction(index: Int, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (selected) Pink else Color(0xFF62666D).copy(alpha = if (enabled) 1f else .4f)
    Column(
        Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(26.dp)) {
            val u = size.width / 24f
            val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (index) {
                0 -> {
                    drawRoundRect(tint, topLeft = Offset(2*u, 10*u), size = androidx.compose.ui.geometry.Size(4*u, 11*u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u))
                    drawPath(Path().apply {
                        moveTo(8*u, 10*u); lineTo(12*u, 3*u)
                        cubicTo(16*u, 2*u, 15*u, 7*u, 14*u, 9*u)
                        lineTo(20*u, 9*u); quadraticTo(23*u, 9*u, 22*u, 12*u)
                        lineTo(20*u, 20*u); lineTo(8*u, 20*u); close()
                    }, tint)
                }
                1 -> {
                    drawCircle(tint, radius = 10*u, style = stroke)
                    drawLine(tint, Offset(7*u, 8*u), Offset(17*u, 8*u), strokeWidth = 1.8f*u)
                    drawLine(tint, Offset(12*u, 6*u), Offset(12*u, 19*u), strokeWidth = 1.8f*u)
                    drawLine(tint, Offset(8*u, 12*u), Offset(16*u, 12*u), strokeWidth = 1.8f*u)
                }
                2 -> drawPath(Path().apply {
                    for (point in 0..9) {
                        val angle = Math.PI * point / 5 - Math.PI / 2
                        val radius = if (point % 2 == 0) 11*u else 5*u
                        val x = 12*u + kotlin.math.cos(angle).toFloat()*radius
                        val y = 12*u + kotlin.math.sin(angle).toFloat()*radius
                        if (point == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }, tint)
                3 -> drawPath(Path().apply {
                    moveTo(3*u, 21*u); cubicTo(3*u, 11*u, 7*u, 7*u, 15*u, 7*u)
                    lineTo(15*u, 2*u); lineTo(23*u, 11*u); lineTo(15*u, 18*u)
                    lineTo(15*u, 13*u); quadraticTo(7*u, 13*u, 3*u, 21*u); close()
                }, tint)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (selected) Pink else Muted, fontSize = 11.sp)
    }
}

private fun createTexturePlayerView(context: Context, player: ExoPlayer): PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
        this.player = player
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        keepScreenOn = true
    }

private fun hasLocalVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
    return Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
}

private fun localVideoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun queryLocalVideos(context: Context): List<LocalVideo> {
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE
    )
    val videos = mutableListOf<LocalVideo>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            videos += LocalVideo(
                uri = ContentUris.withAppendedId(collection, id),
                name = cursor.getString(nameColumn).orEmpty().ifBlank { "未命名视频" },
                durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L)
            )
        }
    }
    return videos
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun LibrarySourceSwitcher(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("哔哩哔哩下载", "本地视频")
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .72f))
            .padding(5.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Pink else Color.Transparent)
                    .selectable(isSelected, onClick = { onSelect(index) }, role = Role.Tab),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else Muted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VideoSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text("搜索 B 站视频", color = Muted) },
            leadingIcon = { Text("⌕", fontSize = 26.sp, color = Pink) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = .76f),
                focusedContainerColor = Color.White,
                unfocusedBorderColor = Color(0xFFE4E9EF),
                focusedBorderColor = Pink
            )
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink)
        ) { Text("搜索") }
    }
}

@Composable
private fun SearchLoading() {
    Column(Modifier.fillMaxWidth().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text("正在搜索视频…", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暂时没有搜到结果", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("重试", color = Pink) }
    }
}

@Composable
private fun SearchResults(
    state: SearchState.Success,
    onVideoClick: (SearchVideo) -> Unit,
    onOrderChange: (SearchOrder) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    var orderMenuExpanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("搜索结果", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${state.items.size} 个视频", color = Muted, fontSize = 12.sp)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("排序", color = Muted, fontSize = 12.sp)
        Box {
            TextButton(onClick = { orderMenuExpanded = true }) {
                Text(state.order.label, color = Pink, fontSize = 12.sp)
                Text("⌄", color = Pink, fontSize = 14.sp)
            }
            DropdownMenu(expanded = orderMenuExpanded, onDismissRequest = { orderMenuExpanded = false }) {
                SearchOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label) },
                        onClick = {
                            orderMenuExpanded = false
                            if (order != state.order) onOrderChange(order)
                        },
                    )
                }
            }
        }
    }
    if (state.items.isEmpty()) {
        EmptyCard("没有找到视频", "换个关键词试试看。")
    } else {
        state.items.forEach { video ->
            SearchResultCard(video, Modifier.offset(x = (-4).dp)) { onVideoClick(video) }
            HorizontalDivider(color = Color(0xFFDDE3E8), thickness = 1.dp)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onPageChange(state.page - 1) }, enabled = state.page > 1) {
            Text("上一页", color = if (state.page > 1) Pink else Muted)
        }
        Text("第 ${state.page} 页", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { onPageChange(state.page + 1) }, enabled = state.hasMore) {
            Text("下一页", color = if (state.hasMore) Pink else Muted)
        }
    }
}

@Composable
private fun BiliVideoDetail(video: SearchVideo, session: BiliSession?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(video.bvid, session?.cookie) {
        BiliClient().also { it.cookieHeader = session?.cookie }
    }
    val exoPlayer = remember(video.bvid, session?.cookie) {
        val dataSourceFactory = DefaultDataSource.Factory(context, BiliPlayback.dataSourceFactory(session?.cookie, video.bvid))
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
    var playbackState by remember(video.bvid, session?.cookie) { mutableStateOf<PlaybackState>(PlaybackState.Loading) }
    var playbackData by remember(video.bvid, session?.cookie) { mutableStateOf<com.example.bilibox.bili.PlayData?>(null) }
    var playbackCid by remember(video.bvid, session?.cookie) { mutableLongStateOf(0L) }
    var playbackCandidateIndex by remember(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    var playbackCandidateCount by remember(video.bvid, session?.cookie) { mutableIntStateOf(1) }
    var durlFallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var retryToken by remember(video.bvid) { mutableIntStateOf(0) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playbackState = PlaybackState.Ready
            }

            override fun onPlayerError(error: PlaybackException) {
                val data = playbackData
                val nextIndex = playbackCandidateIndex + 1
                if (data != null && nextIndex < playbackCandidateCount) {
                    playbackCandidateIndex = nextIndex
                    playbackState = PlaybackState.Loading
                    BiliPlayback.attach(exoPlayer, data, preferredQn = BiliQn.P1080, candidateIndex = nextIndex, cookie = session?.cookie, bvid = video.bvid)
                    return
                }
                // DASH uses separate audio/video URLs. If every CDN pair is rejected,
                // request the older single-file stream format as a compatibility fallback.
                if (!durlFallbackTried && playbackCid > 0) {
                    durlFallbackTried = true
                    playbackState = PlaybackState.Loading
                    scope.launch {
                        try {
                            val fallback = client.playUrl(
                                bvid = video.bvid,
                                cid = playbackCid,
                                qn = BiliQn.P720,
                                fnval = BiliClient.FNVAL_DURL,
                            )
                            playbackData = fallback
                            playbackCandidateIndex = 0
                            playbackCandidateCount = BiliPlayback.candidateCount(fallback, preferredQn = BiliQn.P720)
                            BiliPlayback.attach(exoPlayer, fallback, preferredQn = BiliQn.P720, cookie = session?.cookie, bvid = video.bvid)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            playbackState = PlaybackState.Error("播放器无法读取视频流：${error.message ?: "单文件流也不可用"}")
                        }
                    }
                    return
                }
                val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                val message = if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && detail?.contains("403") == true) {
                    "CDN 返回 HTTP 403，当前网络出口可能被 B 站拒绝"
                } else {
                    "播放器无法读取视频流：${error.errorCodeName}${detail?.let { "（$it）" }.orEmpty()}"
                }
                playbackState = PlaybackState.Error(message)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    LaunchedEffect(video.bvid, session?.cookie, retryToken) {
        playbackState = PlaybackState.Loading
        durlFallbackTried = false
        try {
            client.warmUp()
            val info = client.videoInfo(video.bvid)
            val cid = info.pages.firstOrNull()?.cid ?: info.cid
            if (cid <= 0) throw IllegalStateException("B 站没有返回视频分 P 信息")
            val play = client.playUrl(info.bvid.ifBlank { video.bvid }, cid, qn = BiliQn.P1080)
            playbackCid = cid
            playbackData = play
            playbackCandidateIndex = 0
            playbackCandidateCount = BiliPlayback.candidateCount(play, preferredQn = BiliQn.P1080)
            BiliPlayback.attach(exoPlayer, play, preferredQn = BiliQn.P1080, candidateIndex = 0, cookie = session?.cookie, bvid = video.bvid)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackState = PlaybackState.Error(error.message ?: "无法获取视频播放地址")
        }
    }
    VideoPlayerScreen(
        player = exoPlayer,
        title = video.title,
        playbackError = (playbackState as? PlaybackState.Error)?.message,
        loadingMessage = if (playbackState == PlaybackState.Loading) "正在获取视频流…" else null,
        sourceLabel = "哔哩哔哩",
        commentMessage = "暂不支持查看在线评论",
        onBack = onBack,
        onRetry = { retryToken++ },
        details = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(video.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                Spacer(Modifier.height(8.dp))
                Text("${video.author} · ${video.playCount} 次播放 · ${video.duration}",
                    color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VideoMetaIcon(kind = "date", modifier = Modifier.size(width = 14.dp, height = 13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("发布时间  ${formatPublishDate(video.publishTime)}", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VideoAction(3, "分享", false) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${video.title} https://www.bilibili.com/video/${video.bvid}")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享视频"))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFFE9EAEC), thickness = 1.dp)
                Spacer(Modifier.height(14.dp))
                Text("视频信息", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (session == null) "匿名播放 · 登录后可尝试更高画质"
                    else "已登录播放 · 使用账号权限获取视频流",
                    color = Muted, fontSize = 12.sp, lineHeight = 19.sp
                )
            }
        }
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}

private fun formatPlaybackTime(positionMs: Long, unknownWhenZero: Boolean = false): String {
    if (unknownWhenZero && positionMs <= 0L) return "--:--"
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun SearchResultCard(video: SearchVideo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(144.dp).height(90.dp).clip(RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            RemoteCover(video.coverUrl, Modifier.fillMaxSize())
            Text(
                video.duration,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .padding(end = 5.dp, bottom = 5.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(90.dp)) {
            Text(
                video.title,
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 17.sp,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.padding(start = 2.dp)
                            .size(width = 14.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Transparent)
                            .border(0.6.dp, Muted, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("UP", color = Muted, fontSize = 6.sp, lineHeight = 7.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(video.author, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    VideoMeta(icon = "play", value = video.playCount)
                    VideoMeta(icon = "comment", value = video.commentCount.toString())
                }
            }
        }
    }
}

@Composable
private fun VideoMeta(icon: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
        VideoMetaIcon(kind = icon, modifier = Modifier.size(width = 16.dp, height = 13.dp))
        Spacer(Modifier.width(2.dp))
        Text(value, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VideoMetaIcon(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * .065f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = size.width * .14f
        val right = size.width * .86f
        val top = size.height * .14f
        val bottom = size.height * .86f
        when (kind) {
            "play" -> {
                drawRoundRect(Muted, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .18f), style = stroke)
                val path = Path().apply {
                    moveTo(size.width * .43f, size.height * .31f)
                    lineTo(size.width * .67f, size.height * .5f)
                    lineTo(size.width * .43f, size.height * .69f)
                    close()
                }
                drawPath(path, Muted)
            }
            "comment" -> {
                drawRoundRect(Muted, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .16f), style = stroke)
                drawCircle(Muted, size.minDimension * .055f, Offset(size.width * .31f, size.height * .4f))
                drawLine(Muted, Offset(size.width * .43f, size.height * .4f), Offset(size.width * .7f, size.height * .4f), strokeWidth)
                drawCircle(Muted, size.minDimension * .055f, Offset(size.width * .31f, size.height * .62f))
                drawLine(Muted, Offset(size.width * .43f, size.height * .62f), Offset(size.width * .62f, size.height * .62f), strokeWidth)
            }
            "date" -> {
                drawRoundRect(Muted, Offset(left, top + size.height * .08f),
                    androidx.compose.ui.geometry.Size(right - left, bottom - top - size.height * .08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .12f), style = stroke)
                drawLine(Muted, Offset(left, size.height * .38f), Offset(right, size.height * .38f), strokeWidth)
                drawLine(Muted, Offset(size.width * .34f, size.height * .08f), Offset(size.width * .34f, size.height * .3f), strokeWidth)
                drawLine(Muted, Offset(size.width * .66f, size.height * .08f), Offset(size.width * .66f, size.height * .3f), strokeWidth)
            }
        }
    }
}

@Composable
private fun RemoteCover(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                try { connection.inputStream.use(BitmapFactory::decodeStream) } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7EDF2)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (bitmap == null) Text("B", color = Pink.copy(alpha = .75f), fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

private fun toSearchVideo(item: VideoSearchItem): SearchVideo {
    val cleanTitle = item.title.replace(Regex("<[^>]*>"), "")
    return SearchVideo(
        title = android.text.Html.fromHtml(cleanTitle, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
        author = item.author,
        playCount = formatPlayCount(item.play),
        commentCount = item.commentCount,
        publishTime = item.publishTime,
        duration = item.duration,
        bvid = item.bvid,
        coverUrl = when {
            item.pic.startsWith("//") -> "https:${item.pic}"
            item.pic.startsWith("http://") -> item.pic.replaceFirst("http://", "https://")
            else -> item.pic
        },
    )
}

private fun formatPlayCount(raw: String): String {
    val normalized = raw.trim().replace(",", "")
    val numeric = when {
        normalized.endsWith("万") -> normalized.removeSuffix("万").toDoubleOrNull()?.times(10_000.0)
        else -> normalized.toDoubleOrNull()
    } ?: return raw.ifBlank { "--" }
    if (numeric <= 10_000) return numeric.toLong().toString()
    val tenThousands = numeric / 10_000.0
    val formatted = String.format(Locale.US, "%.1f", tenThousands).trimEnd('0').trimEnd('.')
    return "$formatted 万".replace(" ", "")
}

private fun formatPublishDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "--"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("bili", color = Pink, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-1).sp)
        Text("box", color = Ink, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-1).sp)
        Spacer(Modifier.weight(1f))
        Text("让喜欢，自在发生", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-1).sp)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Muted, fontSize = 13.sp)
}

@Composable
private fun HomeContent(saved: Boolean, onSave: () -> Unit) {
    var liked by rememberSaveable { mutableStateOf(false) }
    SectionTitle("发现你的下一份喜欢", "放慢一点，好内容值得停留。")
    Spacer(Modifier.height(26.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("为你推荐", color = Pink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("生活", color = Muted, fontSize = 13.sp)
        Text("音乐", color = Muted, fontSize = 13.sp)
        Text("旅行", color = Muted, fontSize = 13.sp)
    }
    Spacer(Modifier.height(20.dp))
    Landscape(0, Modifier.fillMaxWidth().aspectRatio(1.25f))
    Spacer(Modifier.height(18.dp))
    Text("日落之前，去海边走走", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text("把城市的喧嚣，交给海浪。", color = Muted, fontSize = 13.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(Color(0xFFDDEBEA), CircleShape), contentAlignment = Alignment.Center) { Text("海", color = Color(0xFF6A9997), fontSize = 12.sp) }
        Spacer(Modifier.width(8.dp))
        Text("海边的阿树", color = Ink, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("12.8 万次观看", color = Muted, fontSize = 11.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = liked, onClick = { liked = !liked }, label = { Text(if (liked) "♥ 已点赞" else "♡ 点赞") })
        FilterChip(selected = saved, onClick = onSave, label = { Text(if (saved) "★ 已收藏" else "☆ 收藏") })
    }
    Spacer(Modifier.height(28.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("继续发现", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Text("为好奇心留一点时间", color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(16.dp))
    VideoCard("山野之间，有风经过", "山间来信 · 8.6 万次观看", 1)
    Spacer(Modifier.height(20.dp))
    VideoCard("今天，也要好好生活", "日常放映室 · 3.2 万次观看", 2)
}

@Composable
private fun CollectionPage(title: String, subtitle: String, cardTitle: String, description: String) {
    SectionTitle(title, subtitle)
    Spacer(Modifier.height(26.dp))
    Text("海边的阿树 · 今天 09:41", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    Text(description, color = Ink, fontSize = 17.sp)
    Spacer(Modifier.height(16.dp))
    VideoCard(cardTitle, "生活的片段，也可以很闪亮", 0)
}

@Composable
private fun EmptyCard(title: String, text: String) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .7f), RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun VideoCard(title: String, subtitle: String, variant: Int) {
    Landscape(variant, Modifier.fillMaxWidth().aspectRatio(1.9f))
    Spacer(Modifier.height(12.dp))
    Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(subtitle, color = Muted, fontSize = 11.sp)
}

/** Local artwork keeps the navigation demo usable offline; no video stream is attached. */
@Composable
private fun Landscape(variant: Int, modifier: Modifier = Modifier) {
    val sky = when (variant) {
        1 -> listOf(Color(0xFFAFCCCA), Color(0xFFE1E6D5), Color(0xFF789B8A))
        2 -> listOf(Color(0xFFD6B8C0), Color(0xFFF3DECC), Color(0xFFA99DAE))
        else -> listOf(Color(0xFF8DAEBE), Color(0xFFE9C8B4), Color(0xFF6B99A1))
    }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(sky))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawCircle(Color(0xFFFFE6B6), w * .095f, Offset(w * .72f, h * .36f))
            drawRect(Color(0xFF6B96A0).copy(alpha = .55f), Offset(0f, h * .60f), androidx.compose.ui.geometry.Size(w, h * .4f))
            val hill = Path().apply { moveTo(0f, h * .78f); cubicTo(w * .25f, h * .44f, w * .38f, h * .88f, w * .66f, h * .84f); cubicTo(w * .80f, h * .82f, w * .9f, h * .72f, w, h * .77f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(hill, Color(0xFF344F58).copy(alpha = .80f))
            for (i in 0..5) drawLine(Color.White.copy(alpha = .18f), Offset(w * .52f, h * (.66f + i * .025f)), Offset(w * .91f, h * (.66f + i * .025f)), 1.4f)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text(if (variant == 1) "INTO THE WILD" else "A LITTLE ESCAPE", color = Color.White.copy(alpha = .8f), fontSize = 9.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(7.dp))
            Text(if (variant == 1) "去有风的地方" else "和日落，见一面。", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
        Text("画面预览", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color.Black.copy(alpha = .15f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
        if (variant == 0) {
            Box(Modifier.align(Alignment.Center).size(58.dp).background(Color.White.copy(alpha = .88f), CircleShape), contentAlignment = Alignment.Center) {
                Text("▶", color = Color(0xFF4D6673), fontSize = 22.sp, modifier = Modifier.padding(start = 3.dp))
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.Black.copy(alpha = .2f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(42.dp).height(2.dp).background(Color.White.copy(alpha = .55f), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("01:24 / 04:52", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

/** A three-item capsule plus a detached profile button: (xxx) (x). */
@Composable
private fun LiquidNavigation(selected: Int, onSelect: (Int) -> Unit, backdrop: GraphicsLayer, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val gap = 12.dp
        val circle = 68.dp
        val capsuleWidth = maxWidth - circle - gap
        val itemWidth = (capsuleWidth - 12.dp) / 3
        val indicatorOffset by animateDpAsState(itemWidth * selected.coerceAtMost(2), label = "glass selection position")
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            GlassSurface(backdrop, Modifier.width(capsuleWidth).height(68.dp)) {
                Box(Modifier.fillMaxSize().padding(6.dp)) {
                    if (selected < 3) Box(Modifier.offset(x = indicatorOffset).width(itemWidth).fillMaxHeight()
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .38f))), CircleShape)
                        .then(Modifier.clip(CircleShape)))
                    Row(Modifier.fillMaxSize().selectableGroup()) {
                        listOf("首页", "动态", "视频").forEachIndexed { index, title ->
                            NavigationItem(index, title, selected == index, { onSelect(index) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            GlassSurface(backdrop, Modifier.size(circle)) {
                Box(Modifier.fillMaxSize().padding(6.dp).background(if (selected == 3) Color.White.copy(alpha = .8f) else Color.Transparent, CircleShape)) {
                    NavigationItem(3, "我的", selected == 3, { onSelect(3) }, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun GlassSurface(backdrop: GraphicsLayer, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val blur = with(LocalDensity.current) { 18.dp.toPx() }
    val supportsBlur = android.os.Build.VERSION.SDK_INT >= 31
    val shape = RoundedCornerShape(36.dp)
    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer { shadowElevation = 18.dp.toPx(); this.shape = shape; ambientShadowColor = Color(0xFF5D7288).copy(alpha = .22f); spotShadowColor = Color(0xFF5D7288).copy(alpha = .22f) }) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Box(Modifier.matchParentSize().graphicsLayer {
                if (supportsBlur) renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            }.drawWithContent { translate(-position.x, -position.y) { drawLayer(backdrop) } })
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (supportsBlur) .62f else .93f),
                Color(0xFFF0F5FA).copy(alpha = if (supportsBlur) .38f else .92f)
            ))))
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                drawRoundRect(Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .15f), Color.White.copy(alpha = .65f))),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(stroke))
            }
        }
        content()
    }
}

@Composable
private fun NavigationItem(index: Int, title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) Pink else Color(0xFF667786), label = "navigation tint")
    Column(modifier.fillMaxHeight().clip(CircleShape).selectable(selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NavIcon(index, color)
        Spacer(Modifier.height(3.dp))
        Text(title, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun NavIcon(index: Int, tint: Color) {
    Canvas(Modifier.size(23.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke.width, StrokeCap.Round)
        when (index) {
            0 -> {
                val path = Path().apply { moveTo(3 * unit, 10 * unit); lineTo(12 * unit, 3 * unit); lineTo(21 * unit, 10 * unit); lineTo(21 * unit, 20 * unit); lineTo(15 * unit, 20 * unit); lineTo(15 * unit, 14 * unit); lineTo(9 * unit, 14 * unit); lineTo(9 * unit, 20 * unit); lineTo(3 * unit, 20 * unit); close() }
                drawPath(path, tint, style = stroke)
            }
            1 -> {
                drawRoundRect(tint, Offset(3 * unit, 6 * unit), androidx.compose.ui.geometry.Size(18 * unit, 15 * unit), androidx.compose.ui.geometry.CornerRadius(4 * unit), style = stroke)
                line(8f, 2f, 10f, 6f); line(16f, 2f, 14f, 6f)
                line(8f, 12f, 8f, 15f); line(16f, 12f, 16f, 15f)
            }
            2 -> {
                drawRoundRect(tint, Offset(3 * unit, 7 * unit), androidx.compose.ui.geometry.Size(18 * unit, 14 * unit), androidx.compose.ui.geometry.CornerRadius(3 * unit), style = stroke)
                line(7f, 3f, 17f, 3f)
                val path = Path().apply { moveTo(10 * unit, 11 * unit); lineTo(15 * unit, 14 * unit); lineTo(10 * unit, 17 * unit); close() }
                drawPath(path, tint, style = stroke)
            }
            else -> {
                drawCircle(tint, 4 * unit, Offset(12 * unit, 7 * unit), style = stroke)
                val path = Path().apply { moveTo(4 * unit, 21 * unit); cubicTo(4 * unit, 12 * unit, 20 * unit, 12 * unit, 20 * unit, 21 * unit) }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BiliBoxPreview() { BiliBoxTheme { BiliBoxApp() } }
