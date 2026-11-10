package io.github.bilibox.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.bilibox.data.player.VideoPositionPreferences
import kotlin.math.roundToInt

@Stable
internal class PlayerVideoPositionState(private val store: VideoPositionPreferences) {
    var adjustmentDp by mutableIntStateOf(store.load())
        private set
    var adjusting by mutableStateOf(false)

    val label: String
        get() = when {
            adjustmentDp < 0 -> "偏上"
            adjustmentDp > 0 -> "偏下"
            else -> "默认位置"
        }

    fun update(value: Float) {
        adjustmentDp = value.roundToInt().coerceIn(
            -VideoPositionPreferences.MAX_ADJUSTMENT_DP,
            VideoPositionPreferences.MAX_ADJUSTMENT_DP,
        )
    }

    fun save() = store.save(adjustmentDp)

    fun reset() {
        adjustmentDp = 0
        save()
    }

    fun finish() {
        save()
        adjusting = false
    }
}

@Composable
internal fun rememberPlayerVideoPosition(): PlayerVideoPositionState {
    val context = LocalContext.current.applicationContext
    return remember(context) { PlayerVideoPositionState(VideoPositionPreferences(context)) }
}

internal fun playerVideoOffsetDp(verticalFullScreen: Boolean, portraitVideo: Boolean, adjustmentDp: Int): Int = when {
    !verticalFullScreen -> 0
    portraitVideo -> -32 + adjustmentDp
    else -> -88 + adjustmentDp
}
