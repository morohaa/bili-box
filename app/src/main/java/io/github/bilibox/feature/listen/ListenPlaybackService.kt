package io.github.bilibox.feature.listen

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps 听视频 alive in the background: while a player is attached through
 * [ListenPlaybackBridge] this service exposes it as a MediaSession, so media3
 * posts the media notification / lock-screen controls and promotes the service
 * to foreground while audio is playing.
 *
 * The ExoPlayer itself stays owned by the player screen; this service never
 * creates or releases players.
 */
class ListenPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ListenPlaybackBridge.currentPlayer
        if (player == null) {
            // Listening ended before the service finished starting.
            stopSelf()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val builder = MediaSession.Builder(this, player).setId("bilibox-listen")
        if (launch != null) {
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        session = builder.build().also { addSession(it) }
        ListenPlaybackBridge.onServiceCreated(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    internal fun updatePlayer(player: Player) {
        session?.let { if (it.player !== player) it.player = player }
    }

    /** Drops the session (and with it the notification) before the service stops. */
    internal fun endSession() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The player lives in the activity; once the task is swiped away it is
        // released with the composition, so stop instead of leaving a dead notification.
        session?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        ListenPlaybackBridge.onServiceDestroyed(this)
        endSession()
        super.onDestroy()
    }
}

/**
 * Playlist hooks that must work without the UI: background auto-advance and the
 * notification / lock-screen "next" button call these directly on the main thread,
 * because Compose stops recomposing once the activity is stopped.
 */
internal interface ListenQueue {
    val hasNext: Boolean
    fun skipToNext()
}

/** Connects the composable-owned player to [ListenPlaybackService]. */
internal object ListenPlaybackBridge {
    private val main = Handler(Looper.getMainLooper())
    private var service: ListenPlaybackService? = null
    private var owner: Player? = null
    private var pendingStop: Runnable? = null
    private var queue: ListenQueue? = null

    /** The wrapped player the session should expose, or null when not listening. */
    var currentPlayer: Player? = null
        private set

    /**
     * Starts (or updates) background playback for [player]. Calling again with
     * new metadata only swaps the session player and refreshes the notification.
     */
    fun attach(context: Context, player: Player, metadata: MediaMetadata, queue: ListenQueue? = null) {
        pendingStop?.let { main.removeCallbacks(it) }
        pendingStop = null
        owner = player
        this.queue = queue
        val wrapped = MetadataPlayer(player, metadata, queue)
        currentPlayer = wrapped
        val running = service
        if (running != null) {
            running.updatePlayer(wrapped)
        } else {
            // Attach always happens from a user action in the foreground, but
            // guard against the background-start restriction just in case.
            runCatching {
                val app = context.applicationContext
                app.startService(Intent(app, ListenPlaybackService::class.java))
            }
        }
    }

    /** True while [player] is the one exposed to the background session. */
    fun isAttached(player: Player): Boolean = owner === player

    /**
     * Refreshes the notification after a queue switched items without the UI.
     * Keeps the current artwork unless [artwork] is given; no-op when not listening.
     */
    fun updateMetadata(player: Player, title: String, subtitle: String, artwork: ByteArray? = null) {
        if (owner !== player) return
        val keepArtwork = artwork ?: currentPlayer?.mediaMetadata?.artworkData
        val wrapped = MetadataPlayer(player, buildListenMetadata(title, subtitle, keepArtwork), queue)
        currentPlayer = wrapped
        service?.updatePlayer(wrapped)
    }

    /**
     * Ends background playback for [player]. Deferred briefly so switching to
     * the next local file (old player disposed, new one attached) does not
     * tear the notification down and rebuild it.
     */
    fun detach(context: Context, player: Player) {
        if (owner !== player) return
        val appContext = context.applicationContext
        pendingStop?.let { main.removeCallbacks(it) }
        val stop = Runnable {
            pendingStop = null
            if (owner !== player) return@Runnable
            owner = null
            currentPlayer = null
            queue = null
            // Forget the instance right away: if listening restarts before
            // onDestroy runs, attach() must start a fresh service.
            service?.endSession()
            service = null
            appContext.stopService(Intent(appContext, ListenPlaybackService::class.java))
        }
        pendingStop = stop
        main.postDelayed(stop, 300)
    }

    internal fun onServiceCreated(created: ListenPlaybackService) {
        service = created
    }

    internal fun onServiceDestroyed(destroyed: ListenPlaybackService) {
        if (service === destroyed) service = null
    }
}

/**
 * Supplies title/UP/cover to the notification without touching the MediaItem,
 * and maps the session's "next" to [queue] (each file / part is its own item).
 */
@OptIn(UnstableApi::class)
private class MetadataPlayer(
    player: Player,
    private val metadata: MediaMetadata,
    private val queue: ListenQueue?,
) : ForwardingPlayer(player) {
    override fun getMediaMetadata(): MediaMetadata = metadata

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (queue?.hasNext != true) return base
        return base.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean = queue?.hasNext ?: super.hasNextMediaItem()

    override fun seekToNext() {
        val q = queue
        if (q != null) {
            if (q.hasNext) q.skipToNext()
        } else {
            super.seekToNext()
        }
    }

    override fun seekToNextMediaItem() {
        val q = queue
        if (q != null) {
            if (q.hasNext) q.skipToNext()
        } else {
            super.seekToNextMediaItem()
        }
    }
}

internal fun buildListenMetadata(title: String, subtitle: String, artwork: ByteArray?): MediaMetadata = MediaMetadata.Builder()
    .setTitle(title)
    .setDisplayTitle(title)
    .setArtist(subtitle)
    .apply {
        if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
    }
    .build()
