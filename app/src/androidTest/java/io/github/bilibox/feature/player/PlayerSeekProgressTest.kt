package io.github.bilibox.feature.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
class PlayerSeekProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thumbFollowsPlaybackFramesAndSnapsToScrubAndSeekPositions() {
        val player = compose.runOnUiThread { FrameTestPlayer() }
        var scrubPosition by mutableStateOf<Float?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val position by rememberSeekBarPosition(player, scrubbing = scrubPosition != null)
            VideoSeekBar(
                position = scrubPosition ?: position.toFloat(),
                duration = 1_000L,
                bufferedPosition = 1_000L,
                onSeek = {},
                onSeekFinished = {},
                scrubbing = scrubPosition != null,
            )
        }
        val slider = compose.onNodeWithContentDescription("播放进度")
        val thumb = compose.onNodeWithTag("player-seek-thumb-TELEVISION", useUnmergedTree = true)
        fun sliderValue() = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        fun thumbX() = thumb.fetchSemanticsNode().boundsInRoot.center.x

        assertEquals(100f, sliderValue(), 0f)
        var lastThumbX = thumbX()
        listOf(116L, 132L, 164L).forEach { position ->
            compose.runOnIdle { player.positionMs = position }
            compose.mainClock.advanceTimeBy(32)
            assertEquals(position.toFloat(), sliderValue(), 0f)
            val nextThumbX = thumbX()
            assertTrue(nextThumbX > lastThumbX)
            lastThumbX = nextThumbX
        }

        compose.runOnIdle { scrubPosition = 700f }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(700f, sliderValue(), 0f)
        compose.runOnIdle { player.positionMs = 220L }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(700f, sliderValue(), 0f)

        compose.runOnIdle { scrubPosition = null }
        compose.mainClock.advanceTimeBy(48)
        assertEquals(220f, sliderValue(), 0f)

        compose.runOnIdle { player.setState(Player.STATE_BUFFERING) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { player.positionMs = 300L }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(220f, sliderValue(), 0f)

        compose.runOnIdle { player.jumpTo(800L) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(800f, sliderValue(), 0f)

        compose.runOnIdle { player.setState(Player.STATE_READY) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { player.positionMs = 832L }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(832f, sliderValue(), 0f)
    }

    private class FrameTestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var positionMs = 100L
        private var playbackState = Player.STATE_READY
        private var discontinuity: Long? = null
        private val playlist = listOf(MediaItemData.Builder("fixture").setDurationUs(1_000_000L).build())

        override fun getState(): State = State.Builder()
            .setPlaylist(playlist)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { positionMs }
            .apply { discontinuity?.let { setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, it) } }
            .build()

        fun setState(state: Int) {
            playbackState = state
            invalidateState()
        }

        fun jumpTo(position: Long) {
            positionMs = position
            discontinuity = position
            invalidateState()
            discontinuity = null
        }
    }
}
