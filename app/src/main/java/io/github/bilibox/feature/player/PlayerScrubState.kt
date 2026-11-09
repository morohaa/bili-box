package io.github.bilibox.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 拖动只更新目标位置，松手才由调用方跳转；取消手势不改变播放位置。 */
internal class PlayerScrubState {
    var active by mutableStateOf(false)
        private set
    var position by mutableFloatStateOf(0f)
        private set

    fun update(target: Float) {
        if (!target.isFinite()) return
        position = target.coerceAtLeast(0f)
        active = true
    }

    fun finish(duration: Long): Long? {
        if (!active) return null
        active = false
        return position.toLong().coerceIn(0L, duration.coerceAtLeast(0L))
    }

    fun cancel() {
        active = false
    }
}
