package io.github.bilibox.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
    portraitPanelVisible: Boolean,
    onViewReady: (PlayerView) -> Unit,
) {
    val panelProgress = animateFloatAsState(
        targetValue = if (portraitPanelVisible) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "竖屏弹层画面上移",
    )
    val fullScreenOffset = rememberPlayerVideoOffset(player, verticalFullScreen = true, adjustmentDp = adjustmentDp)
    // Keep the same PlayerView while fitting it between the top bar and the panel.
    // The controls still fill the fullscreen stage; only the video viewport moves.
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            AndroidView(
                factory = { context -> createTexturePlayerView(context, player).also(onViewReady) },
                update = {
                    it.player = player
                    it.keepScreenOn = !listening
                    onViewReady(it)
                },
                modifier = Modifier.fillMaxSize().testTag("player-video-view")
                    .then(if (mirrorEnabled) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val progress = panelProgress.value
        val compactHeight = (width * 9f / 16f).roundToInt().coerceAtMost(height)
        val topBarHeight = PortraitPlayerTopBarHeight.roundToPx().coerceAtMost(compactHeight)
        val viewportHeight = (height + (compactHeight - topBarHeight - height) * progress).roundToInt()
        val normalOffset = fullScreenOffset.toPx() * verticalFullScreenProgress()
        val top = (normalOffset * (1f - progress) + topBarHeight * progress).roundToInt()
        val video = measurables.single().measure(Constraints.fixed(width, viewportHeight))
        layout(width, height) { video.placeRelative(0, top) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}
