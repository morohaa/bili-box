package com.example.bilibox

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
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
        compose.onNodeWithContentDescription("更多播放设置").performClick()
        compose.onNodeWithText("倍速 1.5x").performClick()
        compose.onNodeWithText("评论", useUnmergedTree = true).performClick()
        compose.onNodeWithText("暂不支持查看在线评论").assertExists()
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
