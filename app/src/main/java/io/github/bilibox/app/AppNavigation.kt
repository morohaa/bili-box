package io.github.bilibox.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.component.LiquidNavigation
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.BrandSpringEasing
import io.github.bilibox.data.appearance.BackgroundImageStore
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.favorites.BiliFavorites
import io.github.bilibox.data.profile.ProfileBannerStore
import io.github.bilibox.feature.appearance.AppearanceScreen
import io.github.bilibox.feature.favorites.FavoritesScreen
import io.github.bilibox.feature.favorites.FavoritesStateHolder
import io.github.bilibox.feature.home.HomeScreen
import io.github.bilibox.feature.library.LibraryScreen
import io.github.bilibox.feature.offline.OfflineCacheScreen
import io.github.bilibox.feature.online.OnlineVideoScreen
import io.github.bilibox.feature.player.LocalVideoScreen
import io.github.bilibox.feature.profile.ProfileBannerPreviewScreen
import io.github.bilibox.feature.profile.ProfileScreen
import io.github.bilibox.feature.profile.randomGhostAvatar
import io.github.bilibox.feature.profile.rememberRemoteBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AppTab { HOME, LIBRARY, PROFILE }

/** 组合功能页面与播放覆盖层；跨功能导航仅在应用组装层处理。 */
@Composable
internal fun AppNavigation(
    appState: BiliBoxAppState,
    darkMode: Boolean,
    appearance: AppearanceSettings,
    backgroundImages: Map<BackgroundPage, ImageBitmap>,
    backgroundStores: Map<BackgroundPage, BackgroundImageStore>,
    onBackgroundImageChange: (BackgroundPage, ImageBitmap?) -> Unit,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onToggleDarkMode: (Offset) -> Unit,
) {
    // 未登录默认头像在一次应用运行内固定：放在根组合里记住，切页、退后台、转屏都不会换脸，
    // 只有完全退出后重新启动（没有可恢复的保存状态）才会重新随机。
    val defaultAvatar = rememberSaveable { randomGhostAvatar() }
    val context = LocalContext.current
    val bannerStore = remember(context) { ProfileBannerStore(context) }
    var customBanner by remember { mutableStateOf<ImageBitmap?>(null) }
    var bannerAdjustments by remember(bannerStore) { mutableStateOf(bannerStore.loadAdjustments()) }
    // 自定义头图存在应用私有目录，进前台时异步读回；未读完前先展示主题默认头图。
    LaunchedEffect(bannerStore) {
        customBanner = withContext(Dispatchers.IO) { bannerStore.load()?.asImageBitmap() }
    }
    // 个人页与头图预览共用同一份头像位图，避免预览时重复下载。
    val avatar = rememberRemoteBitmap(appState.session?.avatarUrl)
    val favoritesScope = rememberCoroutineScope()
    val favorites = remember(appState.session?.cookie) {
        appState.session?.let { session ->
            FavoritesStateHolder(BiliFavorites(BiliClient().also { it.cookieHeader = session.cookie }), favoritesScope)
        }
    }
    DisposableEffect(favorites) { onDispose { favorites?.dispose() } }
    val favoritesFolderScroll = rememberLazyListState()
    val favoritesVideoScroll = rememberLazyListState()

    val pageTransition = rememberAppPageTransitionState()
    AppPageTransition(pageTransition, if (darkMode) Color(0xFF17191D) else Color(0xFFF4F6FA)) {
        if (appState.appearanceOpen) {
            AppearanceScreen(
                appearance = appearance,
                darkMode = darkMode,
                backgroundImages = backgroundImages,
                backgroundStores = backgroundStores,
                onBackgroundImageChange = onBackgroundImageChange,
                onBack = { pageTransition.back(appState::closeAppearance) },
                onChange = onAppearanceChange,
            )
            return@AppPageTransition
        }

        if (appState.bannerPreviewOpen) {
            ProfileBannerPreviewScreen(
                avatar = avatar,
                customBanner = customBanner,
                adjustments = bannerAdjustments,
                store = bannerStore,
                onBannerChange = { customBanner = it },
                onAdjustmentsChange = {
                    bannerAdjustments = it
                    bannerStore.saveAdjustments(it)
                },
                onClose = { pageTransition.back(appState::closeBannerPreview) },
            )
            return@AppPageTransition
        }

        appState.selectedVideo?.let { video ->
            OnlineVideoScreen(
                video = video,
                session = appState.session,
                onBack = { pageTransition.back(appState::closeOnlineVideo) },
                onViewCache = {
                    pageTransition.forward {
                        appState.closeOnlineVideo()
                        appState.openOfflineCache()
                    }
                },
            )
            return@AppPageTransition
        }

        if (appState.offlineCacheOpen) {
            Box(Modifier.fillMaxSize()) {
                OfflineCacheScreen(
                    refreshToken = appState.libraryRefresh,
                    onBack = { pageTransition.back(appState::closeOfflineCache) },
                    onPlay = { video, playlist -> pageTransition.forward { appState.openLocalVideo(video, playlist) } },
                    onForward = { action -> pageTransition.forward(action) },
                    onBackward = { action -> pageTransition.back(action) },
                )
                appState.selectedLocalVideo?.let { video ->
                    LocalVideoScreen(
                        video = video,
                        playlist = appState.localPlaylist,
                        onVideoSelected = appState::selectLocalVideo,
                        onBack = { pageTransition.back(appState::closeLocalVideo) },
                    )
                }
            }
            return@AppPageTransition
        }

        if (appState.favoritesOpen) {
            FavoritesScreen(
                darkMode = darkMode,
                holder = favorites,
                folderListState = favoritesFolderScroll,
                videoListState = favoritesVideoScroll,
                onBack = { pageTransition.back(appState::closeFavorites) },
                onVideoClick = { video -> pageTransition.forward { appState.openOnlineVideo(video) } },
            )
            return@AppPageTransition
        }

        val focusManager = LocalFocusManager.current
        // 搜索态属于首页：系统返回键先退出搜索，再交给外层处理。
        BackHandler(enabled = appState.selectedTab == AppTab.HOME && appState.homeExpanded) {
            appState.closeSearch()
            focusManager.clearFocus()
        }
        val backdrop = rememberGraphicsLayer()
        val appBackground = if (darkMode) Color(0xFF17191D) else Color(0xFFF4F6FA)
        Box(Modifier.fillMaxSize().background(appBackground)) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                }
            ) {
                when (appState.selectedTab) {
                    AppTab.LIBRARY -> LibraryScreen(
                        refreshToken = appState.libraryRefresh,
                        darkMode = darkMode,
                        backgroundSettings = appearance.videoBackground,
                        backgroundImage = backgroundImages[BackgroundPage.VIDEO],
                        initialFilter = appState.libraryFilter,
                        onVideoSelected = { video, playlist -> pageTransition.forward { appState.openLocalVideo(video, playlist) } },
                    )

                    AppTab.PROFILE -> ProfileScreen(
                        session = appState.session,
                        avatar = avatar,
                        defaultAvatar = defaultAvatar,
                        customBanner = customBanner,
                        bannerAdjustments = bannerAdjustments,
                        darkMode = darkMode,
                        backgroundSettings = appearance.profileBackground,
                        backgroundImage = backgroundImages[BackgroundPage.PROFILE],
                        onToggleDarkMode = onToggleDarkMode,
                        onOpenThemeAppearance = { pageTransition.forward(appState::openAppearance) },
                        onOpenLibrary = { filter ->
                            pageTransition.tab(appState.selectedTab, AppTab.LIBRARY) { appState.openLibrary(filter) }
                        },
                        onOpenOfflineCache = { pageTransition.forward(appState::openOfflineCache) },
                        onOpenFavorites = { pageTransition.forward(appState::openFavorites) },
                        onBannerClick = { pageTransition.forward(appState::openBannerPreview) },
                        onSessionChange = appState::updateSession,
                        scrollState = appState.profileScroll,
                        onBannerScrollLimitChange = appState::updateProfileBannerScrollLimit,
                    )

                    AppTab.HOME -> HomeScreen(
                        query = appState.searchQuery,
                        onQueryChange = appState::updateQuery,
                        onSearch = { appState.runSearch() },
                        onRetry = appState::runSearch,
                        onVideoClick = { video -> pageTransition.forward { appState.openOnlineVideo(video) } },
                        onPageChange = { appState.runSearch(page = it) },
                        searchState = appState.searchState,
                        order = appState.searchOrder,
                        onOrderChange = { appState.runSearch(order = it) },
                        duration = appState.searchDuration,
                        timeRange = appState.searchTimeRange,
                        onFiltersChange = appState::updateSearchFilters,
                        gridLayout = appState.searchGrid,
                        onToggleLayout = { appState.searchGrid = !appState.searchGrid },
                        onExpandedChange = { appState.homeExpanded = it },
                        scrollState = appState.homeScroll,
                        darkMode = darkMode,
                        backgroundStyle = appearance.background,
                        backgroundImage = backgroundImages[BackgroundPage.HOME],
                        backgroundBlur = appearance.backgroundBlur,
                        backgroundDarkness = appearance.backgroundDarkness,
                    )
                }
            }
            if (appState.selectedLocalVideo == null) {
                AnimatedVisibility(
                    visible = !(appState.selectedTab == AppTab.HOME && appState.homeExpanded),
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
                        appState.selectedTab.ordinal,
                        { index ->
                            pageTransition.tab(appState.selectedTab, AppTab.entries[index]) { appState.selectTab(index) }
                        },
                        backdrop,
                        darkMode,
                        Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                    )
                }
            }
            // 保持下层视频库处于组合中，播放结束后保留目录、搜索与滚动位置。
            appState.selectedLocalVideo?.let { video ->
                LocalVideoScreen(
                    video = video,
                    playlist = appState.localPlaylist,
                    onVideoSelected = appState::selectLocalVideo,
                    onBack = { pageTransition.back(appState::closeLocalVideo) },
                )
            }
        }
    }
}
