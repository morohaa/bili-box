package io.github.bilibox.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.data.player.VideoPositionPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VideoPositionSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = VideoPositionPreferences(context)
    private var original = 0

    @Before
    fun reset() {
        original = preferences.load()
        preferences.save(0)
    }

    @After
    fun restore() {
        preferences.save(original)
    }

    @Test
    fun adjustmentPersistsClampsAndResets() {
        val storage = context.getSharedPreferences("video_position_test", android.content.Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        try {
            val store = VideoPositionPreferences(storage)
            val state = PlayerVideoPositionState(store)
            assertEquals(0, state.adjustmentDp)
            state.update(64f)
            state.save()
            assertEquals(64, PlayerVideoPositionState(store).adjustmentDp)
            state.update(-999f)
            state.save()
            assertEquals(-160, PlayerVideoPositionState(store).adjustmentDp)
            state.reset()
            assertEquals(0, PlayerVideoPositionState(store).adjustmentDp)
        } finally {
            storage.edit().clear().commit()
        }
    }

    @Test
    fun moreSettingsOpensFullscreenLiveAdjustmentAndRestoresDefault() {
        compose.setContent {
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            VideoPlayerScreen(
                player = player,
                title = "画面位置测试",
                sourceLabel = "在线视频",
                commentMessage = "测试",
                onBack = {},
                onRetry = {},
                details = { Text("视频详情") },
            )
        }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        compose.onNodeWithText("竖屏全屏画面位置").performClick()
        compose.onNodeWithTag("video-position-adjustment").assertIsDisplayed()
        compose.onNodeWithText("视频详情").assertDoesNotExist()
        val before = compose.onNodeWithTag("player-video-view").getUnclippedBoundsInRoot().top.value
        compose.onNodeWithTag("video-position-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        val after = compose.onNodeWithTag("player-video-view").getUnclippedBoundsInRoot().top.value
        assertEquals(80f, after - before, 1f)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.cacheDir, "video-position-adjustment.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(80, preferences.load()) }
        compose.onNodeWithTag("video-position-adjustment").assertDoesNotExist()
        // 从竖屏全屏再次进入更多设置，设置值仍保留；重置也立即移动画面。
        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("更多播放设置").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        compose.onNodeWithText("偏下").assertIsDisplayed()
        compose.onNodeWithText("竖屏全屏画面位置").performClick()
        compose.onNodeWithText("恢复默认").performClick()
        val restored = compose.onNodeWithTag("player-video-view").getUnclippedBoundsInRoot().top.value
        assertEquals(before, restored, 1f)
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(0, preferences.load()) }
    }
}
