package io.github.bilibox.feature.library

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoDisplayFields
import io.github.bilibox.data.localvideo.LocalVideoField
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoSettings
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LibraryVideoFieldsTest {
    @get:Rule val compose = createComposeRule()

    private val video = LocalVideo(
        uri = Uri.parse("content://io.github.bilibox.test/video/fields"),
        name = "海边的日落.mp4",
        durationMs = 134_000L,
        sizeBytes = 8_388_608L,
        relativePath = "Movies/旅行",
        width = 1920,
        height = 1080,
        frameRate = 60f,
    )
    private val playback = LocalVideoPlayback(LocalVideoPlaybackState.Watching(52_000L, 52f / 134f))
    private val noFields = LocalVideoField.entries.fold(LocalVideoDisplayFields()) { fields, field ->
        fields.with(field, false)
    }
    private val path = "内部存储 / Movies/旅行 / 海边的日落.mp4"

    @Test
    fun dialogFieldsImmediatelyUpdatePreview() {
        compose.setContent {
            TestTheme {
                var draft by remember { mutableStateOf(LocalVideoSettings(fields = noFields)) }
                LocalVideoSettingsDialog(
                    applied = LocalVideoSettings(fields = noFields),
                    draft = draft,
                    sampleVideo = video,
                    samplePlayback = playback,
                    sampleFolder = null,
                    onDraftChange = { draft = it },
                    onReset = {},
                    onDismiss = {},
                    onApply = {},
                    onRescan = {},
                )
            }
        }
        compose.onNodeWithText("视频字段显示").performClick()
        compose.onNodeWithText("播放中").assertDoesNotExist()
        compose.onNodeWithText("播放状态").performScrollTo().performClick()
        compose.onNodeWithText("播放中").assertExists()
        compose.onNodeWithText("播放状态").performClick()
        compose.onNodeWithText("播放中").assertDoesNotExist()

        compose.onNodeWithText("路径").performScrollTo().performClick()
        compose.onNodeWithText(path, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("帧率").performScrollTo().performClick()
        compose.onNodeWithText("60 fps", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("路径").performScrollTo().performClick()
        compose.onNodeWithText(path).assertDoesNotExist()
        compose.onNodeWithText("帧率").performScrollTo().performClick()
        compose.onNodeWithText("60 fps").assertDoesNotExist()
    }

    @Test
    fun unavailableFrameRateRemainsVisibleAndCanBeDisabled() {
        compose.setContent {
            TestTheme {
                var fields by remember { mutableStateOf(noFields.copy(frameRate = true)) }
                Column {
                    CompactVideoRow(video.copy(frameRate = 0f), LocalVideoSettings(fields = fields), playback)
                    androidx.compose.material3.TextButton(onClick = { fields = noFields }) {
                        androidx.compose.material3.Text("关闭帧率")
                    }
                }
            }
        }
        compose.onNodeWithText("帧率未知").assertIsDisplayed()
        compose.onNodeWithText("关闭帧率").performClick()
        compose.onNodeWithText("帧率未知").assertDoesNotExist()
    }

    @Test
    fun selectedMetadataWrapsWithoutTruncationInNarrowPreview() {
        compose.setContent {
            TestTheme {
                val fields = LocalVideoField.entries.fold(noFields) { current, field -> current.with(field, true) }
                CompactVideoRow(video, LocalVideoSettings(fields = fields), playback, Modifier.width(280.dp))
            }
        }
        compose.onNodeWithText(path).assertIsDisplayed()
        compose.onNodeWithText("播放中").assertIsDisplayed()
        compose.onNodeWithText("60 fps", substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(results)
            assertFalse("Selected metadata must not be ellipsized", results.single().hasVisualOverflow)
        }
    }

    @Test
    fun gridAndListRenderPathWithoutPlaybackStatus() {
        compose.setContent {
            TestTheme {
                val settings = LocalVideoSettings(fields = noFields.copy(path = true))
                Column {
                    VideoRow(video, settings, playback, onClick = {}, onShare = {}, onInfo = {})
                    VideoCard(video.copy(name = "清晨.mp4"), settings, playback, onClick = {}, onShare = {}, onInfo = {})
                }
            }
        }
        compose.onNodeWithText(path).assertIsDisplayed()
        compose.onNodeWithText("内部存储 / Movies/旅行 / 清晨.mp4").assertIsDisplayed()
        compose.onNodeWithText("播放中").assertDoesNotExist()
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    val accent = Color(0xFFFB7299)
    BiliBoxTheme(darkTheme = false, accent = accent, dynamicColor = false) {
        CompositionLocalProvider(LocalLibraryPalette provides libraryPalette(false, accent), content = content)
    }
}
