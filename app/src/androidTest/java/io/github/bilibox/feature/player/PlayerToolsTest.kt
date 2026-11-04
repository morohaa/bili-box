package io.github.bilibox.feature.player

import io.github.bilibox.R
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.MainActivity
import io.github.bilibox.core.util.captureVideoFrame
import io.github.bilibox.core.util.saveVideoFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerToolsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lockedPlayerHidesScreenshotAndBlocksDoubleTapUntilUnlocked() {
        var locked by mutableStateOf(false)
        var playbackToggles = 0
        var controlToggles = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                PlayerTouchSurface(
                    locked = locked,
                    onToggleControls = { controlToggles++ },
                    onTogglePlayback = { playbackToggles++ },
                    modifier = Modifier.fillMaxSize().testTag("video-touch")
                )
                PlayerSideTools(
                    locked = locked,
                    screenshotEnabled = true,
                    onScreenshot = {},
                    onToggleLock = { locked = !locked },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        compose.onNodeWithTag("video-touch").performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertEquals(1, playbackToggles) }
        compose.onNodeWithContentDescription("锁定屏幕").performClick()
        compose.onNodeWithContentDescription("截取视频画面").assertDoesNotExist()
        compose.onNodeWithContentDescription("解锁屏幕").assertExists()
        compose.onNodeWithTag("video-touch").performTouchInput { doubleClick(center) }
        compose.runOnIdle {
            assertEquals(1, playbackToggles)
            assertEquals(1, controlToggles)
        }
        compose.onNodeWithContentDescription("解锁屏幕").performClick()
        compose.onNodeWithContentDescription("截取视频画面").assertExists()
        compose.onNodeWithTag("video-touch").performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertEquals(2, playbackToggles) }
    }

    @Test
    fun portraitVideoSwipesSelectBrightnessOrVolumeByStartingSide() {
        val startedSides = mutableListOf<PlayerGestureSide>()
        var leftDelta = 0f
        var rightDelta = 0f
        compose.setContent {
            PlayerTouchSurface(
                locked = false,
                verticalGesturesEnabled = true,
                onToggleControls = {},
                onTogglePlayback = {},
                onVerticalGestureStart = { startedSides.add(it) },
                onVerticalSwipe = { side, delta ->
                    if (side == PlayerGestureSide.LEFT) leftDelta += delta else rightDelta += delta
                },
                // The portrait page still has a compact 16:9 video touch surface.
                modifier = Modifier.size(320.dp, 180.dp).testTag("video-touch")
            )
        }
        compose.onNodeWithTag("video-touch").performTouchInput {
            swipe(Offset(center.x * .4f, center.y * 1.5f), Offset(center.x * .4f, center.y * .5f))
        }
        compose.onNodeWithTag("video-touch").performTouchInput {
            swipe(Offset(center.x * 1.6f, center.y * 1.5f), Offset(center.x * 1.6f, center.y * .5f))
        }
        compose.runOnIdle {
            assertEquals(listOf(PlayerGestureSide.LEFT, PlayerGestureSide.RIGHT), startedSides)
            assertTrue(leftDelta < 0f)
            assertTrue(rightDelta < 0f)
        }
    }

    @Test
    fun gestureFeedbackUsesReferenceStyleAndExposesCurrentLevelAccessibly() {
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlayerGestureFeedback(PlayerGestureSide.LEFT, .45f)
            }
        }
        compose.onNodeWithContentDescription("亮度调节").assertExists()
        compose.onNodeWithContentDescription("亮度调节")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "45%"))
    }

    @Test
    fun brightnessGestureSetsWindowBrightnessOverride() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val original = activity.window.attributes.screenBrightness
                setPlayerWindowBrightness(activity.window, .73f)
                assertEquals(.73f, activity.window.attributes.screenBrightness, .001f)
                val attributes = activity.window.attributes
                attributes.screenBrightness = original
                activity.window.attributes = attributes
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun captureAndGallerySaveContainOnlyVideoSurfaceDespiteCoveringOverlay() {
        lateinit var playerView: PlayerView
        compose.setContent {
            Box(Modifier.size(160.dp, 90.dp)) {
                AndroidView(
                    factory = {
                        (LayoutInflater.from(it).inflate(R.layout.video_player_view, null) as PlayerView)
                            .also { view -> playerView = view }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // A phone/window screenshot would be red. The video-only capture must stay green.
                Box(Modifier.fillMaxSize().background(Color.Red))
            }
        }
        compose.waitUntil(5_000) {
            compose.runOnIdle { (playerView.videoSurfaceView as TextureView).isAvailable }
        }
        compose.runOnIdle {
            val texture = playerView.videoSurfaceView as TextureView
            val surface = Surface(texture.surfaceTexture)
            try {
                val canvas = surface.lockCanvas(null)
                canvas.drawColor(AndroidColor.GREEN)
                surface.unlockCanvasAndPost(canvas)
            } finally { surface.release() }
        }
        compose.waitUntil(5_000) {
            compose.runOnIdle {
                captureVideoFrame(playerView)?.let {
                    val green = it.getPixel(it.width / 2, it.height / 2) == AndroidColor.GREEN
                    it.recycle()
                    green
                } ?: false
            }
        }
        val frame = compose.runOnIdle { captureVideoFrame(playerView)!! }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val uri = saveVideoFrame(context, frame)
            try {
                val saved = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                assertNotNull(saved)
                assertEquals(frame.width, saved.width)
                assertEquals(frame.height, saved.height)
                assertEquals(AndroidColor.GREEN, saved.getPixel(saved.width / 2, saved.height / 2))
                saved.recycle()
            } finally { context.contentResolver.delete(uri, null, null) }
        } finally { frame.recycle() }
    }
}