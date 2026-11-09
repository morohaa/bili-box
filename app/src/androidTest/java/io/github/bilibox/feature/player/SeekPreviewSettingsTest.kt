package io.github.bilibox.feature.player

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.data.player.SeekPreviewPreferences
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SeekPreviewSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = SeekPreviewPreferences(context)
    private var original = true

    @Before
    fun reset() {
        original = preferences.load()
        preferences.save(true)
    }

    @After
    fun restore() {
        preferences.save(original)
    }

    @Test
    fun defaultsOnAndReopenedStateRestoresBothChoices() {
        val storage = context.getSharedPreferences("seek_preview_settings_test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        try {
            val store = SeekPreviewPreferences(storage)
            val state = SeekPreviewPreferenceState(store)
            assertTrue(state.enabled)
            state.update(false)
            assertFalse(SeekPreviewPreferenceState(store).enabled)
            state.update(true)
            assertTrue(SeekPreviewPreferenceState(store).enabled)
        } finally {
            storage.edit().clear().commit()
        }
    }

    @Test
    fun moreSettingsTogglesPersistsAndReturnsToParentBeforeClosing() {
        val generation = mutableIntStateOf(0)
        var activity: ComponentActivity? = null
        compose.setContent {
            val localContext = androidx.compose.ui.platform.LocalContext.current
            activity = localContext as ComponentActivity
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            key(generation.intValue) {
                VideoPlayerScreen(
                    player = player,
                    title = "预览设置测试",
                    sourceLabel = "本地视频",
                    commentMessage = "测试",
                    onBack = {},
                    onRetry = {},
                    playbackModeSheet = { Text("播放顺序测试面板") },
                    details = { Text("视频详情") },
                )
            }
        }
        openMoreSettings()
        val toggle = compose.onNodeWithContentDescription("进度条画面预览")
        toggle.assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(preferences.load()) }
        compose.onNodeWithTag("more-playback-settings").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, "seek-preview-settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("返回播放设置").performClick()
        compose.onNodeWithText("自动连播").assertIsDisplayed()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        toggle.assertIsOff()
        compose.runOnIdle { activity!!.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("more-playback-settings").assertDoesNotExist()
        compose.onNodeWithText("自动连播").assertIsDisplayed()
        compose.runOnIdle { generation.intValue++ }
        openMoreSettings()
        toggle.assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(preferences.load()) }
        compose.onNodeWithText("播放顺序").performClick()
        compose.onNodeWithText("播放顺序测试面板").assertIsDisplayed()
    }

    private fun openMoreSettings() {
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
    }
}
