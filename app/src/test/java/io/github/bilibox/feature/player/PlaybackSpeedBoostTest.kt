package io.github.bilibox.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedBoostTest {
    @Test
    fun longPressBoostsNormalSpeedsToDouble() {
        assertEquals(2f, longPressPlaybackSpeed(0.5f), 0f)
        assertEquals(2f, longPressPlaybackSpeed(1f), 0f)
        assertEquals(2f, longPressPlaybackSpeed(1.5f), 0f)
    }

    @Test
    fun longPressNeverSlowsAFasterUserChoice() {
        assertEquals(2f, longPressPlaybackSpeed(2f), 0f)
        assertEquals(3f, longPressPlaybackSpeed(3f), 0f)
    }
}
