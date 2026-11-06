package io.github.bilibox.core.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTimeFoldTest {
    @Test
    fun foldedTimeDividesRemainingDurationBySpeed() {
        assertEquals(397_142L, foldedRemainingMs(1_112_000L, 2.8f))
        assertEquals(556_000L, foldedRemainingMs(1_112_000L, 2f))
        assertEquals(1_112_000L, foldedRemainingMs(1_112_000L, 1f))
    }

    @Test
    fun savedPercentReflectsSpeedAndNeverGoesNegative() {
        assertEquals(64, savedTimePercent(2.8f))
        assertEquals(50, savedTimePercent(2f))
        assertEquals(33, savedTimePercent(1.5f))
        assertEquals(0, savedTimePercent(1f))
        assertEquals(0, savedTimePercent(0.75f))
    }
}
