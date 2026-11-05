package io.github.bilibox.core.playback

/** How playback continues when the current local video reaches its end. */
internal enum class LocalQueueMode {
    LIST_LOOP,
    SINGLE_LOOP,
    STOP_AFTER,
    SHUFFLE,
}
