package io.github.bilibox.feature.player

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.data.player.SeekPreviewPreferences
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerSeekPreviewTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var mainPlayer: ExoPlayer
    private val preferences = SeekPreviewPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
    private var originalPreference = true

    @Before
    fun resetPreference() {
        originalPreference = preferences.load()
        preferences.save(true)
    }

    @After
    fun restorePreference() {
        preferences.save(originalPreference)
    }

    @Test
    fun portraitPreviewDecodesTargetWhileMainPlaybackContinues() = verifyPreview(Configuration.ORIENTATION_PORTRAIT)

    @Test
    fun landscapePreviewDecodesTargetWhileMainPlaybackContinues() = verifyPreview(Configuration.ORIENTATION_LANDSCAPE)

    @Test
    fun disabledPortraitPreviewStillSeeksOnRelease() = verifyPreview(Configuration.ORIENTATION_PORTRAIT, false)

    @Test
    fun disabledLandscapePreviewStillSeeksOnRelease() = verifyPreview(Configuration.ORIENTATION_LANDSCAPE, false)

    private fun verifyPreview(orientation: Int, enabled: Boolean = true) {
        preferences.save(enabled)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = createSeekPreviewFixture(context)
        var seekCount = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { this.orientation = orientation }
            val player = remember {
                val renderers = DefaultRenderersFactory(context).setMediaCodecSelector { mime, secure, tunneling ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { !it.softwareOnly }
                }
                ExoPlayer.Builder(context, renderers).build().apply {
                    mainPlayer = this
                    setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                    prepare()
                    play()
                    addListener(object : Player.Listener {
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) seekCount++
                        }
                    })
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                VideoPlayerScreen(
                    player = player,
                    title = "拖动预览测试",
                    sourceLabel = "本地视频",
                    commentMessage = "测试",
                    onBack = {},
                    onRetry = {},
                    details = { Text("视频详情") },
                )
            }
        }
        compose.waitUntil(15_000) { onMain { mainPlayer.isPlaying } }
        val bar = compose.onNodeWithContentDescription("播放进度")
        val dragStarted = SystemClock.elapsedRealtime()
        bar.performTouchInput {
            down(Offset(width * .1f, centerY))
            moveTo(Offset(width * .7f, centerY), 300)
        }
        val image = compose.onNodeWithTag("seek-preview-image")
        if (enabled) {
            compose.onNodeWithTag("seek-preview").assertIsDisplayed()
            compose.waitUntil(1_000) {
                compose.onAllNodesWithTag("seek-preview-image").fetchSemanticsNodes().firstOrNull()
                    ?.config?.get(SemanticsProperties.StateDescription)?.removePrefix("预览位置：")?.toLong()?.let { it > 15_000L } == true
            }
            val firstFrameMs = SystemClock.elapsedRealtime() - dragStarted
            Log.i("SeekPreviewTiming", "orientation=$orientation firstTargetFrameMs=$firstFrameMs")
            val pixels = image.captureToImage().toPixelMap()
            assertTrue("预览应显示明亮的目标段，主视频仍在暗色的开始段", pixels[pixels.width / 2, pixels.height / 2].red > .4f)
        } else {
            compose.onNodeWithTag("seek-preview").assertDoesNotExist()
        }
        bar.performTouchInput { moveTo(Offset(width * .8f, centerY), 150) }
        compose.onNodeWithText("加载中…").assertDoesNotExist()
        if (enabled) {
            image.assertIsDisplayed()
            compose.waitUntil(1_000) {
                image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                    .removePrefix("预览位置：").toLong() > 22_000L
            }
        }
        val before = onMain { mainPlayer.currentPosition }
        compose.waitUntil(5_000) { onMain { mainPlayer.currentPosition > before + 400L } }
        compose.onNodeWithText("加载中…").assertDoesNotExist()
        onMain {
            assertTrue(mainPlayer.isPlaying)
            assertTrue("拖动期间主视频不应跳转", mainPlayer.currentPosition < 10_000L)
            assertEquals(0, seekCount)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, "seek-preview-$orientation.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        bar.performTouchInput { up() }
        compose.onNodeWithTag("seek-preview").assertDoesNotExist()
        compose.waitUntil(10_000) { onMain { mainPlayer.currentPosition > 15_000L && mainPlayer.isPlaying } }
        onMain { assertEquals(1, seekCount) }
        onMain { mainPlayer.pause() }
        val pausedAt = onMain { mainPlayer.currentPosition }
        bar.performTouchInput {
            down(Offset(width * .6f, centerY))
            moveTo(Offset(width * .3f, centerY), 300)
        }
        if (enabled) {
            compose.onNodeWithTag("seek-preview").assertIsDisplayed()
        } else {
            compose.onNodeWithTag("seek-preview").assertDoesNotExist()
        }
        bar.performTouchInput { cancel() }
        compose.onNodeWithTag("seek-preview").assertDoesNotExist()
        onMain {
            assertEquals("取消拖动不应提交跳转", 1, seekCount)
            assertEquals(pausedAt, mainPlayer.currentPosition)
            assertTrue(!mainPlayer.playWhenReady)
        }
        bar.performTouchInput {
            down(Offset(width * .6f, centerY))
            moveTo(Offset(width * .4f, centerY), 300)
            up()
        }
        onMain {
            assertEquals(2, seekCount)
            assertTrue("原本暂停的视频在跳转后仍应暂停", !mainPlayer.playWhenReady)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
