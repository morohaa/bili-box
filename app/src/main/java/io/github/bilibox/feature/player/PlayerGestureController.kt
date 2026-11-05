package io.github.bilibox.feature.player

import android.media.AudioManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.playback.PlaybackSpeedBoost
import io.github.bilibox.core.playback.longPressPlaybackSpeed

internal data class PlayerGestureFeedbackState(
    val side: PlayerGestureSide,
    val level: Float,
)

@Stable
internal class PlayerGestureController internal constructor(
    private val player: ExoPlayer,
    private val activity: ComponentActivity?,
    private val audioManager: AudioManager,
    private val isScreenLocked: () -> Boolean,
    private val onSpeedChange: (Float) -> Unit,
) {
    var feedback by mutableStateOf<PlayerGestureFeedbackState?>(null)
        private set
    var verticalGestureActive by mutableStateOf(false)
        private set
    var speedBoostActive by mutableStateOf(false)
        private set
    var speedBoostSpeed by mutableFloatStateOf(PlaybackSpeedBoost)
        private set

    private var screenBrightness by mutableFloatStateOf(activity?.let(::readScreenBrightness) ?: .5f)
    private var volumeGestureRemainder by mutableFloatStateOf(0f)
    private var speedBeforeBoost by mutableFloatStateOf(1f)

    fun startVerticalGesture(side: PlayerGestureSide) {
        if (speedBoostActive) return
        verticalGestureActive = true
        if (side == PlayerGestureSide.LEFT) {
            screenBrightness = activity?.let(::readScreenBrightness) ?: screenBrightness
        } else {
            volumeGestureRemainder = 0f
        }
        updateFeedback(side)
    }

    fun adjustVerticalGesture(side: PlayerGestureSide, deltaFraction: Float) {
        if (speedBoostActive) return
        if (side == PlayerGestureSide.LEFT) {
            val newBrightness = (screenBrightness - deltaFraction).coerceIn(.02f, 1f)
            screenBrightness = newBrightness
            activity?.window?.let { setPlayerWindowBrightness(it, newBrightness) }
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            volumeGestureRemainder += -deltaFraction * maxVolume
            val volumeSteps = volumeGestureRemainder.toInt()
            if (volumeSteps != 0) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (current + volumeSteps).coerceIn(0, maxVolume),
                    0,
                )
                volumeGestureRemainder -= volumeSteps
            }
        }
        updateFeedback(side)
    }

    fun endVerticalGesture() {
        verticalGestureActive = false
    }

    fun clearFeedback() {
        feedback = null
    }

    fun startSpeedBoost() {
        if (isScreenLocked() || speedBoostActive) return
        val current = player.playbackParameters.speed
        val target = longPressPlaybackSpeed(current)
        speedBeforeBoost = current
        speedBoostSpeed = target
        speedBoostActive = true
        feedback = null
        player.setPlaybackSpeed(target)
        onSpeedChange(target)
    }

    fun endSpeedBoost() {
        if (!speedBoostActive) return
        speedBoostActive = false
        runCatching { player.setPlaybackSpeed(speedBeforeBoost) }
        onSpeedChange(speedBeforeBoost)
    }

    fun restoreSpeedBoostOnDispose() {
        if (speedBoostActive) {
            runCatching { player.setPlaybackSpeed(speedBeforeBoost) }
        }
    }

    private fun updateFeedback(side: PlayerGestureSide) {
        val level = if (side == PlayerGestureSide.LEFT) {
            screenBrightness.coerceIn(0f, 1f)
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        }
        feedback = PlayerGestureFeedbackState(side, level)
    }
}

@Composable
internal fun rememberPlayerGestureController(
    player: ExoPlayer,
    activity: ComponentActivity?,
    audioManager: AudioManager,
    screenLocked: Boolean,
    onSpeedChange: (Float) -> Unit,
): PlayerGestureController {
    val currentScreenLocked = rememberUpdatedState(screenLocked)
    val currentOnSpeedChange = rememberUpdatedState(onSpeedChange)
    return remember(player, activity, audioManager) {
        PlayerGestureController(
            player = player,
            activity = activity,
            audioManager = audioManager,
            isScreenLocked = { currentScreenLocked.value },
            onSpeedChange = { currentOnSpeedChange.value(it) },
        )
    }
}

private fun readScreenBrightness(activity: ComponentActivity): Float {
    val override = activity.window.attributes.screenBrightness
    if (override >= 0f) return override.coerceIn(0f, 1f)
    val systemBrightness = Settings.System.getInt(
        activity.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return systemBrightness.coerceIn(0, 255) / 255f
}
