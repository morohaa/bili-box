package io.github.bilibox.feature.player

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.playback.LocalQueueMode
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.feature.online.OnlineVideoDetails
import io.github.bilibox.feature.player.details.LocalVideoIntro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun localPortraitDetailsFollowAppThemeAcrossLightDarkLight() = verifyThemeSwitch(online = false)

    @Test
    fun onlinePortraitDetailsFollowAppThemeAcrossLightDarkLight() = verifyThemeSwitch(online = true)

    private fun verifyThemeSwitch(online: Boolean) {
        var dark by mutableStateOf(false)
        var mountedPlayers = 0
        val title = if (online) "在线主题测试" else "本地主题测试"
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build().also { mountedPlayers++ } }
            DisposableEffect(player) { onDispose { player.release() } }
            // Deliberately oppose the app preference: appearance must follow BiliBoxTheme.
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                BiliBoxTheme(darkTheme = dark, dynamicColor = false) {
                    VideoPlayerScreen(
                        player = player,
                        title = title,
                        sourceLabel = if (online) "哔哩哔哩" else "本地视频",
                        commentMessage = "暂无评论",
                        sourcePill = !online,
                        onBack = {},
                        onRetry = {},
                        details = { duration ->
                            if (online) {
                                OnlineVideoDetails(
                                    video = VideoSummary(title, "测试作者", "1万", 0, 0, "01:00", "BVtest", ""),
                                    info = null,
                                    creatorCard = null,
                                    context = context,
                                    selectedPage = 1,
                                    autoPlayNext = true,
                                    onAutoPlayNextChange = {},
                                    onPageSelected = {},
                                    onOpenEpisodes = {},
                                )
                            } else {
                                LocalVideoIntro(
                                    video = LocalVideo(Uri.EMPTY, title, 60_000, 1024),
                                    playlist = emptyList(),
                                    durationMs = duration,
                                    resumeMs = 0,
                                    queueMode = LocalQueueMode.LIST_LOOP,
                                    playbackByKey = emptyMap(),
                                    onShare = {},
                                    onSelectVideo = {},
                                    onResumeDismiss = {},
                                )
                            }
                        },
                    )
                }
            }
        }
        listOf(false, true, false).forEach { darkMode ->
            compose.runOnIdle { dark = darkMode }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("playerDetails").captureToImage().toPixelMap()
            val background = pixels[2, pixels.height - 2].luminance()
            assertTrue("Details background follows app dark=$darkMode: $background", if (darkMode) background < .1f else background > .9f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val foreground = layouts.single().layoutInput.style.color.luminance()
            assertTrue("Title stays readable for dark=$darkMode: $foreground", if (darkMode) foreground > .7f else foreground < .1f)
            compose.runOnIdle { assertEquals("Theme changes keep the player mounted", 1, mountedPlayers) }
        }
    }
}
