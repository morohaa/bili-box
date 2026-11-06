package io.github.bilibox.core.playback

import kotlin.math.roundToInt

internal const val PlaybackSpeedMin = 0.5f
internal const val PlaybackSpeedMax = 3f
internal const val PlaybackSpeedStep = 0.05f
internal val PlaybackSpeedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

/** Temporary speed used while the video area is held down. */
internal const val PlaybackSpeedBoost = 2f

/**
 * 长按只会加速：当前倍速低于 2x 时临时切到 2x，已经更快时保持用户的选择，
 * 避免“长按反而慢下来”。
 */
internal fun longPressPlaybackSpeed(current: Float): Float = maxOf(current, PlaybackSpeedBoost)

internal fun quantizePlaybackSpeed(value: Float): Float {
    val stepped = (value / PlaybackSpeedStep).roundToInt() * PlaybackSpeedStep
    return stepped.coerceIn(PlaybackSpeedMin, PlaybackSpeedMax)
}
