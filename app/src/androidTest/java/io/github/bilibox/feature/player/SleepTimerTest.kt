package io.github.bilibox.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour of the player-held sleep timer, independent of the Compose sheet. */
class SleepTimerTest {
    @Test
    fun minutesCountdownExtendsAndCancels() {
        val timer = PlaybackSleepTimer()
        assertFalse(timer.active)

        timer.startMinutes(30)
        assertTrue(timer.active)
        assertEquals(SleepTimerMode.TIME, timer.mode)
        assertEquals(30, timer.minutes)
        assertEquals(30 * 60_000L, timer.totalMs)

        val firstEnd = timer.endAt
        timer.extend(10)
        assertEquals(40, timer.minutes)
        assertEquals(firstEnd + 10 * 60_000L, timer.endAt)

        timer.cancel()
        assertFalse(timer.active)
        assertEquals(0, timer.minutes)
        assertEquals(0L, timer.endAt)
    }

    @Test
    fun videoCountdownStopsAfterTheConfiguredFile() {
        val timer = PlaybackSleepTimer()
        timer.startVideos(3)
        assertEquals(SleepTimerMode.VIDEOS, timer.mode)
        assertEquals(3, timer.videosLeft)

        timer.onVideoFinished()
        assertEquals(2, timer.videosLeft)
        timer.onVideoFinished()
        assertEquals(1, timer.videosLeft)
        // The last file pauses and is reported by the player listener instead.
        timer.onVideoFinished()
        assertEquals(1, timer.videosLeft)

        timer.cancel()
        assertFalse(timer.active)
        assertEquals(0, timer.videoCount)
    }

    @Test
    fun extendingACountdownTurnsItIntoMinutes() {
        val timer = PlaybackSleepTimer()
        timer.startVideos(2)
        timer.extend(10, currentRemainingMs = 330_000L)
        assertEquals(SleepTimerMode.TIME, timer.mode)
        // 5:30 left rounds up to 6 minutes, plus the requested 10.
        assertEquals(16, timer.minutes)
    }

    @Test
    fun warningIsOfferedOnceAndResetByAnExtension() {
        val timer = PlaybackSleepTimer()
        timer.startMinutes(5)
        assertFalse(timer.warning)

        timer.showWarning()
        assertTrue(timer.warning)
        assertTrue(timer.warned)

        timer.dismissWarning()
        assertFalse(timer.warning)
        assertTrue(timer.warned)

        timer.extend(5)
        assertFalse(timer.warning)
        assertFalse(timer.warned)
    }
}