package io.github.bilibox.core.playback

internal enum class PlaybackMode(val label: String) {
    STOP_AFTER("播完停止"),
    SEQUENTIAL("顺序播放"),
    SINGLE_LOOP("单集循环"),
    LIST_LOOP("列表循环"),
    SHUFFLE("随机播放"),
    ;

    fun nextIndex(current: Int, count: Int): Int? {
        if (current !in 0 until count) return null
        return when (this) {
            STOP_AFTER -> null
            SEQUENTIAL -> (current + 1).takeIf { it < count }
            SINGLE_LOOP -> current
            LIST_LOOP -> (current + 1) % count
            SHUFFLE -> if (count == 1) current else (0 until count - 1).random().let { if (it < current) it else it + 1 }
        }
    }

    companion object {
        val standard = entries.filterNot { it == SHUFFLE }
    }
}
