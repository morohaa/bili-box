package io.github.bilibox.core.playback

/**
 * Playlist hooks that must work without the UI: background auto-advance and the
 * notification / lock-screen "next" button call these directly on the main thread,
 * because Compose stops recomposing once the activity is stopped.
 */
internal interface ListenQueue {
    val hasNext: Boolean
    fun skipToNext()
}
