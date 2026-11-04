package io.github.bilibox.core.util



internal fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Search results return durations as unbounded minutes and seconds (for example
 * `4511:45`). Convert those values to the same readable format used elsewhere.
 */
internal fun formatSearchDuration(duration: String): String {
    val parts = duration.trim().split(':')
    if (parts.size !in 2..3) return duration
    val values = parts.map { it.toLongOrNull() ?: return duration }
    val totalSeconds = when (values.size) {
        2 -> values[0] * 60L + values[1]
        else -> values[0] * 3600L + values[1] * 60L + values[2]
    }
    if (totalSeconds < 0L) return duration
    return formatVideoDuration(totalSeconds * 1_000L)
}
