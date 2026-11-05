package io.github.bilibox.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "%.0f KB".format(bytes / 1_000.0)
}

internal fun resolutionLabel(width: Int, height: Int): String {
    val shortSide = minOf(width, height)
    return when {
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "2K"
        shortSide >= 1080 -> "1080P"
        shortSide >= 720 -> "720P"
        shortSide >= 480 -> "480P"
        else -> "${shortSide}P"
    }
}

internal fun frameRateLabel(frameRate: Float): String {
    val rounded = frameRate.roundToInt()
    val text = if (kotlin.math.abs(frameRate - rounded) < .05f) {
        "$rounded"
    } else {
        "%.2f".format(frameRate).trimEnd('0').trimEnd('.')
    }
    return "$text fps"
}

internal fun formatRelativeDate(milliseconds: Long): String {
    if (milliseconds <= 0L) return "未知"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(milliseconds).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "$days 天前"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}

internal fun formatFullDate(milliseconds: Long, formatter: DateTimeFormatter): String = if (milliseconds <= 0L) {
    "未知"
} else {
    Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).format(formatter)
}
