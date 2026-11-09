package io.github.bilibox.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScrubStateTest {
    @Test
    fun releaseCommitsLatestTargetOnlyOnce() {
        val scrub = PlayerScrubState()
        scrub.update(5_000f)
        scrub.update(18_500f)
        assertTrue(scrub.active)
        assertEquals(18_500L, scrub.finish(30_000L))
        assertFalse(scrub.active)
        assertNull(scrub.finish(30_000L))
    }

    @Test
    fun cancelDiscardsTargetAndReleaseClampsToDuration() {
        val scrub = PlayerScrubState()
        scrub.update(18_500f)
        scrub.cancel()
        assertNull(scrub.finish(30_000L))
        scrub.update(40_000f)
        assertEquals(30_000L, scrub.finish(30_000L))
        scrub.update(Float.NaN)
        assertFalse(scrub.active)
    }
}
