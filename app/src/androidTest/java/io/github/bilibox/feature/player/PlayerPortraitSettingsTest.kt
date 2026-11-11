package io.github.bilibox.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerPortraitSettingsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer
    private var selectedQuality by mutableStateOf(80)

    @Test
    fun moreAndDismissMoveVideoAboveSettingsAndRestoreFullscreenPosition() {
        showPlayer()
        val compact = surfaceBounds()
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()
        assertTrue(expanded.height > compact.height)
        val video = compose.onNodeWithTag("player-video-view").fetchSemanticsNode()
        val originalVideoBounds = video.boundsInRoot
        compose.runOnIdle { player.setPlaybackSpeed(1.25f) }

        openSettings()
        assertFullscreen(expanded)
        val sheet = compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(compact.bottom, sheet.top, 1f)
        assertEquals(root.bottom, sheet.bottom, 1f)
        assertEquals(video.id, compose.onNodeWithTag("player-video-view").fetchSemanticsNode().id)
        val liftedVideo = compose.onNodeWithTag("player-video-view").fetchSemanticsNode().boundsInRoot
        val topBar = compose.onNodeWithTag("player-top-bar").fetchSemanticsNode().boundsInRoot
        assertEquals(topBar.bottom, liftedVideo.top, 1f)
        assertEquals(sheet.top, liftedVideo.bottom, 1f)
        assertTrue("画面应上移到菜单上方", liftedVideo.top > originalVideoBounds.top && liftedVideo.bottom < originalVideoBounds.bottom)
        compose.runOnIdle { assertEquals(1.25f, player.playbackParameters.speed, 0f) }

        pressBack()
        compose.onNodeWithTag("player-settings-sheet").assertDoesNotExist()
        assertFullscreen(expanded)
        assertEquals(originalVideoBounds, compose.onNodeWithTag("player-video-view").fetchSemanticsNode().boundsInRoot)
        openSettings()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        compose.onNodeWithTag("more-playback-settings").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("more-playback-settings").assertDoesNotExist()
        compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed()
        assertFullscreen(expanded)

        // Tap the exposed scrim above the card to dismiss it.
        compose.onRoot().performTouchInput { click(Offset(center.x, compact.center.y)) }
        compose.onNodeWithTag("player-settings-sheet").assertDoesNotExist()
        assertFullscreen(expanded)
        assertEquals(originalVideoBounds, compose.onNodeWithTag("player-video-view").fetchSemanticsNode().boundsInRoot)
        pressBack()
        compose.onNodeWithText("视频详情").assertExists()
    }

    @Test
    fun speedQualityAndTimerOpenInsideFullscreen() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()

        openSettings()
        compose.onNodeWithText("倍速").performClick()
        compose.onNodeWithText("1.5x").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1.5f, player.playbackParameters.speed, 0f) }
        compose.onNodeWithContentDescription("关闭倍速面板").assertDoesNotExist()
        assertFullscreen(expanded)

        openSettings()
        compose.onNodeWithText("画质").performClick()
        compose.onNodeWithContentDescription("选择清晰度 720P").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(64, selectedQuality) }
        assertFullscreen(expanded)

        openSettings()
        compose.onNodeWithText("定时关闭").performClick()
        compose.onNodeWithTag("player-sleep-timer-sheet").assertIsDisplayed()
        assertFullscreen(expanded)
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("player-sleep-timer-sheet").assertDoesNotExist()
        assertFullscreen(expanded)
    }

    @Test
    fun cacheAndPlaybackOrderRemainReachableInsideFullscreen() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()

        openSettings()
        compose.onNodeWithText("缓存").performClick()
        compose.onNodeWithTag("test-cache-panel").assertIsDisplayed()
        assertFullscreen(expanded)
        pressBack()
        compose.onNodeWithTag("test-cache-panel").assertDoesNotExist()
        assertFullscreen(expanded)

        openSettings()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        compose.onNodeWithText("播放顺序").performClick()
        compose.onNodeWithText("队列设置").assertIsDisplayed()
        assertFullscreen(expanded)
        pressBack()
        compose.onNodeWithText("队列设置").assertDoesNotExist()
        assertFullscreen(expanded)
    }

    private fun showPlayer() {
        compose.setContent {
            val context = LocalContext.current
            player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            var cacheVisible by remember { mutableStateOf(false) }
            VideoPlayerScreen(
                player = player,
                title = "竖屏设置测试",
                sourceLabel = "哔哩哔哩",
                commentMessage = "测试",
                onBack = {},
                onRetry = {},
                qualityOptions = listOf(PlayerQualityOption(80, "1080P"), PlayerQualityOption(64, "720P")),
                selectedQuality = selectedQuality,
                onQualitySelected = { selectedQuality = it },
                playbackModeSheet = { Text("队列设置") },
                onCache = { cacheVisible = true },
                cacheSheetVisible = cacheVisible,
                onCacheSheetVisibleChange = { cacheVisible = it },
                cacheSheet = { visible, topInset, _ ->
                    if (visible) {
                        Box(Modifier.fillMaxSize().padding(top = topInset).testTag("test-cache-panel")) {
                            Text("缓存面板")
                        }
                    }
                },
                details = { Text("视频详情") },
            )
        }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed()
    }

    private fun surfaceBounds(): Rect = compose.onNodeWithTag("player-touch-surface").fetchSemanticsNode().boundsInRoot

    private fun assertFullscreen(expected: Rect) {
        compose.onNodeWithText("视频详情").assertDoesNotExist()
        compose.onNodeWithContentDescription("退出竖屏").assertExists()
        assertEquals(expected, surfaceBounds())
    }
}
