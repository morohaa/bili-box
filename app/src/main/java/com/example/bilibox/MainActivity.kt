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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.bilibox.bili.Page
import com.example.bilibox.bili.BiliPlayback
import com.example.bilibox.bili.BiliQn
import com.example.bilibox.bili.VideoInfo
import com.example.bilibox.bili.VideoSearchItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("appearance", Context.MODE_PRIVATE) }
            var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
            BiliBoxTheme(darkTheme = darkMode, dynamicColor = false) {
                BiliBoxApp(darkMode) {
                    darkMode = !darkMode
                    preferences.edit().putBoolean("dark_mode", darkMode).apply()
                }
            }
        }
    }
}

@Composable
private fun BiliBoxApp(darkMode: Boolean = false, onToggleDarkMode: () -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // 0 动态 / 1 视频 / 2 我的. Clamp restored state from the old four-tab layout.
    val tab = selected.coerceIn(0, 2)
    var session by remember { mutableStateOf<BiliSession?>(BiliSessionStore.load(context)) }
    var libraryFilter by remember { mutableStateOf<LocalVideoQuickFilter?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var searchOrder by rememberSaveable { mutableStateOf(SearchOrder.RELEVANCE) }
    var searchDuration by rememberSaveable { mutableStateOf<SearchDuration?>(null) }
    var searchTimeRange by rememberSaveable { mutableStateOf<SearchTimeRange?>(null) }
    var searchGrid by rememberSaveable { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val pageScroll = rememberScrollState()
    // “我的”页是通栏头图，状态栏图标默认用白色压在图片上；头图滚出屏幕后
    // 状态栏落到浅色页面上，需要换回深色图标，否则会看不见。
    val bannerScrollPx = with(LocalDensity.current) { 200.dp.toPx() }
    val pastBanner by remember(bannerScrollPx) {
        derivedStateOf { pageScroll.value > bannerScrollPx }
    }
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = when {
                tab != 2 -> !darkMode
                else -> !darkMode && pastBanner
            }
            isAppearanceLightNavigationBars = !darkMode
        }
    }
    // 进入“我的”页时回到顶部：头图与头像需要从顶部完整露出。
    LaunchedEffect(tab) { if (tab == 2) pageScroll.scrollTo(0) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val searchClient = remember(session?.cookie) {
        BiliClient().also { it.cookieHeader = session?.cookie }
    }
    val runSearch: (Int, SearchOrder) -> Unit = { page, order ->
        val keyword = searchQuery.trim()
        if (keyword.isNotEmpty()) {
            val nowSeconds = System.currentTimeMillis() / 1000L
            val requestedDuration = searchDuration
            val requestedRange = searchTimeRange
            searchOrder = order
            searchState = SearchState.Loading
            searchJob?.cancel()
            searchJob = scope.launch {
                pageScroll.scrollTo(0)
                searchState = try {
                    val result = searchClient.searchVideos(
                        keyword = keyword,
                        page = page,
                        order = order.apiValue,
                        duration = requestedDuration?.apiValue,
                        pubtimeBegin = requestedRange?.let { nowSeconds - it.days * 86_400L },
                        pubtimeEnd = requestedRange?.let { nowSeconds },
                    )
                    SearchState.Success(
                        items = result.items.map(::toSearchVideo),
                        page = result.page,
                        hasMore = result.hasMore,
                        order = order,
                        total = result.total,
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
    var libraryRefresh by remember { mutableIntStateOf(0) }
    val searchActive = tab == 0 && searchState != SearchState.Idle
    val closeSearch = {
        searchJob?.cancel()
        searchState = SearchState.Idle
        focusManager.clearFocus()
    }
    selectedVideo?.let { video ->
        BiliVideoDetail(video, session) { selectedVideo = null }
        return
    }
    // 搜索结果是独立页面：系统返回键先退出搜索，再交给外层页面处理。
    BackHandler(enabled = searchActive) { closeSearch() }
    val backdrop = rememberGraphicsLayer()
    val appBackground = if (darkMode) Color(0xFF17191D) else Color(0xFFF4F6FA)
    Box(Modifier.fillMaxSize().background(appBackground)) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }) {
            if (tab == 1) {
                // The local-video browser owns its full-height layout so folders, the
                // settings sheet and the floating resume card can use the whole screen.
                LibraryPage(
                    refreshToken = libraryRefresh,
                    darkMode = darkMode,
                    initialFilter = libraryFilter,
                    onToggleDarkMode = onToggleDarkMode,
                    onVideoSelected = { video, playlist ->
                        localPlaylist = playlist
                        selectedLocalVideo = video
                    },
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    // “我的”页使用通栏头图，不再叠加固定顶栏；其余 Tab 共用同一条栏。
                    // 固定顶栏：不跟随内容滚动，三个 Tab 共用尺寸与下边界。
                    if (tab != 2) {
                        if (searchActive) {
                            SearchTopBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSubmit = { runSearch(1, searchOrder) },
                                onBack = closeSearch,
                                order = searchOrder,
                                onOrderChange = { runSearch(1, it) },
                                duration = searchDuration,
                                timeRange = searchTimeRange,
                                onFiltersChange = { duration, timeRange ->
                                    searchDuration = duration
                                    searchTimeRange = timeRange
                                    runSearch(1, searchOrder)
                                },
                                gridLayout = searchGrid,
                                onToggleLayout = { searchGrid = !searchGrid },
                                darkMode = darkMode,
                            )
                        } else if (tab == 0) {
                            AppTopBar(darkMode = darkMode) {
                                DynamicTopBarContent(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = { runSearch(1, searchOrder) },
                                    darkMode = darkMode,
                                )
                                Spacer(Modifier.width(2.dp))
                                DarkModeToggle(darkMode, onToggleDarkMode)
                            }
                        } else {
                            AppTopBar(darkMode = darkMode, onToggleDarkMode = onToggleDarkMode)
                        }
                    }
                    // The scrollable page is captured once and shared by both glass surfaces.
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(pageScroll)
                            .padding(horizontal = if (tab == 2) 0.dp else 16.dp)
                    ) {
                        when (tab) {
                            0 -> when (val state = searchState) {
                                SearchState.Idle -> Unit
                                SearchState.Loading -> SearchLoading(darkMode)
                                is SearchState.Error -> SearchError(state.message, darkMode) { runSearch(state.page, state.order) }
                                is SearchState.Success -> {
                                    SearchResults(
                                        state = state,
                                        darkMode = darkMode,
                                        gridLayout = searchGrid,
                                        onVideoClick = { selectedVideo = it },
                                        onPageChange = { runSearch(it, state.order) },
                                    )
                                }
                            }
                            else -> MyPage(
                                session = session,
                                darkMode = darkMode,
                                onToggleDarkMode = onToggleDarkMode,
                                onOpenLibrary = { filter ->
                                    libraryFilter = filter
                                    selected = 1
                                },
                                onSessionChange = { session = it },
                            )
                        }
                        Spacer(Modifier.height(150.dp))
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
        if (selectedLocalVideo == null) {
            LiquidNavigation(tab, { index ->
                libraryFilter = null
                selected = index.coerceIn(0, 2)
            }, backdrop, darkMode,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
        }
        // Keep the library composed underneath so its folder position, search and
        // scroll state survive a playback session.
        selectedLocalVideo?.let { video ->
            LocalVideoPlayer(
                video = video,
                playlist = localPlaylist,
                onVideoSelected = { selectedLocalVideo = it },
                onBack = {
                    selectedLocalVideo = null
                    libraryRefresh++
                },
            )
        }
    }
}

@Composable
private fun BiliBoxPreview() { BiliBoxTheme { BiliBoxApp() } }
