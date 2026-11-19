package io.github.bilibox.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeTest {
    @Test
    fun standardModesChooseWhatHappensWhenTheCurrentItemEnds() {
        val positions = listOf(0, 1, 2)
        assertEquals(listOf(null, null, null), positions.map { PlaybackMode.STOP_AFTER.nextIndex(it, 3) })
        assertEquals(listOf(1, 2, null), positions.map { PlaybackMode.SEQUENTIAL.nextIndex(it, 3) })
        assertEquals(positions, positions.map { PlaybackMode.SINGLE_LOOP.nextIndex(it, 3) })
        assertEquals(listOf(1, 2, 0), positions.map { PlaybackMode.LIST_LOOP.nextIndex(it, 3) })
    }

    @Test
    fun singleItemOnlyReplaysInLoopModes() {
        assertEquals(listOf(null, null, 0, 0), PlaybackMode.standard.map { it.nextIndex(0, 1) })
    }

    @Test
    fun invalidCurrentItemNeverTriggersPlayback() {
        PlaybackMode.entries.forEach { mode ->
            assertNull(mode.nextIndex(0, 0))
            assertNull(mode.nextIndex(-1, 3))
            assertNull(mode.nextIndex(3, 3))
        }
    }

    @Test
    fun shuffleRemainsAvailableForLocalQueues() {
        assertTrue(PlaybackMode.SHUFFLE.nextIndex(1, 3) in listOf(0, 2))
        assertEquals(0, PlaybackMode.SHUFFLE.nextIndex(0, 1))
    }
}
