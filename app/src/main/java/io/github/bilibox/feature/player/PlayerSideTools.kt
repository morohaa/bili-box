package io.github.bilibox.feature.player

import android.content.res.Configuration
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatSpeedBadge
import kotlin.math.roundToInt

internal enum class PlayerGestureSide { LEFT, RIGHT }

internal fun setPlayerWindowBrightness(window: Window, level: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = level.coerceIn(.02f, 1f)
    window.attributes = attributes
}

/** Shown while the screen is held down for temporary 2x playback. */
@Composable
internal fun PlayerSpeedBoostIndicator(
    active: Boolean,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(130)) +
            scaleIn(initialScale = .88f, animationSpec = tween(180)),
        exit = fadeOut(tween(160)) +
            scaleOut(targetScale = .92f, animationSpec = tween(160)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = .52f))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp)
                .semantics {
                    contentDescription = "长按倍速播放"
                    stateDescription = "${formatSpeedBadge(speed)} 倍速播放中"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FastForwardGlyph(Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                formatSpeedBadge(speed),
                color = OnlinePink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "倍速播放中",
                color = Color.White.copy(alpha = .94f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FastForwardGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.width / 18f
        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(1.5f, 8.5f).forEach { startX ->
            drawPath(
                Path().apply {
                    moveTo(startX * unit, 4f * unit)
                    lineTo((startX + 6f) * unit, 9f * unit)
                    lineTo(startX * unit, 14f * unit)
                },
                Color.White,
                style = stroke,
            )
        }
    }
}

@Composable
internal fun PlayerGestureFeedback(
    side: PlayerGestureSide,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val safeLevel = level.coerceIn(0f, 1f)
    val percentage = (safeLevel * 100f).roundToInt()
    val muted = side == PlayerGestureSide.RIGHT && safeLevel < .005f
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val pillShape = RoundedCornerShape(percent = 50)

    val pillWidth = if (compact) 200.dp else 240.dp
    val pillHeight = if (compact) 48.dp else 56.dp
    val horizontalPadding = if (compact) 18.dp else 22.dp
    val iconSize = if (compact) 22.dp else 24.dp
    val iconGap = if (compact) 7.dp else 8.dp
    val trackHeight = if (compact) 3.5.dp else 4.dp
    val textGap = if (compact) 10.dp else 12.dp
    val percentSize = if (compact) 14.sp else 16.sp
    val percentMinWidth = if (compact) 36.dp else 42.dp
    val shadowElevation = if (compact) 12.dp else 14.dp

    Row(
        modifier.width(pillWidth).height(pillHeight)
            .shadow(shadowElevation, pillShape)
            .clip(pillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF2202125),
                        Color(0xF6111215),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = .06f), pillShape)
            .semantics {
                contentDescription = if (side == PlayerGestureSide.LEFT) "亮度调节" else "音量调节"
                stateDescription = "$percentage%"
            }
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerGestureGlyph(
            side = side,
            muted = muted,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(iconGap))
        Box(
            Modifier.weight(1f).height(trackHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = .22f))
        ) {
            Box(
                Modifier.fillMaxWidth(safeLevel).fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(OnlinePink)
            )
        }
        Spacer(Modifier.width(textGap))
        Text(
            text = "$percentage%",
            color = Color.White,
            fontSize = percentSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = .1.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = percentMinWidth),
        )
    }
}

@Composable
private fun PlayerGestureGlyph(
    side: PlayerGestureSide,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val white = Color.White
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (side == PlayerGestureSide.LEFT) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(white, radius = 4.2f * u, center = center, style = stroke)
            for (angle in 0 until 8) {
                val radians = Math.toRadians(angle * 45.0)
                val cos = kotlin.math.cos(radians).toFloat()
                val sin = kotlin.math.sin(radians).toFloat()
                drawLine(
                    white,
                    Offset(center.x + cos * 7.8f * u, center.y + sin * 7.8f * u),
                    Offset(center.x + cos * 9.8f * u, center.y + sin * 9.8f * u),
                    strokeWidth = 1.9f * u,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            drawPath(
                Path().apply {
                    moveTo(11f * u, 4.8f * u)
                    lineTo(6.6f * u, 8.6f * u)
                    lineTo(3.4f * u, 8.6f * u)
                    lineTo(3.4f * u, 15.4f * u)
                    lineTo(6.6f * u, 15.4f * u)
                    lineTo(11f * u, 19.2f * u)
                    close()
                },
                white,
                style = stroke,
            )
            if (muted) {
                drawLine(
                    white,
                    Offset(15.4f * u, 9.4f * u),
                    Offset(20.6f * u, 14.6f * u),
                    1.9f * u,
                    StrokeCap.Round,
                )
                drawLine(
                    white,
                    Offset(20.6f * u, 9.4f * u),
                    Offset(15.4f * u, 14.6f * u),
                    1.9f * u,
                    StrokeCap.Round,
                )
            } else {
                drawPath(
                    Path().apply {
                        moveTo(15.1f * u, 9.2f * u)
                        cubicTo(17.2f * u, 10.6f * u, 17.2f * u, 13.4f * u, 15.1f * u, 14.8f * u)
                    },
                    white,
                    style = stroke,
                )
                drawPath(
                    Path().apply {
                        moveTo(18f * u, 6.5f * u)
                        cubicTo(22.2f * u, 10.2f * u, 22.2f * u, 13.8f * u, 18f * u, 17.5f * u)
                    },
                    white,
                    style = stroke,
                )
            }
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
        drawPath(
            Path().apply {
                moveTo(5 * u, 7 * u)
                lineTo(8 * u, 7 * u)
                lineTo(10 * u, 4 * u)
                lineTo(14 * u, 4 * u)
                lineTo(16 * u, 7 * u)
                lineTo(19 * u, 7 * u)
                quadraticTo(21 * u, 7 * u, 21 * u, 9 * u)
                lineTo(21 * u, 18 * u)
                quadraticTo(21 * u, 20 * u, 19 * u, 20 * u)
                lineTo(5 * u, 20 * u)
                quadraticTo(3 * u, 20 * u, 3 * u, 18 * u)
                lineTo(3 * u, 9 * u)
                quadraticTo(3 * u, 7 * u, 5 * u, 7 * u)
                close()
            },
            color,
            style = stroke
        )
        drawCircle(color, 3.2f * u, Offset(12 * u, 13 * u), style = stroke)
    }
}

@Composable
private fun ScreenLockIcon(locked: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(Color.White, Offset(4 * u, 10 * u), Size(16 * u, 11 * u), CornerRadius(2.5f * u), style = stroke)
        drawPath(
            Path().apply {
                if (locked) {
                    moveTo(8 * u, 10 * u)
                    lineTo(8 * u, 6 * u)
                    cubicTo(8 * u, 2 * u, 16 * u, 2 * u, 16 * u, 6 * u)
                    lineTo(16 * u, 10 * u)
                } else {
                    moveTo(12 * u, 10 * u)
                    lineTo(12 * u, 6 * u)
                    cubicTo(12 * u, 2 * u, 20 * u, 2 * u, 20 * u, 6 * u)
                    lineTo(20 * u, 8 * u)
                }
            },
            Color.White,
            style = stroke
        )
        drawCircle(Color.White, 1.5f * u, Offset(12 * u, 15 * u))
        drawLine(Color.White, Offset(12 * u, 15 * u), Offset(12 * u, 18 * u), 1.7f * u, StrokeCap.Round)
    }
}
