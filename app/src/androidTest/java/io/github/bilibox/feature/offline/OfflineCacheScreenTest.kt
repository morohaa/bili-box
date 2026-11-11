package io.github.bilibox.feature.offline

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.online.OfflineCacheEntry
import io.github.bilibox.data.online.OfflineCacheGroup
import io.github.bilibox.data.online.OfflineCacheLibrary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OfflineCacheScreenTest {
    @get:Rule val compose = createComposeRule()
    private val first = entry(1, "起步")
    private val second = entry(2, "进阶")
    private val single = entry(1, "短片", "BV-single")
    private val series = OfflineCacheGroup("BV-series", "Compose 入门课", "BiliBox", 2, listOf(first, second))
    private val library = OfflineCacheLibrary(listOf(series, OfflineCacheGroup("BV-single", "短片", "作者", 1, listOf(single))), 9_000_000_000)

    @Test
    fun multiPartDrillsDownAndPlaysOrderedPlaylistWithProgress() {
        var played: LocalVideo? = null
        var playlist = emptyList<LocalVideo>()
        show(onPlay = { video, queue ->
            played = video
            playlist = queue
        })
        compose.onNodeWithText("2 个分集").assertIsDisplayed()
        savePreview("offline-cache-root.png")
        compose.onNodeWithText("Compose 入门课").performClick()
        compose.onNodeWithText("P1  起步").assertIsDisplayed()
        compose.onNodeWithText("已看完").assertIsDisplayed()
        compose.onNodeWithText("00:20 / 01:40").assertIsDisplayed()
        savePreview("offline-cache-parts.png")
        compose.onNodeWithText("P2  进阶").performClick()
        compose.runOnIdle {
            assertEquals(second.media, played)
            assertEquals(listOf(first.media, second.media), playlist)
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("离线缓存").assertDoesNotExist()
        compose.onNodeWithText("已缓存视频").assertDoesNotExist()
        compose.onNodeWithText("管理").assertIsDisplayed()
    }

    @Test
    fun searchAndSingleVideoOpenOnlyMatchingQueue() {
        var played: LocalVideo? = null
        show(onPlay = { video, _ -> played = video })
        compose.onNodeWithContentDescription("搜索缓存").performClick()
        compose.onNodeWithText("搜索视频、分集或 UP 主").performTextInput("作者")
        compose.onNodeWithText("Compose 入门课").assertDoesNotExist()
        compose.onNodeWithText("短片").performClick()
        compose.runOnIdle { assertEquals(single.media, played) }
    }

    @Test
    fun manageDeletesSelectedGroupOnlyAfterConfirmationInDarkMode() {
        var deleted = emptyList<OfflineCacheEntry>()
        show(dark = true, onDelete = { deleted = it })
        savePreview("offline-cache-dark.png")
        compose.onNodeWithText("管理").performClick()
        compose.onNodeWithText("Compose 入门课").performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithText("删除缓存").performClick()
        compose.onNodeWithText("删除离线缓存？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<OfflineCacheEntry>(), deleted) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("删除缓存").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf(first, second), deleted) }
    }

    @Test
    fun overflowDeletesOneFileWithoutStartingPlayback() {
        var played: LocalVideo? = null
        var deleted = emptyList<OfflineCacheEntry>()
        show(onPlay = { video, _ -> played = video }, onDelete = { deleted = it })
        compose.onNodeWithContentDescription("更多：短片").performClick()
        compose.onNodeWithText("删除缓存").performClick()
        compose.onNodeWithText("删除离线缓存？").assertIsDisplayed()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(null, played)
            assertEquals(listOf(single), deleted)
        }
    }

    @Test
    fun emptyLibraryExplainsWhereToDownload() {
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                OfflineCacheContent(
                    library = OfflineCacheLibrary(),
                    playback = emptyMap(),
                    onDelete = {},
                    onBack = {},
                    onPlay = { _, _ -> },
                    onForward = { it() },
                    onBackward = { it() },
                )
            }
        }
        compose.onNodeWithText("暂无离线缓存").assertIsDisplayed()
        compose.onNodeWithText("在视频播放页下载后，就能在这里离线观看").assertIsDisplayed()
    }

    private fun show(
        dark: Boolean = false,
        onPlay: (LocalVideo, List<LocalVideo>) -> Unit = { _, _ -> },
        onDelete: (List<OfflineCacheEntry>) -> Unit = {},
    ) {
        compose.setContent {
            BiliBoxTheme(darkTheme = dark, dynamicColor = false) {
                OfflineCacheContent(
                    library = library,
                    playback = mapOf(
                        first.id to LocalVideoPlayback(LocalVideoPlaybackState.Watched),
                        second.id to LocalVideoPlayback(LocalVideoPlaybackState.Watching(20_000, .2f)),
                    ),
                    onDelete = onDelete,
                    onBack = {},
                    onPlay = onPlay,
                    onForward = { it() },
                    onBackward = { it() },
                )
            }
        }
    }

    private fun savePreview(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun entry(page: Int, title: String, bvid: String = "BV-series") = OfflineCacheEntry(
        bvid,
        page.toLong(),
        page,
        title,
        false,
        "720P 高清",
        LocalVideo(Uri.parse("content://offline-test/$bvid/$page"), "$title.mp4", 100_000, 3_100_000),
    )
}
