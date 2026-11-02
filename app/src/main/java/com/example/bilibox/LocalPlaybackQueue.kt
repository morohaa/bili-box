package com.example.bilibox

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Owns the local folder queue outside of Compose. One [player] is reused for
 * every file: switching (end of file, notification "next", picking a file) calls
 * setMediaItem directly, so 连播 keeps working while the screen is locked and
 * the UI is not recomposing. [onCurrentChanged] tells the UI to catch up.
 */
internal class LocalPlaybackQueue(
    private val context: Context,
    val player: ExoPlayer,
    private val progressStore: PlaybackProgressStore,
    initial: LocalVideo,
) : ListenQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null

    var playlist: List<LocalVideo> = emptyList()
    var onCurrentChanged: (LocalVideo) -> Unit = {}
    var mode: LocalQueueMode = LocalQueueMode.LIST_LOOP
        set(value) {
            field = value
            val repeat = if (value == LocalQueueMode.SINGLE_LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (player.repeatMode != repeat) player.repeatMode = repeat
        }

    /** File the player is actually on; can run ahead of the UI in the background. */
    var current: LocalVideo = initial
        private set

    /** Position the current file was opened at (the 续播 bar shows it). */
    var startPositionMs: Long = progressStore.load(initial.key, initial.durationMs)
        private set

    private var opened = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                save()
            }
        }
    }

    init {
        player.addListener(listener)
    }

    /** Saves the exact position of the file the player is on. */
    fun save() {
        // Ignore the empty/idle player during setup or a failed preparation.
        if (!opened || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) return
        progressStore.save(
            current.key,
            player.currentPosition,
            player.duration.takeIf { it > 0L } ?: current.durationMs,
            completed = player.playbackState == Player.STATE_ENDED
        )
    }

    /** Opens [video] unless the player is already on it (e.g. the queue got there first). */
    fun open(video: LocalVideo) {
        if (opened && video.uri == current.uri) return
        if (opened) save()
        current = video
        startPositionMs = progressStore.load(video.key, video.durationMs)
        opened = true
        player.setMediaItem(MediaItem.fromUri(video.uri), startPositionMs)
        player.prepare()
        player.playWhenReady = true
        refreshNotification()
    }

    /** Reloads the current file at the current position (重试). */
    fun retry() {
        val position = player.currentPosition.coerceAtLeast(0L)
        player.stop()
        player.setMediaItem(MediaItem.fromUri(current.uri), position)
        player.prepare()
        player.playWhenReady = true
    }

    private fun nextInList(): LocalVideo? {
        val index = playlist.indexOfFirst { it.uri == current.uri }
        return (playlist.getOrNull(index + 1) ?: playlist.firstOrNull())?.takeIf { it.uri != current.uri }
    }

    override val hasNext: Boolean
        get() {
            val index = playlist.indexOfFirst { it.uri == current.uri }
            return index >= 0 && index < playlist.lastIndex
        }

    /** Notification / listen-page "next": the next file in folder order. */
    override fun skipToNext() {
        val index = playlist.indexOfFirst { it.uri == current.uri }
        val next = playlist.getOrNull(index + 1) ?: return
        moveTo(next)
    }

    /** Picks [video] from the UI; same as [open] but also notifies the UI. */
    fun moveTo(video: LocalVideo) {
        open(video)
        onCurrentChanged(video)
    }

    private fun onEnded() {
        when (mode) {
            LocalQueueMode.LIST_LOOP -> nextInList()
            LocalQueueMode.SHUFFLE -> playlist.filter { it.uri != current.uri }.randomOrNull()
            else -> return
        }?.let(::moveTo) ?: run {
            // Single-file folder: start the same file again.
            player.seekTo(0L)
            player.playWhenReady = true
        }
    }

    /** While listening, keep the notification title and cover on the real file. */
    private fun refreshNotification() {
        if (!ListenPlaybackBridge.isAttached(player)) return
        val video = current
        val subtitle = localListenSubtitle(video)
        ListenPlaybackBridge.updateMetadata(player, video.name, subtitle, artwork = null)
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                loadVideoThumbnail(context, video.uri, video.key)?.let { bitmap ->
                    runCatching {
                        ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            out.toByteArray()
                        }
                    }.getOrNull()
                }
            } ?: return@launch
            if (current.uri == video.uri) {
                ListenPlaybackBridge.updateMetadata(player, video.name, subtitle, bytes)
            }
        }
    }

    fun release() {
        save()
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }
}

/** "Movies/动漫 · MP4" line under the title on the listen page and notification. */
internal fun localListenSubtitle(video: LocalVideo): String = listOf(
    video.relativeDirectory.ifBlank { video.bucketName },
    video.extension.uppercase(),
).filter { it.isNotBlank() }.joinToString(" · ")
