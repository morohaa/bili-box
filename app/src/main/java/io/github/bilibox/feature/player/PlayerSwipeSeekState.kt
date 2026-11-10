package io.github.bilibox.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 横滑以按下时的进度为基准，滑满画面最多调整两分钟；松手才提交跳转。 */
internal class PlayerSwipeSeekState(private val scrub: PlayerScrubState) {
    var active by mutableStateOf(false)
        private set
    var startPosition = 0L
        private set
    private var duration = 0L
    private var distanceFraction = 0.0

    fun start(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        duration = durationMs
        startPosition = positionMs.coerceIn(0L, durationMs)
        distanceFraction = 0.0
        active = true
        scrub.update(startPosition.toFloat())
    }

    fun drag(deltaFraction: Float) {
        if (!active || !deltaFraction.isFinite()) return
        distanceFraction += deltaFraction
        val target = startPosition + distanceFraction * duration.coerceAtMost(120_000L)
        scrub.update(target.coerceIn(0.0, duration.toDouble()).toFloat())
    }

    fun finish(currentDurationMs: Long): Long? {
        if (!active) return null
        active = false
        return scrub.finish(currentDurationMs)
    }

    fun cancel() {
        if (!active) return
        active = false
        scrub.cancel()
    }
}
