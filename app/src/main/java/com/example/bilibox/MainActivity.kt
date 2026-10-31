package com.example.bilibox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibox.ui.theme.BiliBoxTheme
import com.example.bilibox.bili.BiliClient
import com.example.bilibox.bili.BiliPlayback
import com.example.bilibox.bili.BiliQn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

private val Ink = Color(0xFF24313D)
private val Pink = Color(0xFFEF7196)
private val Muted = Color(0xFF82909D)

private data class SearchVideo(
    val title: String,
    val author: String,
    val playCount: String,
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

private sealed interface LocalVideoScanState {
    data object NeedsPermission : LocalVideoScanState
    data object Loading : LocalVideoScanState
    data class Ready(val videos: List<LocalVideo>) : LocalVideoScanState
    data class Error(val message: String) : LocalVideoScanState
}

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(val items: List<SearchVideo>) : SearchState
    data class Error(val message: String) : SearchState
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
    val scope = rememberCoroutineScope()
    var selectedVideo by remember { mutableStateOf<SearchVideo?>(null) }
    val backdrop = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F6FA))) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }) {
            // The scrollable page is captured once and shared by both glass surfaces.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 24.dp)) {
                Header()
                when (selected) {
                    0 -> {
                        if (selectedVideo != null) {
                            BiliVideoDetail(selectedVideo!!, session) { selectedVideo = null }
                        } else {
                            VideoSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = {
                                    val keyword = searchQuery.trim()
                                    if (keyword.isNotEmpty()) {
                                        searchState = SearchState.Loading
                                        scope.launch {
                                            searchState = try {
                                                SearchState.Success(searchBilibiliVideos(keyword))
                                            } catch (error: Exception) {
                                                SearchState.Error(error.message ?: "搜索失败，请稍后重试")
                                            }
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(26.dp))
                            when (val state = searchState) {
                                SearchState.Idle -> HomeContent(saved) { saved = !saved }
                                SearchState.Loading -> SearchLoading()
                                is SearchState.Error -> SearchError(state.message) {
                                    searchState = SearchState.Loading
                                    scope.launch {
                                        searchState = try {
                                            SearchState.Success(searchBilibiliVideos(searchQuery.trim()))
                                        } catch (error: Exception) {
                                            SearchState.Error(error.message ?: "搜索失败，请稍后重试")
                                        }
                                    }
                                }
                                is SearchState.Success -> SearchResults(state.items) { selectedVideo = it }
                            }
                        }
                    }
                    1 -> CollectionPage("动态", "关注的人，分享的日常", "今天的灵感", "沿着海岸线，收集一整个夏天")
                    2 -> {
                        LibraryPage(
                            source = librarySource,
                            onSourceChange = { librarySource = it },
                            hasSavedVideo = saved,
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
private fun LibraryPage(source: Int, onSourceChange: (Int) -> Unit, hasSavedVideo: Boolean) {
    LibrarySourceSwitcher(source, onSourceChange)
    Spacer(Modifier.height(24.dp))
    if (source == 0) {
        if (hasSavedVideo) {
            VideoCard("日落之前，去海边走走", "哔哩哔哩下载 · 1 个视频", 0)
        } else {
            EmptyCard("还没有哔哩哔哩下载", "从哔哩哔哩下载的视频会出现在这里。")
        }
    } else {
        LocalVideoPage()
    }
}

@Composable
private fun LocalVideoPage() {
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
                    LocalVideoRow(context, video)
                    if (index != state.videos.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalVideoRow(context: Context, video: LocalVideo) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = .78f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).background(Pink.copy(alpha = .12f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Text("▶", color = Pink, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Text(
                "${formatVideoDuration(video.durationMs)} · ${Formatter.formatFileSize(context, video.sizeBytes)}",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
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
private fun SearchResults(items: List<SearchVideo>, onVideoClick: (SearchVideo) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("搜索结果", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${items.size} 个视频", color = Muted, fontSize = 12.sp)
    }
    if (items.isEmpty()) {
        EmptyCard("没有找到视频", "换个关键词试试看。")
    } else {
        items.forEach { video ->
            SearchResultCard(video) { onVideoClick(video) }
            Spacer(Modifier.height(14.dp))
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
        val dataSourceFactory = DefaultDataSource.Factory(context, BiliPlayback.dataSourceFactory(session?.cookie))
        ExoPlayer.Builder(context)
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
                    BiliPlayback.attach(exoPlayer, data, preferredQn = BiliQn.P1080, candidateIndex = nextIndex, cookie = session?.cookie)
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
                            BiliPlayback.attach(exoPlayer, fallback, preferredQn = BiliQn.P720, cookie = session?.cookie)
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
            BiliPlayback.attach(exoPlayer, play, preferredQn = BiliQn.P1080, candidateIndex = 0, cookie = session?.cookie)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackState = PlaybackState.Error(error.message ?: "无法获取视频播放地址")
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Ink, fontSize = 32.sp, modifier = Modifier.padding(end = 8.dp))
            Text("返回搜索结果", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(video.title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
        Spacer(Modifier.height(6.dp))
        Text("${video.author}  ·  ${video.playCount} 次播放", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        when (val state = playbackState) {
            PlaybackState.Loading -> Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF18222C)), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("正在获取视频流…", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                }
            }
            is PlaybackState.Error -> Column(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF18222C)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("暂时无法播放", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(state.message, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { retryToken++ }) { Text("重试", color = Color.White) }
            }
            PlaybackState.Ready -> AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        keepScreenOn = true
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (session == null) "匿名播放 · ${video.duration} · 登录后可尝试更高画质"
            else "已登录播放 · ${video.duration} · 使用账号权限获取视频流",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SearchResultCard(video: SearchVideo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .86f)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteCover(video.coverUrl, Modifier.width(132.dp).height(82.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 19.sp)
            Spacer(Modifier.height(7.dp))
            Text(video.author, color = Muted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("▶ ${video.playCount}   ·   ${video.duration}", color = Muted, fontSize = 10.sp, maxLines = 1)
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

private suspend fun searchBilibiliVideos(keyword: String): List<SearchVideo> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
    val url = URL("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded&page=1")
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
    connection.setRequestProperty("Referer", "https://www.bilibili.com/")
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (status !in 200..299) error("B 站接口暂不可用（HTTP $status）")
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) error(root.optString("message").ifBlank { "B 站搜索暂不可用" })
        val result = root.optJSONObject("data")?.optJSONArray("result")
            ?: return@withContext emptyList()
        buildList {
            for (index in 0 until minOf(result.length(), 20)) {
                val item = result.optJSONObject(index) ?: continue
                val bvid = item.optString("bvid")
                if (bvid.isBlank()) continue
                val rawTitle = item.optString("title").replace(Regex("<[^>]*>"), "")
                add(SearchVideo(
                    title = android.text.Html.fromHtml(rawTitle, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
                    author = item.optString("author", "未知 UP 主"),
                    playCount = item.optString("play", "--"),
                    duration = item.optString("duration", "--:--"),
                    bvid = bvid,
                    coverUrl = item.optString("pic").let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }
                ))
            }
        }
    } finally {
        connection.disconnect()
    }
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
