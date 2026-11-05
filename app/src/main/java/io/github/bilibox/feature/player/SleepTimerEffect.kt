package io.github.bilibox.feature.player

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.playback.PlaybackSleepTimer
import io.github.bilibox.core.playback.SleepTimerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SleepFadeMs = 30_000L
private const val SleepWarnMs = 60_000L

/**
 * Drives [timer]: pauses when the wall-clock deadline is reached, fades the
 * volume, counts finished files and raises the last-minute warning.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun SleepTimerEffect(player: ExoPlayer, timer: PlaybackSleepTimer) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(player, timer.mode, timer.endAt) {
        if (timer.mode != SleepTimerMode.TIME) return@LaunchedEffect
        try {
            while (isActive) {
                val left = timer.endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    player.playWhenReady = false
                    timer.cancel()
                    Toast.makeText(context, "定时关闭：已暂停播放", Toast.LENGTH_SHORT).show()
                    break
                }
                if (timer.warn && !timer.warned && left <= SleepWarnMs) timer.showWarning()
                player.volume = if (timer.fade && left <= SleepFadeMs) {
                    (left.toFloat() / SleepFadeMs).coerceIn(0f, 1f)
                } else {
                    1f
                }
                delay(if (left <= SleepFadeMs) 200L else 1_000L)
            }
        } finally {
            player.volume = 1f
        }
    }

    // 按内容: count finished files; only the last one is allowed to pause at
    // its end, otherwise 连播 / 自动下一 P would never reach the next file.
    DisposableEffect(player, timer.mode, timer.videosLeft) {
        if (timer.mode != SleepTimerMode.VIDEOS) return@DisposableEffect onDispose { }
        player.pauseAtEndOfMediaItems = timer.videosLeft <= 1
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && timer.mode == SleepTimerMode.VIDEOS) {
                    timer.onVideoFinished()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    val count = timer.videoCount
                    timer.cancel()
                    Toast.makeText(
                        context,
                        if (count <= 1) "定时关闭：当前视频已播完" else "定时关闭：已播完 $count 个视频",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.pauseAtEndOfMediaItems = false
        }
    }

    // The last file of a "播完多个视频" timer still fades and warns like a countdown.
    LaunchedEffect(player, timer.mode, timer.videosLeft) {
        if (timer.mode != SleepTimerMode.VIDEOS) return@LaunchedEffect
        try {
            while (isActive) {
                if (timer.videosLeft > 1) {
                    player.volume = 1f
                } else {
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    val remaining = if (duration > 0L) {
                        (duration - player.currentPosition).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    if (timer.warn && !timer.warned && remaining in 1..SleepWarnMs) timer.showWarning()
                    player.volume = if (timer.fade && remaining in 0..SleepFadeMs) {
                        (remaining.toFloat() / SleepFadeMs).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                }
                delay(250L)
            }
        } finally {
            player.volume = 1f
        }
    }
}
