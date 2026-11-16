package io.github.bilibox.feature.library

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.feature.player.createSeekPreviewFixture
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryVideoActionsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample = LocalVideo(Uri.parse("content://media/external/video/media/999999"), "【P1】Java 入门与开发环境.mp4", 678_000, 27_262_976)
    private val createdUris = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        createdUris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
    }

    @Test
    fun lightMenuReplacesPlayAndDispatchesEachActionOnceWithoutPlayingVideo() {
        showMenu(dark = false)
        saveScreenshot("library-video-menu-light.png")
        exerciseMenu()
    }

    @Test
    fun darkMenuUsesTheSelectedAccentAndProvidesAllActions() {
        showMenu(dark = true, accent = Color(0xFF73B8D5))
        saveScreenshot("library-video-menu-dark.png")
        exerciseMenu()
    }

    private val calls = mutableListOf<String>()

    private fun showMenu(dark: Boolean, accent: Color = Color(0xFFFB7299), fontScale: Float = 1f, playbackState: LocalVideoPlaybackState = LocalVideoPlaybackState.Unplayed) {
        compose.setContent {
            BiliBoxTheme(darkTheme = dark, accent = accent, dynamicColor = false) {
                val palette = libraryPalette(dark, accent)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalLibraryPalette provides palette, LocalDensity provides Density(density.density, fontScale)) {
                    Column(Modifier.fillMaxSize().background(palette.background).padding(top = 100.dp, start = 20.dp, end = 20.dp)) {
                        VideoRow(
                            sample,
                            LocalVideoSettings(),
                            LocalVideoPlayback(playbackState),
                            onClick = { calls += "play" }, onShare = { calls += "share" }, onInfo = { calls += "info" },
                            onRename = { calls += "rename" }, onDelete = { calls += "delete" }, onToggleWatched = { calls += "watched" },
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithTag("library-video-menu").assertIsDisplayed()
    }

    private fun exerciseMenu() {
        compose.onNodeWithText("播放", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasAnyAncestor(hasTestTag("library-video-menu")) and hasText(sample.title), useUnmergedTree = true).assertCountEquals(0)
        val actions = listOf("重命名" to "rename", "分享" to "share", "文件信息" to "info", "设为已播完" to "watched", "删除" to "delete")
        actions.forEachIndexed { index, (label, _) ->
            if (index != 0) compose.onNodeWithContentDescription("更多操作").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithTag("library-video-menu").assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(actions.map { it.second }, calls) }
    }

    @Test
    fun watchedMenuOffersResetAndLargeTextCanScrollToDelete() {
        showMenu(dark = true, fontScale = 1.6f, playbackState = LocalVideoPlaybackState.Watched)
        compose.onNodeWithText("设为已播完").assertDoesNotExist()
        compose.onNodeWithText("设为未播放").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("删除").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("watched", "delete"), calls) }
    }

    @Test
    fun watchingVideoOffersMarkingCompletedInsteadOfReset() {
        showMenu(dark = false, playbackState = LocalVideoPlaybackState.Watching(52_000L, .5f))
        compose.onNodeWithText("设为未播放").assertDoesNotExist()
        compose.onNodeWithText("设为已播完").performClick()
        compose.runOnIdle { assertEquals(listOf("watched"), calls) }
    }

    @Test
    fun gridMenuDoesNotTriggerPlaybackAndDismissesOnBack() {
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                Box(Modifier.width(220.dp).padding(16.dp)) {
                    VideoCard(
                        sample,
                        LocalVideoSettings(),
                        LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                        onClick = { calls += "play" },
                        onShare = {},
                        onInfo = {},
                        onRename = { calls += "rename" },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.waitForIdle()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil { compose.onAllNodesWithTag("library-video-menu").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("重命名").performClick()
        compose.runOnIdle { assertEquals(listOf("rename"), calls) }
    }

    @Test
    fun renameDialogKeepsExtensionAndRejectsInvalidOrUnchangedNames() {
        var saved: String? = null
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                RenameVideoDialog(sample, false, null, {}, { saved = it })
            }
        }
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithContentDescription("视频文件名").performTextReplacement("../越界")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithContentDescription("视频文件名").performTextReplacement("【P1】新的标题")
        compose.onNodeWithText(".mp4").assertExists()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("【P1】新的标题", saved) }
    }

    @Test
    fun realFileActionsUpdateRowPersistWatchedStateAndOnlyDeleteAfterConfirmation() {
        val video = publish()
        lateinit var actions: LibraryVideoActionsState
        var current by mutableStateOf<LocalVideo?>(video)
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                val palette = libraryPalette(false, Color(0xFFFB7299))
                CompositionLocalProvider(LocalLibraryPalette provides palette) {
                    actions = rememberLibraryVideoActions(onFileChanged = { old, name -> current = name?.let { old.copy(name = it) } }, notify = {})
                    val progress = remember { PlaybackProgressStore(context) }
                    Box(Modifier.fillMaxSize().background(palette.background).padding(20.dp), contentAlignment = Alignment.TopCenter) {
                        current?.let { item ->
                            val playback = remember(item, actions.version) { progress.playback(item.key, item.durationMs) }
                            VideoRow(item, LocalVideoSettings(), playback, onClick = {}, onShare = {}, onInfo = {}, onRename = { actions.openRename(item) }, onDelete = { actions.openDelete(item) }, onToggleWatched = { actions.toggleWatched(item) })
                        }
                        LibraryVideoDialogs(actions)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("设为已播完").performClick()
        compose.onNodeWithText("已播完").assertExists()
        compose.runOnIdle { assertEquals(LocalVideoPlaybackState.Watched, PlaybackProgressStore(context).playback(video.key, video.durationMs).state) }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("设为未播放").performClick()
        compose.onNodeWithText("未播放").assertExists()
        compose.runOnIdle {
            assertEquals(LocalVideoPlayback(LocalVideoPlaybackState.Unplayed), PlaybackProgressStore(context).playback(video.key, video.durationMs))
        }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("设为已播完").performClick()
        compose.onNodeWithText("已播完").assertExists()
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("重命名").performClick()
        saveScreenshot("library-video-rename.png")
        compose.onNodeWithContentDescription("视频文件名").performTextReplacement("重命名后的课程")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { compose.runOnIdle { current?.name == "重命名后的课程.mp4" } }
        compose.onNodeWithText("重命名后的课程").assertIsDisplayed()
        compose.onNodeWithText("已播完").assertExists()
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("删除").performClick()
        saveScreenshot("library-video-delete.png")
        compose.onNodeWithText("取消").performClick()
        assertTrue(exists(video.uri))
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除视频").performClick()
        compose.waitUntil(5_000) { compose.runOnIdle { current == null && !actions.busy } }
        assertFalse(exists(video.uri))
    }

    private fun publish(): LocalVideo {
        val fixture = createSeekPreviewFixture(context)
        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "文件操作测试.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BiliBoxActionsTest-${System.nanoTime()}")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        )!!
        createdUris += uri
        context.contentResolver.openOutputStream(uri)!!.use { output -> fixture.inputStream().use { it.copyTo(output) } }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return LocalVideo(uri, "文件操作测试.mp4", 30_000, fixture.length(), relativePath = path(uri))
    }

    private fun path(uri: Uri): String = context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.RELATIVE_PATH), null, null, null)!!.use {
        check(it.moveToFirst())
        it.getString(0).trim('/')
    }

    private fun exists(uri: Uri): Boolean = context.contentResolver.query(uri, null, null, null, null)?.use { it.moveToFirst() } == true

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
