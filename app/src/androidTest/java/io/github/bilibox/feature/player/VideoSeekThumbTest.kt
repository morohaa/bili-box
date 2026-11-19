package io.github.bilibox.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.bilibox.core.ui.theme.LocalSeekThumbStyle
import io.github.bilibox.core.ui.theme.SeekThumbStyle
import org.junit.Assert.assertEquals
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

        listOf(SeekThumbStyle.DOT, SeekThumbStyle.DIAMOND, SeekThumbStyle.STAR).forEach { option ->
            compose.runOnIdle { style = option }
            television.assertDoesNotExist()
            val thumb = compose.onNodeWithTag("player-seek-thumb-${option.name}", useUnmergedTree = true)
            assertEquals(originalThumb, thumb.fetchSemanticsNode().boundsInRoot)
            assertEquals(originalTrack, track.fetchSemanticsNode().boundsInRoot)
        }
    }
}
