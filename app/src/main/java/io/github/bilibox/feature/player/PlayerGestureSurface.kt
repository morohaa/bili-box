package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.util.formatPlaybackTime

@Composable
internal fun PlayerGestureSurface(
    player: ExoPlayer,
    mediaKey: Any?,
    duration: Long,
    scrub: PlayerScrubState,
    gestures: PlayerGestureController,
    locked: Boolean,
    speedBoostEnabled: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPositionChange: (Long) -> Unit,
) {
    val swipe = remember(player, mediaKey) { PlayerSwipeSeekState(scrub) }
    var seekable by remember(player, mediaKey) { mutableStateOf(player.isCurrentMediaItemSeekable) }
    DisposableEffect(player, mediaKey) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                seekable = player.isCurrentMediaItemSeekable
            }
        }
        player.addListener(listener)
        seekable = player.isCurrentMediaItemSeekable
        onDispose { player.removeListener(listener) }
    }
    val seekEnabled = !locked && duration > 0L && seekable
    DisposableEffect(swipe) { onDispose { swipe.cancel() } }
    LaunchedEffect(seekEnabled) { if (!seekEnabled) swipe.cancel() }
    PlayerTouchSurface(
        locked = locked,
        onToggleControls = onToggleControls,
        onTogglePlayback = onTogglePlayback,
        verticalGesturesEnabled = !locked,
        onVerticalGestureStart = { gestures.startVerticalGesture(it) },
        onVerticalSwipe = { side, delta -> gestures.adjustVerticalGesture(side, delta) },
        onVerticalGestureEnd = { gestures.endVerticalGesture() },
        speedBoostEnabled = speedBoostEnabled,
        onSpeedBoostStart = { gestures.startSpeedBoost() },
        onSpeedBoostEnd = { gestures.endSpeedBoost() },
        horizontalGesturesEnabled = seekEnabled,
        onHorizontalGestureStart = { swipe.start(player.currentPosition, duration) },
        onHorizontalSwipe = swipe::drag,
        onHorizontalGestureEnd = {
            swipe.finish(duration)?.let {
                player.seekTo(it)
                onPositionChange(it)
            }
        },
        onHorizontalGestureCancel = swipe::cancel,
        modifier = Modifier.fillMaxSize().testTag("player-touch-surface"),
    )
    if (swipe.active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val direction = if (scrub.position >= swipe.startPosition) "快进" else "后退"
            Text(
                "$direction  ${formatPlaybackTime(scrub.position.toLong())} / ${formatPlaybackTime(duration)}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.background(Color.Black.copy(alpha = .7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("player-swipe-seek-feedback")
                    .semantics { contentDescription = "滑动调节播放进度" },
            )
        }
    }
}
