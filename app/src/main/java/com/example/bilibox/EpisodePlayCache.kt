package com.example.bilibox

import com.example.bilibox.bili.PlayData

/** How early (in playback ms) the next part's play url is prefetched. */
internal const val NEXT_PART_PREFETCH_AHEAD_MS = 15_000L

/**
 * Short-lived cache for an eagerly fetched play url of a neighbouring part.
 *
 * Bilibili's CDN urls carry expiring signatures, so an entry is only handed
 * out for [ttlMs] after it was fetched. Each entry is consumed at most once.
 */
internal class EpisodePlayCache(
    private val ttlMs: Long = 5 * 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val play: PlayData, val fetchedAt: Long)

    private val entries = mutableMapOf<Long, Entry>()

    fun put(cid: Long, play: PlayData) {
        entries[cid] = Entry(play, clock())
    }

    /** Whether a fresh entry is already available, so it does not need a refetch. */
    fun containsFresh(cid: Long): Boolean {
        val entry = entries[cid] ?: return false
        return clock() - entry.fetchedAt <= ttlMs
    }

    /** Removes and returns the cached play data, or null when missing/expired. */
    fun take(cid: Long): PlayData? {
        val entry = entries.remove(cid) ?: return null
        return entry.play.takeIf { clock() - entry.fetchedAt <= ttlMs }
    }
}
