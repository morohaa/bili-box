package io.github.bilibox.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.bilibox.core.ui.theme.AppearanceMode
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.core.ui.theme.resolveDark
import io.github.bilibox.data.appearance.AppearanceStore
import io.github.bilibox.data.appearance.HomeBackgroundImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 应用根布局：组合主题、系统栏外观与页面导航。 */
@Composable
internal fun BiliBoxApp() {
    val context = LocalContext.current
    val store = remember(context) { AppearanceStore(context) }
    var appearance by remember(store) { mutableStateOf(store.load()) }
    // 自定义首页背景是本地图片，启动时异步读回；没读完前按渐变绘制，不会白屏。
    val backgroundStore = remember(context) { HomeBackgroundImageStore(context) }
    var backgroundImage by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(backgroundStore) {
        backgroundImage = withContext(Dispatchers.IO) { backgroundStore.load()?.asImageBitmap() }
    }
    val systemDark = isSystemInDarkTheme()
    val darkMode = appearance.mode.resolveDark(systemDark)
    val appState = rememberBiliBoxAppState()

    fun updateAppearance(value: AppearanceSettings) {
        appearance = value
        store.save(value)
    }

    BiliBoxTheme(darkTheme = darkMode, accent = appearance.theme.base, dynamicColor = false) {
        AppSystemBars(appState, darkMode)
        AppNavigation(
            appState = appState,
            darkMode = darkMode,
            appearance = appearance,
            backgroundImage = backgroundImage,
            backgroundStore = backgroundStore,
            onBackgroundImageChange = { backgroundImage = it },
            onAppearanceChange = ::updateAppearance,
            onToggleDarkMode = {
                updateAppearance(
                    appearance.copy(
                        mode = if (darkMode) AppearanceMode.LIGHT else AppearanceMode.DARK,
                    )
                )
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
    // 外观页盖在「我的」页之上，必须单独读一次，否则 SideEffect 不会因它关闭而重跑。
    val appearanceOpen = appState.appearanceOpen
    // 头图预览是深色沉浸层，系统栏图标始终用白色。
    val bannerPreviewOpen = appState.bannerPreviewOpen
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = if (bannerPreviewOpen) {
                false
            } else {
                !darkMode && (
                    appearanceOpen ||
                        appState.selectedTab != AppTab.PROFILE ||
                        pastBanner
                    )
            }
            isAppearanceLightNavigationBars = if (bannerPreviewOpen) false else !darkMode
        }
    }
    // 进入“我的”页时，内容回到顶部。
    LaunchedEffect(appState.selectedTab) {
        if (appState.selectedTab == AppTab.PROFILE) appState.profileScroll.scrollTo(0)
    }
}
