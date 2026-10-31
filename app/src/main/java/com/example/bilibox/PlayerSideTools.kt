package com.example.bilibox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerTouchSurface(
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleControls by rememberUpdatedState(onToggleControls)
    val togglePlayback by rememberUpdatedState(onTogglePlayback)
    Box(modifier.pointerInput(locked) {
        detectTapGestures(
            onTap = { toggleControls() },
            onDoubleTap = { if (locked) toggleControls() else togglePlayback() }
        )
    })
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
