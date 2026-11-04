package io.github.bilibox.feature.online

import io.github.bilibox.data.bili.PlayData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodePlayCacheTest {
    private var now = 10_000L
    private val cache = EpisodePlayCache(ttlMs = 60_000L, clock = { now })

    @Test
    fun freshEntryIsHandedOutOnce() {
        val play = PlayData(quality = 80)
        cache.put(cid = 42L, play = play)

        assertTrue(cache.containsFresh(42L))
        assertSame(play, cache.take(42L))
        // Prefetched data is consumed by the switch that uses it.
        assertFalse(cache.containsFresh(42L))
        assertNull(cache.take(42L))
    }

    @Test
    fun expiredEntryIsDiscardedAndRefetched() {
        cache.put(cid = 42L, play = PlayData(quality = 80))
        now += 60_001L

        assertFalse(cache.containsFresh(42L))
        assertNull(cache.take(42L))
    }

    @Test
    fun entriesAreTrackedPerPart() {
        val first = PlayData(quality = 80)
        val second = PlayData(quality = 64)
        cache.put(cid = 1L, play = first)
        cache.put(cid = 2L, play = second)

        assertSame(second, cache.take(2L))
        assertSame(first, cache.take(1L))
    }
}
