package io.github.bilibox.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSwipeSeekStateTest {
    private val scrub = PlayerScrubState()
    private val swipe = PlayerSwipeSeekState(scrub)

    @Test
    fun swipePreviewsFromInitialPositionAndCommitsOnlyOnRelease() {
        swipe.start(60_000L, 300_000L)
        swipe.drag(.25f)
        assertTrue(swipe.active)
        assertEquals(90_000f, scrub.position, 1f)
        swipe.drag(-.5f)
        assertEquals(30_000f, scrub.position, 1f)
        assertEquals(30_000L, swipe.finish(300_000L))
        assertFalse(scrub.active)
        assertFalse(swipe.active)
        assertNull(swipe.finish(300_000L))
    }

    @Test
    fun shortVideoUsesItsDurationAndTargetsStayWithinBounds() {
        swipe.start(30_000L, 60_000L)
        swipe.drag(.25f)
        assertEquals(45_000f, scrub.position, 1f)
        swipe.drag(1f)
        assertEquals(60_000f, scrub.position, 1f)
        swipe.drag(-3f)
        assertEquals(0L, swipe.finish(60_000L))
    }

    @Test
    fun reversingAfterReachingEdgeUsesTotalFingerDisplacement() {
        swipe.start(5_000L, 300_000L)
        swipe.drag(-.25f)
        swipe.drag(.125f)
        assertEquals(0f, scrub.position, 1f)
        swipe.drag(.25f)
        assertEquals(20_000L, swipe.finish(300_000L))
    }

    @Test
    fun cancellationDiscardsSeekAndNextSwipeStartsAtCurrentPosition() {
        swipe.start(60_000L, 300_000L)
        swipe.drag(.5f)
        swipe.cancel()
        assertFalse(scrub.active)
        assertNull(swipe.finish(300_000L))
        swipe.start(10_000L, 300_000L)
        swipe.drag(.25f)
        assertEquals(40_000L, swipe.finish(300_000L))
    }

    @Test
    fun unavailableDurationAndInvalidMovementDoNotProduceInvalidSeek() {
        swipe.start(20_000L, 0L)
        swipe.drag(.5f)
        assertFalse(swipe.active)
        assertNull(swipe.finish(0L))
        swipe.start(20_000L, 60_000L)
        swipe.drag(Float.NaN)
        swipe.drag(Float.POSITIVE_INFINITY)
        assertEquals(10_000L, swipe.finish(10_000L))
    }
}
