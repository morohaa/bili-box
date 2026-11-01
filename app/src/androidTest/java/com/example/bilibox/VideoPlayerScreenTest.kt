package com.example.bilibox

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bilibox.bili.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class VideoPlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun streamLoadingAndRetryKeepPlayerSurfaceAndSharedControlsMounted() {
        var loading by mutableStateOf<String?>("正在获取视频流…")
        var error by mutableStateOf<String?>(null)
        var retries = 0
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            VideoPlayerScreen(
                player = player,
                title = "搜索视频",
                loadingMessage = loading,
                playbackError = error,
                sourceLabel = "哔哩哔哩",
                commentMessage = "暂不支持查看在线评论",
                onBack = {},
                onRetry = { retries++; error = null; loading = "正在获取视频流…" },
                details = { Text("搜索视频简介") }
            )
        }
        compose.onNodeWithText("正在获取视频流…").assertExists()
        compose.onNodeWithContentDescription("播放进度").assertExists()
        compose.onNodeWithContentDescription("切换横屏").assertExists()
        compose.onNodeWithText("00:00 / --:--").assertExists()
        // Playback intent must still toggle while no stream is ready to render.
        compose.onNodeWithContentDescription("播放").performClick()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("播放").assertExists()
        val initialSurface = findPlayerView()
        compose.runOnIdle { error = "测试取流失败"; loading = null }
        compose.onNodeWithText("暂时无法播放").assertExists()
        compose.onAllNodesWithText("重试")[0].performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("正在获取视频流…").assertExists()
        assertSame(initialSurface, findPlayerView())
        compose.onNodeWithText("倍速").performClick()
        compose.onNodeWithText("1.5x").performClick()
        compose.onNodeWithText("评论", useUnmergedTree = true).performClick()
        compose.onNodeWithText("暂不支持查看在线评论").assertExists()
    }

    @Test
    fun fullscreenSpeedPanelAppliesPresetsAndCloses() {
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            val landscape = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                VideoPlayerScreen(
                    player = player,
                    title = "搜索视频",
                    sourceLabel = "哔哩哔哩",
                    commentMessage = "暂不支持查看在线评论",
                    onBack = {},
                    onRetry = {},
                    details = { Text("搜索视频简介") }
                )
            }
        }
        compose.onNodeWithText("倍速").performClick()
        compose.onNodeWithContentDescription("收起倍速面板").assertExists()
        compose.onNodeWithText("1.50").assertDoesNotExist()
        compose.onNodeWithText("1.5x").performClick()
        // The side drawer is a live control: it stays open so the runner can be
        // watched, and the readout reflects the freshly picked speed.
        compose.onNodeWithText("1.50").assertExists()
        compose.onNodeWithText("快跑中").assertExists()
        compose.onNodeWithContentDescription("收起倍速面板").performClick()
        compose.onNodeWithContentDescription("收起倍速面板").assertDoesNotExist()
    }

    @Test
    fun onlineFullscreenUsesLockAndScreenshotToolsWithoutLocalEpisodes() {
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            val landscape = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                VideoPlayerScreen(
                    player = player,
                    title = "搜索视频",
                    sourceLabel = "哔哩哔哩",
                    commentMessage = "暂不支持查看在线评论",
                    onBack = {},
                    onRetry = {},
                    details = { Text("搜索视频简介") }
                )
            }
        }
        compose.onNodeWithText("搜索视频").assertExists()
        compose.onNodeWithText("倍速").assertExists()
        compose.onNodeWithText("选集").assertDoesNotExist()
        compose.onNodeWithContentDescription("退出全屏").assertDoesNotExist()
        compose.onNodeWithContentDescription("下一集").assertExists()
        compose.onNodeWithContentDescription("画质 选择").assertDoesNotExist()
        compose.onNodeWithContentDescription("弹幕开关（开发中）").assertExists()
        compose.onNodeWithContentDescription("切换横屏").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放进度").assertExists()
        compose.onNodeWithText("00:00 / --:--").assertExists()
        compose.onNodeWithContentDescription("截取视频画面").assertExists()
        compose.onNodeWithContentDescription("锁定屏幕").performClick()
        compose.onNodeWithContentDescription("解锁屏幕").assertExists()
        compose.onNodeWithContentDescription("截取视频画面").assertDoesNotExist()
        compose.onNodeWithContentDescription("解锁屏幕").performClick()
        compose.onNodeWithContentDescription("截取视频画面").assertExists()
    }

    @Test
    fun fullscreenQualitySheetAndNextEpisodeButtonWork() {
        var pickedQuality = -1
        var nextEpisodes = 0
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            val landscape = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                VideoPlayerScreen(
                    player = player,
                    title = "搜索视频",
                    sourceLabel = "哔哩哔哩",
                    commentMessage = "暂不支持查看在线评论",
                    onBack = {},
                    onRetry = {},
                    onNextEpisode = { nextEpisodes++ },
                    qualityOptions = listOf(
                        PlayerQualityOption(80, "1080P", "高清 · 推荐"),
                        PlayerQualityOption(64, "720P", "高清 · 省流"),
                        PlayerQualityOption(32, "480P", "清晰"),
                    ),
                    selectedQuality = 80,
                    onQualitySelected = { pickedQuality = it },
                    details = { Text("搜索视频简介") }
                )
            }
        }

        compose.onNodeWithContentDescription("下一集").performClick()
        compose.runOnIdle { assertEquals(1, nextEpisodes) }
        compose.onNodeWithText("1080P").assertExists()
        compose.onNodeWithContentDescription("画质 1080P").performClick()
        compose.onNodeWithText("清晰度与音质").assertExists()
        compose.onNodeWithContentDescription("选择清晰度 720P").performClick()
        compose.runOnIdle { assertEquals(64, pickedQuality) }
        compose.waitForIdle()
        compose.onNodeWithText("清晰度与音质").assertDoesNotExist()
    }

    @Test
    fun onlineEpisodePickerSelectsTheRequestedPartAndDismisses() {
        val pages = listOf(
            Page(cid = 101, page = 1, part = "第一集", duration = 60),
            Page(cid = 202, page = 2, part = "第二集", duration = 90),
        )
        var selectedPage = 1
        var visible by mutableStateOf(true)
        compose.setContent {
            if (visible) {
                OnlineVideoEpisodes(
                    pages = pages,
                    selectedPage = selectedPage,
                    onDismiss = { visible = false },
                    onPageSelected = { selectedPage = it.page }
                )
            }
        }

        compose.onNodeWithText("第二集", substring = true).performClick()
        compose.runOnIdle {
            assertEquals(2, selectedPage)
            assertEquals(false, visible)
        }
    }

    @Test
    fun onlineEpisodesSheetSwitchesLayoutAndSelectsPart() {
        val pages = listOf(
            Page(cid = 101, page = 1, part = "第一集", duration = 60),
            Page(cid = 202, page = 2, part = "第二集", duration = 90),
            Page(cid = 303, page = 3, part = "第三集", duration = 120),
        )
        var selectedPage = 1
        var visible by mutableStateOf(true)
        var gridView by mutableStateOf(false)
        var autoPlayNext by mutableStateOf(true)
        compose.setContent {
            if (visible) {
                OnlineEpisodesBottomSheet(
                    pages = pages,
                    selectedPage = selectedPage,
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChange = { autoPlayNext = it },
                    gridView = gridView,
                    onGridViewChange = { gridView = it },
                    onDismiss = { visible = false },
                    onPageSelected = { selectedPage = it },
                )
            }
        }

        compose.onNodeWithText("视频选集").assertExists()
        compose.onNodeWithText("(1/3)").assertExists()
        compose.onNodeWithContentDescription("切换为宫格视图").performClick()
        compose.onNodeWithText("列表视图").assertExists()
        compose.onNodeWithContentDescription("切换为列表视图").performClick()
        compose.onNodeWithText("宫格视图").assertExists()
        compose.onNodeWithContentDescription("自动连播").performClick()
        compose.runOnIdle { assertEquals(false, autoPlayNext) }
        compose.onNodeWithText("第二集", substring = true).performClick()
        compose.runOnIdle {
            assertEquals(2, selectedPage)
            // Picking a part must keep the panel open so a mis-tap can be
            // corrected without reopening the picker.
            assertEquals(true, visible)
        }
        compose.onNodeWithContentDescription("关闭选集").performClick()
        compose.runOnIdle { assertEquals(false, visible) }
    }

    @Test
    fun onlineEpisodesSectionShowsFractionAndTogglesAutoPlay() {
        val pages = listOf(
            Page(cid = 501, page = 1, part = "第一集", duration = 60),
            Page(cid = 502, page = 2, part = "第二集", duration = 90),
            Page(cid = 503, page = 3, part = "第三集", duration = 120),
        )
        var selectedPage = 1
        var autoPlayNext by mutableStateOf(true)
        var opened = 0
        compose.setContent {
            Column(Modifier.fillMaxSize().padding(top = 80.dp)) {
                OnlineEpisodesSection(
                    pages = pages,
                    selectedPage = selectedPage,
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChange = { autoPlayNext = it },
                    onPageSelected = { selectedPage = it },
                    onOpenEpisodes = { opened++ },
                )
            }
        }

        compose.onNodeWithText("正片").assertExists()
        compose.onNodeWithText("(1/3)").assertExists()
        compose.onNodeWithText("共3P · 查看全部").assertExists()
        compose.onNodeWithText("第一集").assertExists()
        compose.onNodeWithContentDescription("自动连播").assertIsOn().assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("自动连播").assertIsOff()
        compose.runOnIdle { assertEquals(false, autoPlayNext) }
        compose.onNodeWithText("第二集").performClick()
        compose.runOnIdle { assertEquals(2, selectedPage) }
        compose.onNodeWithContentDescription("查看全部选集").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun episodeOverlaySlidesInOverDetailsAndLeavesOnDismiss() {
        var visible by mutableStateOf(false)
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            VideoPlayerScreen(
                player = player,
                title = "搜索视频",
                sourceLabel = "哔哩哔哩",
                commentMessage = "暂不支持查看在线评论",
                onBack = {},
                onRetry = {},
                episodeOverlay = {
                    Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                        Text("视频选集")
                    }
                },
                episodeOverlayVisible = visible,
                episodeOverlayDismiss = { visible = false },
                details = { Text("搜索视频简介") }
            )
        }

        compose.onNodeWithText("视频选集").assertDoesNotExist()
        compose.onNodeWithText("搜索视频简介").assertExists()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("视频选集").assertExists()
        // The details page stays behind the sliding panel so the exit
        // animation reveals content instead of a blank pane.
        compose.onNodeWithText("搜索视频简介").assertExists()
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("视频选集").assertDoesNotExist()
        compose.onNodeWithText("搜索视频简介").assertExists()
    }

    private fun findPlayerView(): PlayerView {
        var result: PlayerView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun visit(view: android.view.View) {
                if (view is PlayerView) result = view
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
            }
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            val activity = monitor.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            visit(activity.window.decorView)
        }
        return checkNotNull(result)
    }
}
