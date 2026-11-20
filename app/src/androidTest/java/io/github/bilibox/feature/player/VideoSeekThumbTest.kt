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

    @Test
    fun corgiKeepsItsFaceCenteredAndWagsOnlyDuringPlayback() {
        var playing by mutableStateOf(false)
        var scrubbing by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalSeekThumbStyle provides SeekThumbStyle.CORGI) {
                VideoSeekBar(
                    position = 500f,
                    duration = 1_000L,
                    bufferedPosition = 750L,
                    onSeek = {},
                    onSeekFinished = {},
                    scrubbing = scrubbing,
                    isPlaying = playing,
                )
            }
        }
        val thumb = compose.onNodeWithTag("player-seek-thumb-CORGI", useUnmergedTree = true)
        val track = compose.onNodeWithTag("player-seek-track", useUnmergedTree = true)
        val originalTrackBounds = track.fetchSemanticsNode().boundsInRoot
        val resting = thumb.captureToImage().toPixelMap()
        val eyesAndNose = listOf(.41f to .45f, .59f to .45f, .51f to .55f)
        eyesAndNose.forEach { (horizontal, vertical) ->
            val ink = resting[(resting.width * horizontal).toInt(), (resting.height * vertical).toInt()]
            assertTrue(ink.red < .35f && ink.green < .35f && ink.blue < .35f)
        }

        fun matchesResting(columns: IntRange): Boolean {
            val frame = thumb.captureToImage().toPixelMap()
            return columns.all { column -> (0 until resting.height).all { row -> frame[column, row] == resting[column, row] } }
        }

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playing = true }
        compose.mainClock.advanceTimeBy(64)
        val faceStart = resting.width / 3
        assertTrue(!matchesResting(0 until faceStart))
        assertTrue(matchesResting(faceStart until resting.width))
        assertEquals(originalTrackBounds, track.fetchSemanticsNode().boundsInRoot)

        compose.runOnIdle { scrubbing = true }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(matchesResting(0 until resting.width))

        compose.runOnIdle {
            playing = false
            scrubbing = false
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(matchesResting(0 until resting.width))
    }
}
