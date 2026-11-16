package io.github.bilibox.feature.player

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoBookmarkStore
import io.github.bilibox.data.player.BookmarkDisplayPreferences
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalVideoBookmarksTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var preferences: SharedPreferences
    private lateinit var store: LocalVideoBookmarkStore
    private lateinit var player: ExoPlayer
    private lateinit var video: LocalVideo
    private lateinit var state: LocalVideoBookmarkState
    private var messages = mutableListOf<String>()
    private lateinit var displayPreferences: BookmarkDisplayPreferences
    private var originalDotsEnabled = true

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("bookmark_ui_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = LocalVideoBookmarkStore(preferences)
        displayPreferences = BookmarkDisplayPreferences(context)
        originalDotsEnabled = displayPreferences.load()
        displayPreferences.save(true)
        val fixture = createSeekPreviewFixture(context)
        video = LocalVideo(Uri.fromFile(fixture), "标记测试.mp4", 30_000L, fixture.length())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        displayPreferences.save(originalDotsEnabled)
    }

    private fun showPlayer(landscape: Boolean = false, darkTheme: Boolean = false) {
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == orientation }
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
                this.orientation = orientation
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                BiliBoxTheme(darkTheme = darkTheme, dynamicColor = false) {
                    VideoPlayerScreen(
                        player = player,
                        title = video.name,
                        defaultDurationMs = video.durationMs,
                        sourceLabel = "本地视频",
                        commentMessage = "",
                        detailTabs = listOf("简介", "标记"),
                        onBookmarksClick = { state.sheetOpen = true },
                        onAddBookmark = state::add,
                        bookmarkPositions = state.items.map { it.positionMs },
                        episodes = { Text("测试选集") },
                        extraDetailPane = { LocalVideoBookmarks(state) },
                        onBack = {},
                        onRetry = {},
                        details = { Text("视频简介") },
                    )
                    LocalVideoBookmarkOverlays(state)
                }
            }
        }
        compose.waitUntil(15_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
        compose.runOnIdle { state.refresh() }
    }

    private fun savePopupScreenshot(name: String) {
        compose.waitForIdle()
        // 等待平台窗口的进场动画结束，再截取包含菜单或 Dialog 的屏幕。
        android.os.SystemClock.sleep(900)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val pending = File(instrumentation.targetContext.cacheDir, "$name.pending")
        pending.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        check(pending.renameTo(File(instrumentation.targetContext.cacheDir, name)))
        bitmap.recycle()
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
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:06").assertExists()
        compose.onNodeWithContentDescription("管理标记 00:06").performClick()
        savePopupScreenshot("bookmark-menu-light.png")
        compose.onNodeWithText("修改备注").performClick()
        savePopupScreenshot("bookmark-edit-light.png")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("管理标记 00:06").performClick()
        compose.onNodeWithText("删除标记").performClick()
        savePopupScreenshot("bookmark-delete-light.png")
        compose.onNodeWithText("取消").performClick()
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
        showPlayer(darkTheme = true)
        compose.onNodeWithText("媒体信息").assertDoesNotExist()
        compose.onNodeWithText("标记").performClick()
        compose.onNodeWithText("还没有标记").assertExists()
        compose.onNodeWithText("视频标记").assertDoesNotExist()
        compose.onNodeWithText("添加标记").assertDoesNotExist()
        compose.onNodeWithText("点击跳转 · 按时间排序").assertDoesNotExist()
        seekTo(5_000L)
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.runOnIdle { assertFalse(state.sheetOpen) }
        compose.onNodeWithContentDescription("跳转到标记 00:05").assertExists()
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.runOnIdle {
            assertEquals(1, state.items.size)
            assertEquals("该时间点已有标记", messages.last())
        }
        compose.onNodeWithContentDescription("管理标记 00:05").performClick()
        savePopupScreenshot("bookmark-menu-dark.png")
        compose.onNodeWithText("修改备注").performClick()
        savePopupScreenshot("bookmark-edit-dark.png")
        compose.onNode(hasSetTextAction()).performTextInput("重点讲解")
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.onNodeWithText("重点讲解").assertExists()
        compose.runOnIdle { assertEquals("重点讲解", LocalVideoBookmarkStore(preferences).load(video.key).single().note) }
        seekTo(15_000L)
        compose.onNodeWithContentDescription("添加标记").performClick()
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
        savePopupScreenshot("bookmark-delete-dark.png")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(2, state.items.size) }
        compose.onNodeWithContentDescription("管理标记 00:05").performClick()
        compose.onNodeWithText("删除标记").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("重点讲解").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, store.load(video.key).size) }
    }

    @Test
    fun landscapeToolbarAddsDirectlyAndBottomEntryManagesMarksWithoutLeavingFullscreen() {
        showPlayer(landscape = true)
        seekTo(8_000L)
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.onNodeWithContentDescription("收起标记面板").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(state.sheetOpen)
            assertEquals(8_000L, store.load(video.key).single().positionMs)
            assertEquals("已添加标记 00:08", messages.last())
            assertFalse(player.playWhenReady)
        }
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.runOnIdle { assertEquals(1, state.items.size) }
        val entry = compose.onNodeWithContentDescription("标记").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val episodes = compose.onNodeWithContentDescription("选集").fetchSemanticsNode().boundsInRoot
        val speed = compose.onNodeWithText("倍速").fetchSemanticsNode().boundsInRoot
        assertTrue(entry.right <= episodes.left && entry.right <= speed.left)
        compose.onNodeWithContentDescription("标记").performClick()
        compose.onNodeWithContentDescription("收起标记面板").assertIsDisplayed()
        compose.onNodeWithContentDescription("管理标记 00:08").performClick()
        compose.onNodeWithText("修改备注").performClick()
        savePopupScreenshot("bookmark-edit-landscape.png")
        compose.onNode(hasSetTextAction()).performClick().performTextInput("横屏重点")
        savePopupScreenshot("bookmark-edit-keyboard.png")
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.onNodeWithText("横屏重点").assertIsDisplayed()
        seekTo(20_000L)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("00:20 / 00:30").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.cacheDir, "landscape-bookmark-dots.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithContentDescription("跳转到标记 00:08").performClick()
        compose.onNodeWithContentDescription("收起标记面板").assertDoesNotExist()
        compose.onNodeWithContentDescription("切换竖屏全屏").assertExists()
        compose.runOnIdle {
            assertEquals(8_000L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
        compose.onNodeWithContentDescription("标记").performClick()
        compose.onNodeWithContentDescription("管理标记 00:08").performClick()
        compose.onNodeWithText("删除标记").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("还没有标记").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("收起标记面板").assertDoesNotExist()
        compose.onNodeWithContentDescription("切换竖屏全屏").assertExists()
        compose.runOnIdle { assertTrue(store.load(video.key).isEmpty()) }
        compose.onNodeWithContentDescription("标记").performClick()
        compose.onNodeWithContentDescription("收起标记面板").performClick()
        compose.onNodeWithContentDescription("收起标记面板").assertDoesNotExist()
        compose.onNodeWithContentDescription("标记").performClick()
        compose.onNodeWithContentDescription("关闭标记面板").performClick()
        compose.onNodeWithContentDescription("收起标记面板").assertDoesNotExist()
        compose.runOnIdle { player.play() }
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.runOnIdle {
            assertTrue(player.playWhenReady)
            assertEquals(1, store.load(video.key).size)
            player.pause()
        }
    }

    @Test
    fun moreSettingsToggleHidesDotsWithoutRemovingBookmarksAndPersistsChoice() {
        showPlayer()
        seekTo(8_000L)
        compose.onNodeWithContentDescription("添加标记").performClick()
        seekTo(20_000L)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("00:20 / 00:30").fetchSemanticsNodes().isNotEmpty() }
        fun assertDot(visible: Boolean) {
            val pixels = compose.onNodeWithTag("player-seek-track", useUnmergedTree = true).captureToImage().toPixelMap()
            val color = pixels[(pixels.width * 8f / 30).toInt(), pixels.height / 2]
            assertEquals("标记白点显隐", visible, color.red > .9f && color.green > .9f && color.blue > .9f)
        }
        fun openMore() {
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        }
        assertDot(true)
        openMore()
        compose.onNodeWithContentDescription("显示标记点").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle {
            assertFalse(displayPreferences.load())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertFalse(BookmarkDisplayPreferenceState(BookmarkDisplayPreferences(context)).enabled)
            assertEquals(1, store.load(video.key).size)
        }
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        assertDot(false)
        openMore()
        compose.onNodeWithContentDescription("显示标记点").performScrollTo().assertIsOff().performClick().assertIsOn()
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        assertDot(true)
        compose.onNodeWithText("标记").performClick()
        compose.onNodeWithContentDescription("跳转到标记 00:08").assertExists()
    }

    @Test
    fun verticalFullscreenAddsDirectlyAndOldVideoCannotWriteToNextFile() {
        showPlayer()
        compose.onNodeWithContentDescription("竖屏").performClick()
        seekTo(3_000L)
        compose.onNodeWithContentDescription("添加标记").performClick()
        compose.onNodeWithText("还没有标记").assertDoesNotExist()
        compose.onNodeWithContentDescription("退出竖屏").assertExists()
        compose.runOnIdle {
            assertFalse(state.sheetOpen)
            assertEquals(3_000L, state.items.single().positionMs)
            assertFalse(player.playWhenReady)
            player.setMediaItem(MediaItem.fromUri("file:///other-video.mp4"))
            state.add()
            state.jump(state.items.single())
            assertFalse(state.canAdd)
            assertEquals(1, store.load(video.key).size)
            assertTrue(store.load("other-video").isEmpty())
        }
    }
}
