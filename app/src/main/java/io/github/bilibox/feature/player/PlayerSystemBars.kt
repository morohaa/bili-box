package io.github.bilibox.feature.player

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * System-bar / inset appearance for the player.
 *
 * Kept in its own file so VideoPlayerScreen stays below the repository's
 * 1000-line limit while the player keeps one source of truth for status and
 * navigation bar behavior across portrait, vertical-fullscreen and landscape.
 */
@Composable
internal fun PlayerSystemBarsEffects(
    activity: Activity?,
    fullScreen: Boolean,
    verticalFullScreen: Boolean,
    controlsVisible: Boolean,
    screenLocked: Boolean,
    listenVisible: Boolean,
    detailsBackground: Color,
    onResetOverlays: () -> Unit,
    onVerticalFullScreenChange: (Boolean) -> Unit,
    onScreenLockedChange: (Boolean) -> Unit,
) {
    LaunchedEffect(activity, fullScreen, verticalFullScreen) {
        // 横屏全屏时以方向为准，避免退出横屏后又回到竖屏全屏。
        if (fullScreen) onVerticalFullScreenChange(false)
        onResetOverlays()
        if (!fullScreen) onScreenLockedChange(false)
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars =
                    !fullScreen && !verticalFullScreen && detailsBackground.luminance() > .5f
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullScreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                } else if (verticalFullScreen) {
                    // 竖屏全屏显示状态栏、隐藏底部导航栏；状态栏会随控件
                    // 自动显隐，顶部安全区用稳定 inset 保持高度不变。
                    show(WindowInsetsCompat.Type.statusBars())
                    hide(WindowInsetsCompat.Type.navigationBars())
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    LaunchedEffect(activity, verticalFullScreen, controlsVisible, screenLocked) {
        if (!verticalFullScreen) return@LaunchedEffect
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        } ?: return@LaunchedEffect
        if (controlsVisible && !screenLocked) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // Runs after the effect above, so the dark 听视频 page keeps light nav icons
    // even when opening it rotates the screen back to portrait.
    LaunchedEffect(activity, fullScreen, verticalFullScreen, listenVisible, detailsBackground) {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightNavigationBars =
                !listenVisible && !fullScreen && !verticalFullScreen && detailsBackground.luminance() > .5f
        }
    }
}

/** Keeps the 听视频 page composed while its reveal animation is still running. */
@Composable
internal fun rememberListenVisible(listenReveal: Animatable<Float, *>): Boolean {
    val visible by remember { derivedStateOf { listenReveal.value > 0f } }
    return visible
}
