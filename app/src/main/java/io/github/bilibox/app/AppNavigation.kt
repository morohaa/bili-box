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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.component.LiquidNavigation
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BrandSpringEasing
import io.github.bilibox.feature.appearance.AppearanceScreen
import io.github.bilibox.feature.home.HomeScreen
import io.github.bilibox.feature.library.LibraryScreen
import io.github.bilibox.feature.online.OnlineVideoScreen
import io.github.bilibox.feature.player.LocalVideoScreen
import io.github.bilibox.feature.profile.ProfileScreen
import io.github.bilibox.feature.profile.randomGhostAvatar

internal enum class AppTab { HOME, LIBRARY, PROFILE }

/** 组合功能页面与播放覆盖层；跨功能导航仅在应用组装层处理。 */
@Composable
internal fun AppNavigation(
    appState: BiliBoxAppState,
    darkMode: Boolean,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onToggleDarkMode: () -> Unit,
) {
    // 未登录默认头像在一次应用运行内固定：放在根组合里记住，切页、退后台、转屏都不会换脸，
    // 只有完全退出后重新启动（没有可恢复的保存状态）才会重新随机。
    val defaultAvatar = rememberSaveable { randomGhostAvatar() }

    if (appState.appearanceOpen) {
        AppearanceScreen(
            appearance = appearance,
            darkMode = darkMode,
            onBack = appState::closeAppearance,
            onChange = onAppearanceChange,
        )
        return
    }

    appState.selectedVideo?.let { video ->
        OnlineVideoScreen(
            video = video,
            session = appState.session,
            onBack = appState::closeOnlineVideo,
            onViewCache = {
                // Cached MP4s are published to Movies/BiliBox, so the local
                // video library is the natural "view cache" destination.
                appState.closeOnlineVideo()
                appState.selectTab(AppTab.LIBRARY.ordinal)
            },
        )
        return
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
                    initialFilter = appState.libraryFilter,
                    onToggleDarkMode = onToggleDarkMode,
                    onVideoSelected = appState::openLocalVideo,
                )

                AppTab.PROFILE -> ProfileScreen(
                    session = appState.session,
                    defaultAvatar = defaultAvatar,
                    darkMode = darkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenThemeAppearance = appState::openAppearance,
                    onOpenLibrary = appState::openLibrary,
                    onSessionChange = appState::updateSession,
                    scrollState = appState.profileScroll,
                    onBannerScrollLimitChange = appState::updateProfileBannerScrollLimit,
                )

                AppTab.HOME -> HomeScreen(
                    query = appState.searchQuery,
                    onQueryChange = appState::updateQuery,
                    onSearch = { appState.runSearch() },
                    onRetry = appState::runSearch,
                    onVideoClick = appState::openOnlineVideo,
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
                    appState::selectTab,
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
                onBack = appState::closeLocalVideo,
            )
        }
    }
}
