package io.github.bilibox.feature.player

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoBookmarkStore
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalVideoBookmarksTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var preferences: SharedPreferences
    private lateinit var store: LocalVideoBookmarkStore
    private lateinit var player: ExoPlayer
    private lateinit var video: LocalVideo
    private lateinit var state: LocalVideoBookmarkState
    private var messages = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("bookmark_ui_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = LocalVideoBookmarkStore(preferences)
        val fixture = createSeekPreviewFixture(context)
        video = LocalVideo(Uri.fromFile(fixture), "标记测试.mp4", 30_000L, fixture.length())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    private fun showPlayer(landscape: Boolean = false) {
        compose.setContent {
            val context = LocalContext.current
            player = remember {
                val renderers = DefaultRenderersFactory(context).setMediaCodecSelector { mime, secure, tunneling ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { !it.softwareOnly }
                }
                ExoPlayer.Builder(context, renderers).build().apply {
                    setMediaItem(MediaItem.fromUri(video.uri))
                    prepare()
                }
            }
            state = remember { LocalVideoBookmarkState(player, video, store) { messages.add(it) } }
            DisposableEffect(player) { onDispose { player.release() } }
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                VideoPlayerScreen(
                    player = player,
                    title = video.name,
                    defaultDurationMs = video.durationMs,
                    sourceLabel = "本地视频",
                    commentMessage = "",
                    detailTabs = listOf("简介", "标记"),
                    onBookmarksClick = { state.sheetOpen = true },
                    extraDetailPane = { LocalVideoBookmarks(state) },
                    onBack = {},
                    onRetry = {},
                    details = { Text("视频简介") },
                )
                LocalVideoBookmarkOverlays(state)
            }
        }
        compose.waitUntil(15_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
        compose.runOnIdle { state.refresh() }
    }

    private fun seekTo(position: Long) {
        compose.runOnIdle { player.seekTo(position) }
        compose.waitUntil(5_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
    }

    @Test
    fun localScreenReplacesMediaTabAndKeepsBookmarksSeparateWhenSwitchingFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secondFile = File(context.cacheDir, "bookmark-second-fixture.mp4")
        File(checkNotNull(video.uri.path)).copyTo(secondFile, overwrite = true)
        val second = video.copy(uri = Uri.fromFile(secondFile), name = "另一个视频.mp4")
        val saved = LocalVideoBookmarkStore(context)
        saved.save(video.key, emptyList())
        saved.save(second.key, emptyList())
        var current by mutableStateOf(video)
        compose.setContent {
            LocalVideoScreen(current, listOf(video, second), onVideoSelected = { current = it }, onBack = {})
        }
        compose.runOnIdle {
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            val activity = monitor.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            fun visit(view: android.view.View) {
                if (view is PlayerView) player = view.player as ExoPlayer
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(activity.window.decorView)
            // 此处验证本地页面接线；避免模拟器硬解码器影响时间标记测试。
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            player.prepare()
            player.pause()
        }
        compose.waitUntil(15_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
        compose.onNodeWithText("媒体信息").assertDoesNotExist()
        compose.onNodeWithText("标记").performClick()
        seekTo(6_000L)
        compose.onNodeWithText("添加标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:06").assertExists()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "local-bookmarks-preview.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { current = second }
        compose.waitForIdle()
        compose.onNodeWithText("标记").performClick()
        compose.onNodeWithText("还没有标记").assertExists()
        compose.runOnIdle { current = video }
        compose.waitForIdle()
        compose.onNodeWithText("标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:06").assertExists()
        saved.save(video.key, emptyList())
        saved.save(second.key, emptyList())
    }

    @Test
    fun marksCanBeAddedEditedJumpedAndDeletedWithoutChangingPlaybackIntent() {
        showPlayer()
        compose.onNodeWithText("媒体信息").assertDoesNotExist()
        compose.onNodeWithText("标记").performClick()
        compose.onNodeWithText("还没有标记").assertExists()
        seekTo(5_000L)
        compose.onNodeWithText("添加标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:05").assertExists()
        compose.onNodeWithText("添加标记").performClick()
        compose.runOnIdle {
            assertEquals(1, state.items.size)
            assertEquals("该时间点已有标记", messages.last())
        }
        compose.onNodeWithContentDescription("管理标记 00:05").performClick()
        compose.onNodeWithText("修改备注").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("重点讲解")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("重点讲解").assertExists()
        compose.runOnIdle { assertEquals("重点讲解", LocalVideoBookmarkStore(preferences).load(video.key).single().note) }
        seekTo(15_000L)
        compose.onNodeWithText("添加标记").performClick()
        compose.runOnIdle { assertEquals(listOf(5_000L, 15_000L), state.items.map { it.positionMs }) }
        compose.onNodeWithContentDescription("跳转到标记 00:05").performClick()
        compose.runOnIdle {
            assertEquals(5_000L, player.currentPosition)
            assertFalse(player.playWhenReady)
            player.play()
            state.jump(state.items.last())
            assertTrue(player.playWhenReady)
            player.pause()
        }
        compose.onNodeWithContentDescription("管理标记 00:05").performClick()
        compose.onNodeWithText("删除标记").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(2, state.items.size) }
        compose.onNodeWithContentDescription("管理标记 00:05").performClick()
        compose.onNodeWithText("删除标记").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("重点讲解").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, store.load(video.key).size) }
    }

    @Test
    fun fullscreenToolbarOpensListAndJumpClosesItWithoutLeavingFullscreen() {
        showPlayer(landscape = true)
        compose.onNodeWithContentDescription("打开标记列表").performClick()
        compose.onNodeWithText("还没有标记").assertExists()
        seekTo(8_000L)
        compose.onNodeWithText("添加标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:08").performClick()
        compose.onNodeWithText("视频标记").assertDoesNotExist()
        compose.onNodeWithContentDescription("切换竖屏全屏").assertExists()
        compose.runOnIdle { assertEquals(8_000L, player.currentPosition) }
    }

    @Test
    fun verticalFullscreenCanOpenBookmarksAndOldVideoCannotWriteToNextFile() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        compose.onNodeWithContentDescription("打开标记列表").performClick()
        seekTo(3_000L)
        compose.onNodeWithText("添加标记").performClick()
        compose.runOnIdle {
            player.setMediaItem(MediaItem.fromUri("file:///other-video.mp4"))
            state.add()
            state.jump(state.items.single())
            assertFalse(state.canAdd)
            assertEquals(1, store.load(video.key).size)
            assertTrue(store.load("other-video").isEmpty())
        }
    }
}
