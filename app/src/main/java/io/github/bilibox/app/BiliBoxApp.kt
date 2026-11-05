package io.github.bilibox.app

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    // “我的”页的通栏头图滚出屏幕后，浅色模式下恢复深色状态栏图标。
    val bannerScrollPx = with(LocalDensity.current) { 200.dp.toPx() }
    val pastBanner by remember(appState, bannerScrollPx) {
        derivedStateOf { appState.profileScroll.value > bannerScrollPx }
    }
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkMode && (appState.selectedTab != AppTab.PROFILE || pastBanner)
            isAppearanceLightNavigationBars = !darkMode
        }
    }
    // 进入“我的”页时，头图与头像从顶部完整露出。
    LaunchedEffect(appState.selectedTab) {
        if (appState.selectedTab == AppTab.PROFILE) appState.profileScroll.scrollTo(0)
    }
}
