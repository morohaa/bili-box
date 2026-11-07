package io.github.bilibox.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlinePink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: Float,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the visual layout compact; Compose still expands the touch target.
    val accent = OnlinePink
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = duration > 0,
            modifier = modifier.height(24.dp).semantics { contentDescription = "播放进度" },
            thumb = { TelevisionSeekThumb() },
            track = { sliderState ->
                Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
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
                }
            }
        )
    }
}
