package io.github.bilibox.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
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
