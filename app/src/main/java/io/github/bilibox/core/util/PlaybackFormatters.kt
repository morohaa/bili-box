package io.github.bilibox.core.util

import java.util.Locale

/** Compact label for pills and the player bar: 1x / 1.5x / 0.75x. */
internal fun formatSpeedBadge(value: Float): String {
    val text = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    return "${text}x"
}

internal fun formatPlaybackTime(positionMs: Long, unknownWhenZero: Boolean = false): String {
    if (unknownWhenZero && positionMs <= 0L) return "--:--"
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
