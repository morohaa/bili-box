package io.github.bilibox.feature.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.pressBack
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.feature.listen.ListenInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerLandscapeControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    private var density = 1f

    private fun showPlayer(online: Boolean) {
        val orientation = if (online) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (online) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == orientation }
        compose.setContent {
            val context = LocalContext.current
            density = LocalDensity.current.density
            player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            var cacheVisible by remember { mutableStateOf(false) }
            // 测试 Activity 不处理方向重建；本地听视频用竖屏窗口模拟横屏配置。
            val landscape = Configuration(LocalConfiguration.current).apply { this.orientation = Configuration.ORIENTATION_LANDSCAPE }
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                BiliBoxTheme(dynamicColor = false) {
                    VideoPlayerScreen(
                        player = player,
                        title = if (online) "在线视频测试" else "本地视频测试",
                        defaultDurationMs = 60_000,
                        sourceLabel = if (online) "哔哩哔哩" else "本地视频",
                        commentMessage = "测试",
                        onBack = {},
                        onRetry = {},
                        listenInfo = ListenInfo("测试视频", online),
                        onBookmarksClick = if (online) null else ({}),
                        playbackModeSheet = if (online) null else ({ dismiss -> TextButtonForTest(dismiss) }),
                        onCache = { cacheVisible = true },
                        cacheSheetVisible = cacheVisible,
                        onCacheSheetVisibleChange = { cacheVisible = it },
                        cacheSheet = { visible, _, _ ->
                            if (visible) Box(Modifier.fillMaxSize().testTag("test-landscape-cache")) { Text("缓存面板") }
                        },
                        details = { Text("视频详情") },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun onlineLandscapeShowsRightHandActionsAndAlignsBackGlyphWithSeekTrack() {
        showPlayer(online = true)
        val more = compose.onNodeWithContentDescription("更多").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val listen = compose.onNodeWithContentDescription("听视频").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("听音频应位于更多左侧", listen.right <= more.left)
        val glyph = compose.onNodeWithTag("player-back-glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithTag("player-seek-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrowLeft = glyph.left + glyph.width * .28f - density
        val trackLeft = track.left - 1.25f * density
        val rootLeft = compose.onRoot().fetchSemanticsNode().boundsInRoot.left
        assertEquals("进度轨道应保持原来的左侧位置", rootLeft + 25.75f * density, trackLeft, density)
        assertEquals("返回箭头笔画应与进度轨道左缘对齐", trackLeft, arrowLeft, density)
        saveScreenshot("landscape-player-controls.png")

        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithTag("player-settings-sheet").assertIsDisplayed()
        saveScreenshot("landscape-player-settings.png")
        // 从设置进入既有横屏倍速面板，验证新增入口确实能操作。
        compose.onNodeWithContentDescription("倍速 1x").performClick()
        compose.onNodeWithContentDescription("收起倍速面板").assertExists()
        compose.onNodeWithContentDescription("收起倍速面板").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("定时关闭").performClick()
        compose.onNodeWithText("30 分钟").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("缓存").performClick()
        compose.onNodeWithTag("test-landscape-cache").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("test-landscape-cache").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        pressBack()
        compose.onNodeWithTag("player-settings-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("锁定屏幕").performClick()
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()
        compose.onNodeWithContentDescription("听视频").assertDoesNotExist()
    }

    @Test
    fun localLandscapeListenButtonOpensAudioModeAndMoreCanOpenPlaybackOrder() {
        showPlayer(online = false)
        compose.onNodeWithContentDescription("打开标记列表").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("更多播放设置").performScrollTo().performClick()
        compose.onNodeWithText("播放顺序").performClick()
        compose.onNodeWithText("测试播放顺序").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("听视频").performClick()
        compose.onNodeWithText("听视频 · 画面已停止解码").assertExists()
        compose.runOnIdle { assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) }
    }

    @Composable
    private fun TextButtonForTest(onClick: () -> Unit) {
        androidx.compose.material3.TextButton(onClick = onClick) { Text("测试播放顺序") }
    }

    private fun saveScreenshot(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
