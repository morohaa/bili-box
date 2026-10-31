package com.example.bilibox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.Window

internal enum class PlayerGestureSide { LEFT, RIGHT }

internal fun setPlayerWindowBrightness(window: Window, level: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = level.coerceIn(.02f, 1f)
    window.attributes = attributes
}

@Composable
internal fun PlayerTouchSurface(
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    verticalGesturesEnabled: Boolean = true,
    onVerticalGestureStart: (PlayerGestureSide) -> Unit = {},
    onVerticalSwipe: (PlayerGestureSide, Float) -> Unit = { _, _ -> },
    onVerticalGestureEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val toggleControls by rememberUpdatedState(onToggleControls)
    val togglePlayback by rememberUpdatedState(onTogglePlayback)
    val startVerticalGesture by rememberUpdatedState(onVerticalGestureStart)
    val verticalSwipe by rememberUpdatedState(onVerticalSwipe)
    val endVerticalGesture by rememberUpdatedState(onVerticalGestureEnd)
    val verticalDragModifier = if (verticalGesturesEnabled) Modifier.pointerInput(verticalGesturesEnabled) {
        var gestureSide: PlayerGestureSide? = null
        detectVerticalDragGestures(
            onDragStart = { position ->
                gestureSide = if (position.x < size.width / 2f) PlayerGestureSide.LEFT else PlayerGestureSide.RIGHT
                gestureSide?.let(startVerticalGesture)
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                val height = size.height.toFloat()
                if (height > 0f) gestureSide?.let { verticalSwipe(it, dragAmount / height) }
            },
            onDragEnd = {
                if (gestureSide != null) endVerticalGesture()
                gestureSide = null
            },
            onDragCancel = {
                if (gestureSide != null) endVerticalGesture()
                gestureSide = null
            }
        )
    } else Modifier
    Box(modifier.then(verticalDragModifier).pointerInput(locked) {
        detectTapGestures(
            onTap = { toggleControls() },
            onDoubleTap = { if (locked) toggleControls() else togglePlayback() }
        )
    })
}

@Composable
internal fun PlayerGestureFeedback(
    side: PlayerGestureSide,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val percentage = (level.coerceIn(0f, 1f) * 100).toInt()
    Row(
        modifier.width(200.dp).height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = .36f))
            .semantics {
                contentDescription = if (side == PlayerGestureSide.LEFT) "亮度调节" else "音量调节"
                stateDescription = "$percentage%"
            }
            .padding(horizontal = 22.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(36.dp)) {
            val scale = size.minDimension / 32f
            val white = Color.White
            val stroke = Stroke(width = 2.2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (side == PlayerGestureSide.LEFT) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(white, radius = 5.5f * scale, center = center, style = stroke)
                for (angle in 0 until 8) {
                    val radians = Math.toRadians(angle * 45.0)
                    val inner = 10f * scale
                    val outer = 13f * scale
                    drawLine(
                        white,
                        Offset(center.x + kotlin.math.cos(radians).toFloat() * inner, center.y + kotlin.math.sin(radians).toFloat() * inner),
                        Offset(center.x + kotlin.math.cos(radians).toFloat() * outer, center.y + kotlin.math.sin(radians).toFloat() * outer),
                        strokeWidth = 2.2f * scale,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                drawPath(Path().apply {
                    moveTo(3f * scale, 12f * scale)
                    lineTo(9f * scale, 12f * scale)
                    lineTo(18f * scale, 5f * scale)
                    lineTo(18f * scale, 27f * scale)
                    lineTo(9f * scale, 20f * scale)
                    lineTo(3f * scale, 20f * scale)
                    close()
                }, white, style = stroke)
                drawPath(Path().apply {
                    moveTo(21f * scale, 11f * scale)
                    quadraticTo(27f * scale, 16f * scale, 21f * scale, 21f * scale)
                    moveTo(23f * scale, 7f * scale)
                    cubicTo(32f * scale, 12f * scale, 32f * scale, 20f * scale, 23f * scale, 25f * scale)
                }, white, style = stroke)
            }
        }
        Spacer(Modifier.width(20.dp))
        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .32f))) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFFF6699)))
        }
    }
}

@Composable
internal fun PlayerSideTools(
    locked: Boolean,
    screenshotEnabled: Boolean,
    onScreenshot: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!locked) {
            IconButton(
                onClick = onScreenshot,
                enabled = screenshotEnabled,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "截取视频画面" }
            ) { CameraIcon(if (screenshotEnabled) 1f else .4f) }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (locked) "解锁屏幕" else "锁定屏幕"
            }
        ) { ScreenLockIcon(locked) }
    }
}

@Composable
private fun CameraIcon(alpha: Float) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val color = Color.White.copy(alpha = alpha)
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply {
            moveTo(5*u, 7*u)
            lineTo(8*u, 7*u); lineTo(10*u, 4*u); lineTo(14*u, 4*u); lineTo(16*u, 7*u)
            lineTo(19*u, 7*u); quadraticTo(21*u, 7*u, 21*u, 9*u)
            lineTo(21*u, 18*u); quadraticTo(21*u, 20*u, 19*u, 20*u)
            lineTo(5*u, 20*u); quadraticTo(3*u, 20*u, 3*u, 18*u)
            lineTo(3*u, 9*u); quadraticTo(3*u, 7*u, 5*u, 7*u); close()
        }, color, style = stroke)
        drawCircle(color, 3.2f*u, Offset(12*u, 13*u), style = stroke)
    }
}

@Composable
private fun ScreenLockIcon(locked: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(Color.White, Offset(4*u, 10*u), Size(16*u, 11*u), CornerRadius(2.5f*u), style = stroke)
        drawPath(Path().apply {
            if (locked) {
                moveTo(8*u, 10*u); lineTo(8*u, 6*u)
                cubicTo(8*u, 2*u, 16*u, 2*u, 16*u, 6*u); lineTo(16*u, 10*u)
            } else {
                moveTo(12*u, 10*u); lineTo(12*u, 6*u)
                cubicTo(12*u, 2*u, 20*u, 2*u, 20*u, 6*u); lineTo(20*u, 8*u)
            }
        }, Color.White, style = stroke)
        drawCircle(Color.White, 1.5f*u, Offset(12*u, 15*u))
        drawLine(Color.White, Offset(12*u, 15*u), Offset(12*u, 18*u), 1.7f*u, StrokeCap.Round)
    }
}
