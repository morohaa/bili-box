package io.github.bilibox.app

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.bilibox.core.ui.theme.BiliBoxTheme

/** 应用根布局：组合主题、系统栏外观与页面导航。 */
@Composable
internal fun BiliBoxApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var darkMode by remember(preferences) { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
    val appState = rememberBiliBoxAppState()

    BiliBoxTheme(darkTheme = darkMode, dynamicColor = false) {
        AppSystemBars(appState, darkMode)
        AppNavigation(
            appState = appState,
            darkMode = darkMode,
            onToggleDarkMode = {
                darkMode = !darkMode
                preferences.edit().putBoolean("dark_mode", darkMode).apply()
            },
        )
    }
}

@Composable
private fun AppSystemBars(appState: BiliBoxAppState, darkMode: Boolean) {
    val view = LocalView.current
    // 头图固定时阈值是 Int.MAX_VALUE，这一页始终用浅色图标；矮视口整页滚动时才按滚动位置切换。
    val pastBannerState = remember(appState) {
        derivedStateOf {
            val limit = appState.profileBannerScrollLimitPx
            limit != Int.MAX_VALUE && appState.profileScroll.value > limit
        }
    }
    // 在组合中读取，越过阈值时才会重组，SideEffect 才能重新应用系统栏外观。
    val pastBanner = pastBannerState.value
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkMode && (appState.selectedTab != AppTab.PROFILE || pastBanner)
            isAppearanceLightNavigationBars = !darkMode
        }
    }
    // 进入“我的”页时，内容回到顶部。
    LaunchedEffect(appState.selectedTab) {
        if (appState.selectedTab == AppTab.PROFILE) appState.profileScroll.scrollTo(0)
    }
}
