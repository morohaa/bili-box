package io.github.bilibox.feature.player

import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.bilibox.R
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineText

@Composable
internal fun PlaybackIcon(playing: Boolean, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        if (playing) {
            listOf(6f, 14.2f).forEach { x ->
                drawRoundRect(
                    Color.White,
                    Offset(x * u, 5f * u),
                    androidx.compose.ui.geometry.Size(3.8f * u, 14f * u),
                    androidx.compose.ui.geometry.CornerRadius(1.2f * u)
                )
            }
        } else {
            drawPath(
                Path().apply {
                    moveTo(6.5f * u, 4.8f * u)
                    cubicTo(5.7f * u, 4.3f * u, 4.7f * u, 4.9f * u, 4.7f * u, 5.8f * u)
                    lineTo(4.7f * u, 18.2f * u)
                    cubicTo(4.7f * u, 19.1f * u, 5.7f * u, 19.7f * u, 6.5f * u, 19.2f * u)
                    lineTo(17.1f * u, 13f * u)
                    cubicTo(17.9f * u, 12.6f * u, 17.9f * u, 11.4f * u, 17.1f * u, 11f * u)
                    close()
                },
                Color.White
            )
        }
    }
}

@Composable
internal fun TelevisionSeekThumb() {
    val accent = OnlinePink
    Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(accent, Offset(7.5f * u, 1.5f * u), Offset(10.5f * u, 4.5f * u), 2f * u, StrokeCap.Round)
        drawLine(accent, Offset(16.5f * u, 1.5f * u), Offset(13.5f * u, 4.5f * u), 2f * u, StrokeCap.Round)
        drawRoundRect(
            Color.White,
            Offset(2.5f * u, 4f * u),
            androidx.compose.ui.geometry.Size(19f * u, 14f * u),
            androidx.compose.ui.geometry.CornerRadius(4.5f * u)
        )
        drawRoundRect(
            accent,
            Offset(2.5f * u, 4f * u),
            androidx.compose.ui.geometry.Size(19f * u, 14f * u),
            androidx.compose.ui.geometry.CornerRadius(4.5f * u),
            style = stroke
        )
        listOf(8.5f, 15.5f).forEach { x ->
            drawLine(OnlineText, Offset(x * u, 10f * u), Offset(x * u, 11.8f * u), 2f * u, StrokeCap.Round)
        }
    }
}

@Composable
internal fun FullscreenIcon(
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = Color.White.copy(alpha = .9f),
) {
    Icon(
        painter = painterResource(R.drawable.ic_player_fullscreen),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}
