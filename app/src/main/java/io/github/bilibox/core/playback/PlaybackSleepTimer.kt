package io.github.bilibox.core.playback

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil

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
