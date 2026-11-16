package io.github.bilibox.feature.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerScreenTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun landscapeShortcutEntersPortraitFullscreenAndKeepsVideoMounted() {
        var orientation by mutableIntStateOf(Configuration.ORIENTATION_LANDSCAPE)
        lateinit var activity: ComponentActivity
        lateinit var player: ExoPlayer
        compose.setContent {
            val context = LocalContext.current
            activity = checkNotNull(context.findActivity())
            player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            val configuration = Configuration(LocalConfiguration.current).apply {
                this.orientation = orientation
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                VideoPlayerScreen(
                    player = player,
                    title = "横竖屏切换测试",
                    sourceLabel = "本地视频",
                    commentMessage = "测试",
                    onBack = {},
                    onRetry = {},
                    details = { Text("视频详情") },
                )
            }
        }
        val surface = playerView()
        compose.runOnIdle { player.setPlaybackSpeed(1.5f) }
        compose.onNodeWithContentDescription("切换竖屏全屏").performClick()
        // 横屏配置尚未更新时，不能提前清掉竖屏全屏请求。
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        compose.mainClock.advanceTimeByFrame()
        assertPortraitFullscreenFrame()
        compose.mainClock.advanceTimeBy(160)
        assertPortraitFullscreenFrame()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("退出竖屏").assertExists()
        compose.onNodeWithContentDescription("切换竖屏全屏").assertDoesNotExist()
        compose.onNodeWithText("视频详情").assertDoesNotExist()
        assertSame(surface, playerView())
        compose.runOnIdle { assertEquals(1.5f, player.playbackParameters.speed, 0f) }
        compose.onNodeWithContentDescription("退出竖屏").performClick()
        compose.onNodeWithText("视频详情").assertExists()

        // 再次进入横屏后，普通返回竖屏不应遗留全屏模式。
        compose.runOnIdle { orientation = Configuration.ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.runOnIdle { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription("竖屏").assertExists()
        compose.onNodeWithText("视频详情").assertExists()
    }

    @Test
    fun portraitTransitionAnimatesBothWaysAndCanReverseWithoutReplacingVideo() {
        lateinit var player: ExoPlayer
        compose.setContent {
            val context = LocalContext.current
            player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            VideoPlayerScreen(
                player = player,
                title = "过渡测试",
                sourceLabel = "本地视频",
                commentMessage = "测试",
                onBack = {},
                onRetry = {},
                details = { Text("视频详情") },
            )
        }
        compose.runOnIdle { player.setPlaybackSpeed(1.5f) }
        val surface = playerView()
        val compactHeight = videoHeight()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithContentDescription("竖屏").performClick()
        compose.mainClock.advanceTimeBy(160)
        val enteringHeight = videoHeight()
        assertTrue("进入时应逐步展开", enteringHeight > compactHeight)
        compose.mainClock.advanceTimeBy(400)
        val expandedHeight = videoHeight()
        assertTrue("进入动画中间帧应小于全屏高度", enteringHeight < expandedHeight)
        compose.onNodeWithText("视频详情").assertDoesNotExist()
        assertSame(surface, playerView())

        compose.onNodeWithContentDescription("退出竖屏").performClick()
        compose.mainClock.advanceTimeBy(160)
        val exitingHeight = videoHeight()
        assertTrue("退出时应逐步收回", exitingHeight > compactHeight && exitingHeight < expandedHeight)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(compactHeight, videoHeight(), 1f)
        compose.onNodeWithText("视频详情").assertExists()

        // Reverse an unfinished transition, then verify that the final layout and player are intact.
        compose.onNodeWithContentDescription("竖屏").performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithContentDescription("退出竖屏").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(compactHeight, videoHeight(), 1f)
        assertSame(surface, playerView())
        compose.runOnIdle { assertEquals(1.5f, player.playbackParameters.speed, 0f) }
        compose.mainClock.autoAdvance = true
    }

    private fun videoHeight(): Float = compose.onNodeWithTag("player-touch-surface").fetchSemanticsNode().boundsInRoot.height

    private fun assertPortraitFullscreenFrame() {
        compose.onNodeWithText("视频详情").assertDoesNotExist()
        val video = compose.onNodeWithTag("player-touch-surface").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("旋转后首帧和中间帧都应保持全屏高度", root.bottom, video.bottom, 1f)
    }

    private fun playerView(): PlayerView {
        var result: PlayerView? = null
        compose.runOnIdle {
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            val activity = monitor.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            fun visit(view: android.view.View) {
                if (view is PlayerView) result = view
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
            }
            visit(activity.window.decorView)
        }
        return checkNotNull(result)
    }
}
