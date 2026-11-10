package io.github.bilibox.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
