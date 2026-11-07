package io.github.bilibox.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.BiliSessionStore
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.feature.search.SearchStateHolder
import io.github.bilibox.feature.search.SearchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun rememberBiliBoxAppState(): BiliBoxAppState {
    val context = LocalContext.current
    val initialSession = remember(context) { BiliSessionStore.load(context) }
    val scope = rememberCoroutineScope()
    val homeScroll = rememberScrollState()
    val profileScroll = rememberScrollState()
    val saver = remember(initialSession, scope, homeScroll, profileScroll) {
        BiliBoxAppState.saver(initialSession, scope, homeScroll, profileScroll)
    }
    val appState = rememberSaveable(saver = saver) {
        BiliBoxAppState(initialSession, scope, homeScroll, profileScroll)
    }
    DisposableEffect(appState) { onDispose { appState.dispose() } }
    return appState
}

/** 应用级会话、导航与搜索组装；具体搜索请求仍由搜索功能管理。 */
internal class BiliBoxAppState(
    initialSession: BiliSession?,
    private val scope: CoroutineScope,
    val homeScroll: ScrollState,
    val profileScroll: ScrollState,
) {
    private var selectedIndex by mutableIntStateOf(0)

    // 兼容旧四标签布局恢复的索引。
    val selectedTab: AppTab get() = AppTab.entries[selectedIndex.coerceIn(0, AppTab.entries.lastIndex)]

    var session by mutableStateOf(initialSession)
        private set
    private var searchHolder by mutableStateOf(createSearchHolder(initialSession))
    val searchState: SearchUiState get() = searchHolder.state

    var searchQuery by mutableStateOf("")
        private set
    var searchOrder by mutableStateOf(SearchOrder.RELEVANCE)
        private set
    var searchDuration by mutableStateOf<SearchDuration?>(null)
        private set
    var searchTimeRange by mutableStateOf<SearchTimeRange?>(null)
        private set
    var searchGrid by mutableStateOf(false)
    var homeExpanded by mutableStateOf(false)

    var libraryFilter by mutableStateOf<LocalVideoQuickFilter?>(null)
        private set
    var selectedVideo by mutableStateOf<VideoSummary?>(null)
        private set
    var selectedLocalVideo by mutableStateOf<LocalVideo?>(null)
        private set
    var localPlaylist by mutableStateOf<List<LocalVideo>>(emptyList())
        private set
    var libraryRefresh by mutableIntStateOf(0)
        private set

    // 「主题外观」是覆盖在「我的」页上的整页设置，关闭后回到原标签。
    var appearanceOpen by mutableStateOf(false)
        private set

    // “我的”页头图滚过状态栏的阈值（px）：只在矮视口整页滚动时有效，固定头图时为 Int.MAX_VALUE。
    var profileBannerScrollLimitPx by mutableIntStateOf(Int.MAX_VALUE)
        private set

    fun updateProfileBannerScrollLimit(px: Int) {
        profileBannerScrollLimitPx = px
    }

    fun selectTab(index: Int) {
        libraryFilter = null
        selectedIndex = index.coerceIn(0, AppTab.entries.lastIndex)
    }

    fun openLibrary(filter: LocalVideoQuickFilter?) {
        libraryFilter = filter
        selectedIndex = AppTab.LIBRARY.ordinal
    }

    fun openAppearance() {
        appearanceOpen = true
    }

    fun closeAppearance() {
        appearanceOpen = false
    }

    fun updateSession(value: BiliSession?) {
        if (value?.cookie != session?.cookie) {
            searchHolder.reset()
            searchHolder = createSearchHolder(value)
        }
        session = value
    }

    fun updateQuery(value: String) {
        searchQuery = value
        if (searchState != SearchUiState.Idle) searchHolder.reset()
    }

    fun runSearch(page: Int = 1, order: SearchOrder = searchOrder) {
        val keyword = searchQuery.trim()
        if (keyword.isNotEmpty()) {
            searchOrder = order
            scope.launch { homeScroll.scrollTo(0) }
            searchHolder.search(keyword, page, order, searchDuration, searchTimeRange)
        }
    }

    fun updateSearchFilters(duration: SearchDuration?, timeRange: SearchTimeRange?) {
        searchDuration = duration
        searchTimeRange = timeRange
        runSearch()
    }

    fun closeSearch() {
        searchHolder.reset()
        searchQuery = ""
    }

    fun dispose() {
        searchHolder.reset()
    }

    fun openOnlineVideo(video: VideoSummary) {
        selectedVideo = video
    }

    fun closeOnlineVideo() {
        selectedVideo = null
    }

    fun openLocalVideo(video: LocalVideo, playlist: List<LocalVideo>) {
        localPlaylist = playlist
        selectedLocalVideo = video
    }

    fun selectLocalVideo(video: LocalVideo) {
        selectedLocalVideo = video
    }

    fun closeLocalVideo() {
        selectedLocalVideo = null
        libraryRefresh++
    }

    private fun createSearchHolder(session: BiliSession?): SearchStateHolder = SearchStateHolder(
        BiliClient().also { it.cookieHeader = session?.cookie },
        scope,
    )

    companion object {
        // 只恢复原先 rememberSaveable 的导航与搜索选项；会话始终从加密存储加载，
        // 播放对象、结果与视频库临时筛选保持原有的非持久生命周期。
        fun saver(
            initialSession: BiliSession?,
            scope: CoroutineScope,
            homeScroll: ScrollState,
            profileScroll: ScrollState,
        ): Saver<BiliBoxAppState, Any> = mapSaver(
            save = {
                mapOf(
                    "selectedIndex" to it.selectedIndex,
                    "searchQuery" to it.searchQuery,
                    "searchOrder" to it.searchOrder.name,
                    "searchDuration" to it.searchDuration?.name,
                    "searchTimeRange" to it.searchTimeRange?.name,
                    "searchGrid" to it.searchGrid,
                    "homeExpanded" to it.homeExpanded,
                    "appearanceOpen" to it.appearanceOpen,
                )
            },
            restore = {
                BiliBoxAppState(initialSession, scope, homeScroll, profileScroll).apply {
                    selectedIndex = it["selectedIndex"] as Int
                    searchQuery = it["searchQuery"] as String
                    searchOrder = SearchOrder.valueOf(it["searchOrder"] as String)
                    searchDuration = (it["searchDuration"] as String?)?.let(SearchDuration::valueOf)
                    searchTimeRange = (it["searchTimeRange"] as String?)?.let(SearchTimeRange::valueOf)
                    searchGrid = it["searchGrid"] as Boolean
                    homeExpanded = it["homeExpanded"] as Boolean
                    appearanceOpen = it["appearanceOpen"] as Boolean? ?: false
                }
            },
        )
    }
}
