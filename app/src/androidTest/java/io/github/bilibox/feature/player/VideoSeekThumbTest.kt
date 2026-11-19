package io.github.bilibox.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.bilibox.core.ui.theme.LocalSeekThumbStyle
import io.github.bilibox.core.ui.theme.SeekThumbStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoSeekThumbTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun themeChangesThumbWithoutMovingTrack() {
        var style by mutableStateOf(SeekThumbStyle.TELEVISION)
        compose.setContent {
            CompositionLocalProvider(LocalSeekThumbStyle provides style) {
                VideoSeekBar(
                    position = 500f,
                    duration = 1_000L,
                    bufferedPosition = 750L,
                    onSeek = {},
                    onSeekFinished = {},
                )
            }
        }
        val track = compose.onNodeWithTag("player-seek-track", useUnmergedTree = true)
        val originalTrack = track.fetchSemanticsNode().boundsInRoot
        val television = compose.onNodeWithTag("player-seek-thumb-TELEVISION", useUnmergedTree = true)
        val originalThumb = television.fetchSemanticsNode().boundsInRoot

        SeekThumbStyle.entries.filterNot { it == SeekThumbStyle.TELEVISION }.forEach { option ->
            compose.runOnIdle { style = option }
            television.assertDoesNotExist()
            val thumb = compose.onNodeWithTag("player-seek-thumb-${option.name}", useUnmergedTree = true)
            assertEquals(originalThumb, thumb.fetchSemanticsNode().boundsInRoot)
            assertEquals(originalTrack, track.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun xiaoheiHasTwoWhiteEyeRingsAroundDarkPupils() {
        compose.setContent {
            CompositionLocalProvider(LocalSeekThumbStyle provides SeekThumbStyle.XIAOHEI) {
                VideoSeekBar(
                    position = 500f,
                    duration = 1_000L,
                    bufferedPosition = 750L,
                    onSeek = {},
                    onSeekFinished = {},
                )
            }
        }
        val thumb = compose.onNodeWithTag("player-seek-thumb-XIAOHEI", useUnmergedTree = true)
        val pixels = thumb.captureToImage().toPixelMap()
        listOf(.28f, .72f).forEach { eye ->
            val pupil = pixels[(pixels.width * eye).toInt(), (pixels.height * .55f).toInt()]
            assertTrue(pupil.red < .2f && pupil.green < .2f && pupil.blue < .2f)
            listOf(eye - .16f, eye + .16f).forEach { edge ->
                val rim = pixels[(pixels.width * edge).toInt(), (pixels.height * .55f).toInt()]
                assertTrue(rim.red > .8f && rim.green > .8f && rim.blue > .8f)
            }
        }
    }
}
