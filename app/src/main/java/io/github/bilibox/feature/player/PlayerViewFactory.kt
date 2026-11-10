package io.github.bilibox.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.bilibox.R
import kotlin.math.roundToInt

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun createTexturePlayerView(context: Context, player: ExoPlayer): PlayerView = (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
    this.player = player
    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
    keepScreenOn = true
}

@Composable
internal fun rememberPlayerVideoOffset(player: Player, verticalFullScreen: Boolean, adjustmentDp: Int): Dp {
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        player.addListener(listener)
        videoSize = player.videoSize
        onDispose { player.removeListener(listener) }
    }
    val portraitVideo = videoSize.height > videoSize.width * videoSize.pixelWidthHeightRatio
    return playerVideoOffsetDp(verticalFullScreen, portraitVideo, adjustmentDp).dp
}

@Composable
internal fun PlayerVideoView(
    player: ExoPlayer,
    listening: Boolean,
    mirrorEnabled: Boolean,
    verticalFullScreenProgress: () -> Float,
    adjustmentDp: Int,
    onViewReady: (PlayerView) -> Unit,
) {
    val fullScreenOffset = rememberPlayerVideoOffset(player, verticalFullScreen = true, adjustmentDp = adjustmentDp)
    AndroidView(
        factory = { context -> createTexturePlayerView(context, player).also(onViewReady) },
        update = {
            it.player = player
            it.keepScreenOn = !listening
            onViewReady(it)
        },
        modifier = Modifier.fillMaxSize()
            .offset { IntOffset(0, (fullScreenOffset.toPx() * verticalFullScreenProgress()).roundToInt()) }
            .testTag("player-video-view")
            .then(if (mirrorEnabled) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}
