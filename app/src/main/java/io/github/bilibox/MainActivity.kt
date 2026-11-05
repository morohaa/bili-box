package io.github.bilibox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.LiquidNavigation
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.core.ui.theme.BrandSpringEasing
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.BiliSessionStore
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.feature.dynamic.DynamicPage
import io.github.bilibox.feature.library.LibraryPage
import io.github.bilibox.feature.online.BiliVideoDetail
import io.github.bilibox.feature.player.LocalVideoPlayer
import io.github.bilibox.feature.profile.MyPage
import io.github.bilibox.feature.search.SearchStateHolder
import io.github.bilibox.feature.search.SearchUiState
import kotlinx.coroutines.launch

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
    var searchOrder by rememberSaveable { mutableStateOf(SearchOrder.RELEVANCE) }
    var searchDuration by rememberSaveable { mutableStateOf<SearchDuration?>(null) }
    var searchTimeRange by rememberSaveable { mutableStateOf<SearchTimeRange?>(null) }
    var searchGrid by rememberSaveable { mutableStateOf(false) }
    // 动态页处于搜索态（输入中或展示结果）时，底部导航随参考稿下滑隐藏。
    var dynamicExpanded by rememberSaveable { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    val dynamicScroll = rememberScrollState()
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
    val searchHolder = remember(searchClient, scope) { SearchStateHolder(searchClient, scope) }
    DisposableEffect(searchHolder) { onDispose { searchHolder.reset() } }
    val searchState = searchHolder.state
    val runSearch: (Int, SearchOrder) -> Unit = { page, order ->
        val keyword = searchQuery.trim()
        if (keyword.isNotEmpty()) {
            searchOrder = order
            scope.launch { dynamicScroll.scrollTo(0) }
            searchHolder.search(keyword, page, order, searchDuration, searchTimeRange)
        }
    }
    var selectedVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var selectedLocalVideo by remember { mutableStateOf<LocalVideo?>(null) }
    var localPlaylist by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var libraryRefresh by remember { mutableIntStateOf(0) }
    val closeSearch = {
        searchHolder.reset()
        searchQuery = ""
        focusManager.clearFocus()
    }
    // 修改关键词即退出上一轮结果，回到“输入中”的历史面板（对齐参考稿）。
    val updateQuery: (String) -> Unit = { value ->
        searchQuery = value
        if (searchState != SearchUiState.Idle) {
            searchHolder.reset()
        }
    }
    selectedVideo?.let { video ->
        BiliVideoDetail(video, session) { selectedVideo = null }
        return
    }
    // 搜索态是动态页内部的独立界面：系统返回键先回到首页，再交给外层处理。
    BackHandler(enabled = tab == 0 && dynamicExpanded) { closeSearch() }
    val backdrop = rememberGraphicsLayer()
    val appBackground = if (darkMode) Color(0xFF17191D) else Color(0xFFF4F6FA)
    Box(Modifier.fillMaxSize().background(appBackground)) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                backdrop.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop)
            }
        ) {
            when (tab) {
                1 -> {
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
                }

                2 -> {
                    // “我的”页是通栏头图，独占整页滚动。
                    Column(Modifier.fillMaxSize().verticalScroll(pageScroll)) {
                        MyPage(
                            session = session,
                            darkMode = darkMode,
                            onToggleDarkMode = onToggleDarkMode,
                            onOpenLibrary = { filter ->
                                libraryFilter = filter
                                selected = 1
                            },
                            onSessionChange = { session = it },
                        )
                        Spacer(Modifier.height(150.dp))
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }

                else -> {
                    DynamicPage(
                        query = searchQuery,
                        onQueryChange = updateQuery,
                        onSearch = { runSearch(1, searchOrder) },
                        onRetry = { page, order -> runSearch(page, order) },
                        onVideoClick = { selectedVideo = it },
                        onPageChange = { runSearch(it, searchOrder) },
                        searchState = searchState,
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
                        onExpandedChange = { dynamicExpanded = it },
                        scrollState = dynamicScroll,
                        darkMode = darkMode,
                    )
                }
            }
        }
        if (selectedLocalVideo == null) {
            AnimatedVisibility(
                visible = !(tab == 0 && dynamicExpanded),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(420, easing = BrandSpringEasing),
                ) + fadeIn(tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(360, easing = BrandSpringEasing),
                ) + fadeOut(tween(140)),
            ) {
                LiquidNavigation(
                    tab,
                    { index ->
                        libraryFilter = null
                        selected = index.coerceIn(0, 2)
                    },
                    backdrop,
                    darkMode,
                    Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                )
            }
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
private fun BiliBoxPreview() {
    BiliBoxTheme { BiliBoxApp() }
}
