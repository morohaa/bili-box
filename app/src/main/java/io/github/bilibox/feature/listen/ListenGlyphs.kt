package io.github.bilibox.feature.listen

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineText

@Composable
internal fun ListenBackdrop(cover: Bitmap?) {
    // A tiny copy stretched with bilinear filtering gives a cheap blur on every API level.
    val tiny = remember(cover) {
        cover?.let { runCatching { Bitmap.createScaledBitmap(it, 12, 8, true) }.getOrNull() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF15161B))) {
        if (tiny != null) {
            Image(
                tiny.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6B2D47), Color(0xFF15161B)))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x590A0A0E),
                        Color(0xBF0A0A0E),
                        Color(0xE60A0A0E)
                    )
                )
            )
        )
    }
}

@Composable
internal fun ListenCoverCard(cover: Bitmap?, playing: Boolean, online: Boolean, onRestoreVideo: () -> Unit) {
    val scale by animateFloatAsState(
        if (playing) 1f else .92f,
        spring(dampingRatio = .6f, stiffness = 300f),
        label = "听视频封面缩放"
    )
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).scale(scale)
            .shadow(20.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A2B33))
    ) {
        if (cover != null) {
            Image(
                cover.asImageBitmap(),
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            HeadphoneIcon(Color.White.copy(alpha = .35f), Modifier.align(Alignment.Center).size(48.dp))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .18f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(600f, 600f)
                )
            )
        )
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .92f))
                .clickable(role = Role.Button, onClick = onRestoreVideo)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VideoScreenIcon(OnlineText)
            Text("回到画面", color = OnlineText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun ListenRoundButton(
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    icon: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = icon
    )
}

@Composable
internal fun ListenPlayButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(66.dp).shadow(14.dp, CircleShape, spotColor = OnlinePink)
            .clip(CircleShape).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (playing) "暂停" else "播放" },
        contentAlignment = Alignment.Center
    ) {
        if (buffering) {
            CircularProgressIndicator(color = OnlinePink, strokeWidth = 2.dp, modifier = Modifier.size(62.dp))
        }
        Canvas(Modifier.size(28.dp)) {
            val u = size.width / 24f
            if (playing) {
                listOf(6f, 14.2f).forEach { x ->
                    drawRoundRect(OnlineText, Offset(x * u, 5f * u), Size(3.8f * u, 14f * u), CornerRadius(1.2f * u))
                }
            } else {
                drawPath(
                    Path().apply {
                        moveTo(8f * u, 5f * u)
                        lineTo(19.5f * u, 12f * u)
                        lineTo(8f * u, 19f * u)
                        close()
                    },
                    OnlineText
                )
            }
        }
    }
}

@Composable
internal fun ChevronDownGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawPath(
            Path().apply {
                moveTo(6f * u, 9f * u)
                lineTo(12f * u, 15f * u)
                lineTo(18f * u, 9f * u)
            },
            tint,
            style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
internal fun VideoScreenIcon(tint: Color) {
    Canvas(Modifier.size(13.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 外框矩形 - rect x="3" y="5" width="18" height="13" rx="2.5"
        drawRoundRect(
            tint,
            topLeft = Offset(3f * u, 5f * u),
            size = Size(18f * u, 13f * u),
            cornerRadius = CornerRadius(2.5f * u),
            style = stroke
        )

        // 底座线 - path d="M8 21h8"
        drawLine(
            tint,
            start = Offset(8f * u, 21f * u),
            end = Offset(16f * u, 21f * u),
            strokeWidth = 1.9f * u,
            cap = StrokeCap.Round
        )

        // 播放三角形（填充）- path d="M10.5 9.2v4.6l4-2.3z"
        drawPath(
            Path().apply {
                moveTo(10.5f * u, 9.2f * u)
                relativeLineTo(0f, 4.6f * u)
                relativeLineTo(4f * u, -2.3f * u)
                close()
            },
            tint
        )
    }
}

@Composable
internal fun TimerGlyph(tint: Color) {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(tint, 7.5f * u, Offset(12f * u, 13.5f * u), style = stroke)
        drawPath(
            Path().apply {
                moveTo(12f * u, 9.5f * u)
                lineTo(12f * u, 13.5f * u)
                lineTo(14.5f * u, 15.5f * u)
            },
            tint,
            style = stroke
        )
        drawLine(tint, Offset(9.5f * u, 2.5f * u), Offset(14.5f * u, 2.5f * u), 1.9f * u, StrokeCap.Round)
    }
}

/** Circular arrow with seconds inside, mirrored for rewind. */
@Composable
internal fun SkipGlyph(forward: Boolean, seconds: Int = 15) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 24f
            val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val start = if (forward) -60f else -120f
            val sweep = if (forward) 300f else -300f
            drawArc(Color.White, start, sweep, false, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), style = stroke)
            val tipX = if (forward) 16.5f else 7.5f
            val dir = if (forward) 1f else -1f
            drawPath(
                Path().apply {
                    moveTo((tipX - 2.6f * dir) * u, 1.4f * u)
                    lineTo((tipX + .4f * dir) * u, 4.2f * u)
                    lineTo((tipX - 3f * dir) * u, 6.4f * u)
                },
                Color.White,
                style = stroke
            )
        }
        Text("$seconds", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PrevGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        // 三角形向左 M18 6.2v11.6a.8.8 0 0 1-1.2.7L8.5 12.7a.8.8 0 0 1 0-1.4l8.3-5.8a.8.8 0 0 1 1.2.7z
        drawPath(
            Path().apply {
                moveTo(18f * u, 6.2f * u)
                lineTo(18f * u, 17.8f * u)
                cubicTo(18f * u, 18.2f * u, 17.6f * u, 18.4f * u, 17.3f * u, 18.2f * u)
                lineTo(8.5f * u, 12.7f * u)
                cubicTo(8.2f * u, 12.5f * u, 8.2f * u, 12.1f * u, 8.5f * u, 11.9f * u)
                lineTo(17.3f * u, 6.1f * u)
                cubicTo(17.6f * u, 5.9f * u, 18f * u, 6.1f * u, 18f * u, 6.4f * u)
                close()
            },
            tint
        )
        // 左侧竖条 rect x="5" y="5.5" width="2.4" height="13" rx="1"
        drawRoundRect(tint, Offset(5f * u, 5.5f * u), Size(2.4f * u, 13f * u), CornerRadius(1f * u))
    }
}

@Composable
internal fun NextGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawPath(
            Path().apply {
                moveTo(6f * u, 5.5f * u)
                lineTo(16f * u, 12f * u)
                lineTo(6f * u, 18.5f * u)
                close()
            },
            tint
        )
        drawRoundRect(tint, Offset(17f * u, 5.5f * u), Size(2.4f * u, 13f * u), CornerRadius(1f * u))
    }
}

@Composable
internal fun ToolButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            icon(if (active) Color(0xFFFFB3CA) else Color.White.copy(alpha = .8f))
        }
        Text(
            label,
            color = if (active) Color(0xFFFFB3CA) else Color.White.copy(alpha = .8f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun SpeedGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // path d="M4.5 18a8.5 8.5 0 1 1 15 0"
        drawArc(
            tint,
            180f,
            180f,
            false,
            topLeft = Offset(4.5f * u, 9.5f * u),
            size = Size(15f * u, 17f * u),
            style = stroke
        )
        // path d="M12 14l4-4.5"
        drawLine(tint, Offset(12f * u, 14f * u), Offset(16f * u, 9.5f * u), 1.9f * u, StrokeCap.Round)
        // circle cx="12" cy="14" r="1.3"
        drawCircle(tint, 1.3f * u, Offset(12f * u, 14f * u))
    }
}

@Composable
internal fun LoopListGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M17 2l4 4-4 4
        drawPath(
            Path().apply {
                moveTo(17f * u, 2f * u)
                lineTo(21f * u, 6f * u)
                lineTo(17f * u, 10f * u)
            },
            tint,
            style = stroke
        )
        // M3 11V9a3 3 0 0 1 3-3h15
        drawPath(
            Path().apply {
                moveTo(3f * u, 11f * u)
                lineTo(3f * u, 9f * u)
                arcTo(Rect(3f * u, 6f * u, 9f * u, 12f * u), 180f, 90f, false)
                lineTo(21f * u, 6f * u)
            },
            tint,
            style = stroke
        )
        // M7 22l-4-4 4-4
        drawPath(
            Path().apply {
                moveTo(7f * u, 22f * u)
                lineTo(3f * u, 18f * u)
                lineTo(7f * u, 14f * u)
            },
            tint,
            style = stroke
        )
        // M21 13v2a3 3 0 0 1-3 3H3
        drawPath(
            Path().apply {
                moveTo(21f * u, 13f * u)
                lineTo(21f * u, 15f * u)
                arcTo(Rect(15f * u, 12f * u, 21f * u, 18f * u), 0f, 90f, false)
                lineTo(3f * u, 18f * u)
            },
            tint,
            style = stroke
        )
    }
}

@Composable
internal fun LoopOneGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(
            Path().apply {
                moveTo(17f * u, 2f * u)
                lineTo(21f * u, 6f * u)
                lineTo(17f * u, 10f * u)
            },
            tint,
            style = stroke
        )
        drawPath(
            Path().apply {
                moveTo(3f * u, 11f * u)
                lineTo(3f * u, 9f * u)
                arcTo(Rect(3f * u, 6f * u, 9f * u, 12f * u), 180f, 90f, false)
                lineTo(21f * u, 6f * u)
            },
            tint,
            style = stroke
        )
        drawPath(
            Path().apply {
                moveTo(7f * u, 22f * u)
                lineTo(3f * u, 18f * u)
                lineTo(7f * u, 14f * u)
            },
            tint,
            style = stroke
        )
        drawPath(
            Path().apply {
                moveTo(21f * u, 13f * u)
                lineTo(21f * u, 15f * u)
                arcTo(Rect(15f * u, 12f * u, 21f * u, 18f * u), 0f, 90f, false)
                lineTo(3f * u, 18f * u)
            },
            tint,
            style = stroke
        )
        // "1" 数字 - M11 10.5l1.5-1v5
        val numStroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(
            Path().apply {
                moveTo(11f * u, 10.5f * u)
                lineTo(12.5f * u, 9.5f * u)
                lineTo(12.5f * u, 14.5f * u)
            },
            tint,
            style = numStroke
        )
    }
}

@Composable
internal fun ShuffleGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M16 3h5v5
        drawPath(
            Path().apply {
                moveTo(16f * u, 3f * u)
                lineTo(21f * u, 3f * u)
                lineTo(21f * u, 8f * u)
            },
            tint,
            style = stroke
        )
        // M4 20L21 3
        drawLine(tint, Offset(4f * u, 20f * u), Offset(21f * u, 3f * u), 1.9f * u, StrokeCap.Round)
        // M21 16v5h-5
        drawPath(
            Path().apply {
                moveTo(21f * u, 16f * u)
                lineTo(21f * u, 21f * u)
                lineTo(16f * u, 21f * u)
            },
            tint,
            style = stroke
        )
        // M15 15l6 6
        drawLine(tint, Offset(15f * u, 15f * u), Offset(21f * u, 21f * u), 1.9f * u, StrokeCap.Round)
        // M4 4l5 5
        drawLine(tint, Offset(4f * u, 4f * u), Offset(9f * u, 9f * u), 1.9f * u, StrokeCap.Round)
    }
}

@Composable
internal fun StopAfterGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        // 暂停符号 - 两个竖条
        drawRoundRect(tint, Offset(7f * u, 5f * u), Size(3f * u, 14f * u), CornerRadius(1f * u))
        drawRoundRect(tint, Offset(14f * u, 5f * u), Size(3f * u, 14f * u), CornerRadius(1f * u))
    }
}

@Composable
internal fun QueueGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M4 6h11 M4 12h11 M4 18h7
        drawLine(tint, Offset(4f * u, 6f * u), Offset(15f * u, 6f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(4f * u, 12f * u), Offset(15f * u, 12f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(4f * u, 18f * u), Offset(11f * u, 18f * u), 1.9f * u, StrokeCap.Round)
        // M18 14v6
        drawLine(tint, Offset(18f * u, 14f * u), Offset(18f * u, 20f * u), 1.9f * u, StrokeCap.Round)
        // circle cx="16.5" cy="19.5" r="1.8"
        drawCircle(tint, 1.8f * u, Offset(16.5f * u, 19.5f * u), style = stroke)
        // M18 14l3 1
        drawLine(tint, Offset(18f * u, 14f * u), Offset(21f * u, 15f * u), 1.9f * u, StrokeCap.Round)
    }
}

internal fun formatTimerBadge(remainingMs: Long): String {
    val mins = (remainingMs / 60_000).toInt()
    return if (mins > 0) "${mins}分钟" else "不到1分钟"
}

internal tailrec fun Context.findListenActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findListenActivity()
    else -> null
}
