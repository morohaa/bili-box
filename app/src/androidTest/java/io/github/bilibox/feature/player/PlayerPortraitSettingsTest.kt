package io.github.bilibox.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.pressBack
import io.github.bilibox.core.playback.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerPortraitSettingsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer
    private var selectedQuality by mutableStateOf(80)
    private var playbackMode by mutableStateOf(PlaybackMode.SEQUENTIAL)

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
    fun speedPresetsAndMoreArrowWorkInsideFullscreen() {
        showPlayer(Modifier.width(400.dp))
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()

        openSettings()
        val label = compose.onNodeWithText("倍速").performScrollTo().assertHasNoClickAction()
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("更多倍速").assertIsDisplayed()
        val moreBounds = more.fetchSemanticsNode().boundsInRoot
        val firstSpeed = compose.onNodeWithContentDescription("切换倍速 0.5x").assertIsDisplayed()
        val firstMode = compose.onNodeWithText("播完停止").assertIsDisplayed()
        val firstModeBounds = firstMode.fetchSemanticsNode().boundsInRoot
        val thirdMode = compose.onNodeWithText("单集循环").assertIsDisplayed()
        assertEquals(moreBounds.right, thirdMode.fetchSemanticsNode().boundsInRoot.right, 1f)
        assertEquals(firstModeBounds.left, firstSpeed.fetchSemanticsNode().boundsInRoot.left, 1f)
        val normal = compose.onNodeWithContentDescription("切换倍速 1x").assertIsSelected()
        val fastest = compose.onNodeWithContentDescription("切换倍速 3x").assertIsNotDisplayed()
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                hasAnyDescendant(hasText("3x")),
        ).performTouchInput { swipeLeft() }
        fastest.assertIsDisplayed()
        assertEquals(moreBounds, more.fetchSemanticsNode().boundsInRoot)
        assertEquals(labelBounds, label.fetchSemanticsNode().boundsInRoot)
        assertEquals(firstModeBounds, firstMode.fetchSemanticsNode().boundsInRoot)

        for ((speed, text) in listOf(0.5f to "0.5x", 1.5f to "1.5x", 2f to "2x", 2.5f to "2.5x", 3f to "3x", 1f to "1x")) {
            val preset = compose.onNodeWithContentDescription("切换倍速 $text").performScrollTo().assertIsDisplayed()
            val bounds = preset.fetchSemanticsNode().boundsInRoot
            assertEquals(labelBounds.center.y, bounds.center.y, 1f)
            assertTrue(bounds.right <= moreBounds.left)
            preset.performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(speed, player.playbackParameters.speed, 0f) }
            compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed()
            compose.onNodeWithText("1.25x").assertDoesNotExist()
        }
        more.performTouchInput { click(center) }
        compose.onNodeWithText("1.25x").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1.25f, player.playbackParameters.speed, 0f) }
        compose.onNodeWithText("1.25x").assertDoesNotExist()
        assertFullscreen(expanded)

        openSettings()
        normal.assertIsNotSelected()
    }

    @Test
    fun timerOpensInsideFullscreen() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()

        openSettings()
        compose.onNodeWithText("定时关闭").performClick()
        compose.onNodeWithTag("player-sleep-timer-sheet").assertIsDisplayed()
        assertFullscreen(expanded)
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("player-sleep-timer-sheet").assertDoesNotExist()
        assertFullscreen(expanded)
    }

    @Test
    fun qualityOptionsGoFromLowToHighAndStayInSettingsInsideFullscreen() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()
        openSettings()

        val current = compose.onNodeWithContentDescription("选择清晰度 1080P")
        val target = compose.onNodeWithContentDescription("选择清晰度 720P")
        current.performScrollTo().assertIsDisplayed().assertIsSelected()
        target.assertIsDisplayed().assertIsNotSelected()
        val currentCenter = current.fetchSemanticsNode().boundsInRoot.center
        val targetCenter = target.fetchSemanticsNode().boundsInRoot.center
        assertTrue(targetCenter.x < currentCenter.x)
        assertEquals(currentCenter.y, targetCenter.y, 1f)
        target.performClick()
        compose.runOnIdle { assertEquals(64, selectedQuality) }
        current.assertIsNotSelected()
        target.assertIsSelected()
        compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("player-settings-sheet").assertDoesNotExist()
        assertFullscreen(expanded)
    }

    @Test
    fun playbackModesShowThreeChoicesAndScrollToTheFourthInPortraitFullscreen() {
        showPlayer(Modifier.width(400.dp))
        compose.onNodeWithContentDescription("竖屏").performClick()
        val expanded = surfaceBounds()
        openSettings()

        val label = compose.onNodeWithText("播放模式").performScrollTo().assertIsDisplayed()
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        for (mode in PlaybackMode.standard.take(3)) {
            val bounds = compose.onNodeWithText(mode.label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(labelBounds.center.y, bounds.center.y, 1f)
        }
        val last = compose.onNodeWithText("列表循环").assertIsNotDisplayed()

        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                hasAnyDescendant(hasText("列表循环")),
        ).performTouchInput { swipeLeft() }

        last.assertIsDisplayed()
        assertEquals(labelBounds, label.fetchSemanticsNode().boundsInRoot)
        assertEquals(labelBounds.center.y, last.fetchSemanticsNode().boundsInRoot.center.y, 1f)
        last.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(PlaybackMode.LIST_LOOP, playbackMode) }
        last.assertIsSelected()
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

    private fun showPlayer(modifier: Modifier = Modifier) {
        compose.setContent {
            val context = LocalContext.current
            player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            var cacheVisible by remember { mutableStateOf(false) }
            Box(modifier) {
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
                    playbackMode = playbackMode,
                    onPlaybackModeChange = { playbackMode = it },
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
