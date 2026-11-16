package io.github.bilibox.feature.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryDetailDialogsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val video = LocalVideo(
        Uri.parse("content://media/external/video/media/999999"),
        "_祀_塔卫二时尚月刊ORITHE副刊_Archive_Vol.4 feat. _夕_望_颉 P1 _祀_塔卫二时尚月刊ORITHE副刊_Archive_Vol.4 合集.mp4",
        134_000L, 11_513_036L, relativePath = "Movies/BiliBox/收藏的视频/音乐现场与特别企划",
        width = 960, height = 720, frameRate = 30f, mimeType = "video/mp4", dateModifiedMs = 1_789_000_000_000L,
    )

    @Test fun darkInfoKeepsLongNameReadableAndCloseVisibleWhileScrolling() = verifyInfo(dark = true)

    @Test fun lightInfoSupportsLargeTextWithoutLosingDetailsOrActions() = verifyInfo(dark = false, fontScale = 1.4f)

    private fun verifyInfo(dark: Boolean, fontScale: Float = 1f) {
        var closed = false
        show(dark, fontScale) {
            VideoInfoDialog(video, LocalVideoPlayback(LocalVideoPlaybackState.Watched)) { closed = true }
        }
        compose.onNodeWithText(video.name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            assertFalse("Full filename should wrap without truncation", layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithText("关闭").assertIsDisplayed()
        saveScreenshot("info-${if (dark) "dark" else "light"}.png")
        compose.onNodeWithText(video.relativeDirectory).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("已播完").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun darkRenameWrapsLongNameAndSavesOnlyTheEditedTitle() = verifyRename(dark = true)

    @Test fun lightRenameKeepsActionsAccessibleWithLargeTextAndKeyboard() = verifyRename(dark = false, fontScale = 1.4f)

    private fun verifyRename(dark: Boolean, fontScale: Float = 1f) {
        var saved: String? = null
        var cancelled = false
        show(dark, fontScale) {
            RenameVideoDialog(video, false, null, { cancelled = true }, { saved = it })
        }
        compose.onNodeWithText("保存").assertIsNotEnabled().assertIsDisplayed()
        compose.onNodeWithText(".mp4").assertIsDisplayed()
        saveScreenshot("rename-${if (dark) "dark" else "light"}.png")
        compose.onNodeWithContentDescription("视频文件名").performClick().performTextReplacement("重新命名的音乐现场")
        compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("重新命名的音乐现场", saved) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    private fun show(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val accent = Color(0xFFFB7299)
            BiliBoxTheme(darkTheme = dark, accent = accent, dynamicColor = false) {
                val palette = libraryPalette(dark, accent)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalLibraryPalette provides palette, LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().background(palette.background)) { content() }
                }
            }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.cacheDir, "library-dialog-$name").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
