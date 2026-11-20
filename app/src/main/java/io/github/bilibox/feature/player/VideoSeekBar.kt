package io.github.bilibox.feature.player

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.bilibox.core.ui.component.playback.SeekThumb
import io.github.bilibox.core.ui.theme.LocalSeekThumbStyle
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun rememberSeekBarPosition(player: Player, scrubbing: Boolean): LongState {
    val position = remember(player) { mutableLongStateOf(player.currentPosition) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                position.longValue = player.currentPosition
                playing = player.isPlaying
            }
        }
        player.addListener(listener)
        playing = player.isPlaying
        onDispose { player.removeListener(listener) }
    }
    val advancing = playing && !scrubbing
    LaunchedEffect(player, playing, scrubbing) {
        position.longValue = player.currentPosition
        while (advancing) {
            withInfiniteAnimationFrameNanos { position.longValue = player.currentPosition }
        }
    }
    return position
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: Float,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
    scrubbing: Boolean = false,
    preview: PlayerSeekPreviewController? = null,
    onSeekCanceled: () -> Unit = {},
    bookmarkPositions: List<Long> = emptyList(),
    isPlaying: Boolean = false,
) {
    // Keep the visual layout compact; Compose still expands the touch target.
    val accent = OnlinePink
    val thumbStyle = LocalSeekThumbStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val cancel by rememberUpdatedState(onSeekCanceled)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is DragInteraction.Cancel) cancel() }
    }
    DisposableEffect(preview) { onDispose { cancel() } }
    val previewPosition = if (scrubbing) position.toLong().coerceIn(0L, (duration - 1L).coerceAtLeast(0L)) else null
    LaunchedEffect(preview, previewPosition) {
        if (previewPosition != null) preview?.request(previewPosition)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Box(modifier.height(24.dp)) {
            Slider(
                value = position.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "播放进度" },
                interactionSource = interactionSource,
                thumb = {
                    SeekThumb(
                        style = thumbStyle,
                        accent = accent,
                        modifier = Modifier.testTag("player-seek-thumb-${thumbStyle.name}"),
                        animate = isPlaying && !scrubbing,
                    )
                },
                track = { sliderState ->
                    Canvas(Modifier.fillMaxWidth().height(2.5.dp).testTag("player-seek-track")) {
                        val played = (sliderState.value / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val buffered = (bufferedPosition.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val start = Offset(0f, center.y)
                        drawLine(
                            Color.White.copy(alpha = .3f),
                            start,
                            Offset(size.width, center.y),
                            size.height,
                            StrokeCap.Round
                        )
                        if (buffered > 0f) {
                            drawLine(
                                Color.White.copy(alpha = .45f),
                                start,
                                Offset(size.width * buffered, center.y),
                                size.height,
                                StrokeCap.Round
                            )
                        }
                        if (played > 0f) {
                            drawLine(
                                accent,
                                start,
                                Offset(size.width * played, center.y),
                                size.height,
                                StrokeCap.Round
                            )
                        }
                        if (duration > 0L) {
                            bookmarkPositions.forEach { markedAt ->
                                if (markedAt in 0L..duration) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.dp.toPx(),
                                        center = Offset(size.width * (markedAt.toDouble() / duration).toFloat(), center.y),
                                    )
                                }
                            }
                        }
                    }
                }
            )
            if (scrubbing && duration > 0L && preview != null) {
                PlayerSeekPreviewPopup(
                    preview = preview,
                    positionMs = position.toLong().coerceIn(0L, duration),
                    durationMs = duration,
                )
            }
        }
    }
}
