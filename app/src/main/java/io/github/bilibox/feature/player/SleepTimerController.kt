package io.github.bilibox.feature.player

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SleepFadeMs = 30_000L
private const val SleepWarnMs = 60_000L

internal enum class SleepTimerMode { OFF, TIME, VIDEOS }

/** Sleep timer shared by the player details pane and the listen page. */
@Stable
internal class PlaybackSleepTimer {
    var mode by mutableStateOf(SleepTimerMode.OFF)
        private set

    /** elapsedRealtime deadline, or 0 when not counting minutes. */
    var endAt by mutableLongStateOf(0L)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var minutes by mutableIntStateOf(0)
        private set

    /** Configured and remaining item count for [SleepTimerMode.VIDEOS]. */
    var videoCount by mutableIntStateOf(0)
        private set
    var videosLeft by mutableIntStateOf(0)
        private set

    var fade by mutableStateOf(true)
    var warn by mutableStateOf(true)

    /** True once the last-minute banner should be on screen. */
    var warning by mutableStateOf(false)
        private set

    /** The banner was already offered once for this deadline. */
    internal var warned by mutableStateOf(false)
        private set

    val active: Boolean get() = mode != SleepTimerMode.OFF

    fun startMinutes(value: Int) {
        val safe = value.coerceAtLeast(1)
        mode = SleepTimerMode.TIME
        minutes = safe
        totalMs = safe * 60_000L
        endAt = SystemClock.elapsedRealtime() + totalMs
        videoCount = 0
        videosLeft = 0
        clearWarning()
    }

    fun startVideos(count: Int) {
        val safe = count.coerceAtLeast(1)
        mode = SleepTimerMode.VIDEOS
        endAt = 0L
        totalMs = 0L
        minutes = 0
        videoCount = safe
        videosLeft = safe
        clearWarning()
    }

    /** Adds minutes; a "播完多个视频" timer turns into a countdown on extend. */
    fun extend(extraMinutes: Int = 10, currentRemainingMs: Long = 0L) {
        when (mode) {
            SleepTimerMode.TIME -> {
                val extraMs = extraMinutes * 60_000L
                endAt += extraMs
                totalMs += extraMs
                minutes += extraMinutes
                clearWarning()
            }

            SleepTimerMode.VIDEOS -> {
                val extra = extraMinutes + ceil(currentRemainingMs / 60_000.0).toInt()
                startMinutes(extra.coerceAtLeast(extraMinutes))
            }

            SleepTimerMode.OFF -> startMinutes(extraMinutes)
        }
    }

    fun cancel() {
        mode = SleepTimerMode.OFF
        endAt = 0L
        totalMs = 0L
        minutes = 0
        videoCount = 0
        videosLeft = 0
        clearWarning()
    }

    internal fun showWarning() {
        warning = true
        warned = true
    }

    internal fun dismissWarning() {
        warning = false
    }

    internal fun clearWarning() {
        warning = false
        warned = false
    }

    /** One file finished while counting items; the caller decides about pausing. */
    internal fun onVideoFinished() {
        // The last file pauses at its end and is reported by the play-when-ready
        // listener, so only intermediate files are counted here.
        if (mode == SleepTimerMode.VIDEOS && videosLeft > 1) {
            videosLeft = (videosLeft - 1).coerceAtLeast(1)
        }
    }
}

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
