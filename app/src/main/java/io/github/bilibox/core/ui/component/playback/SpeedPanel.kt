package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.playback.PlaybackSpeedMax
import io.github.bilibox.core.playback.PlaybackSpeedMin
import io.github.bilibox.core.playback.PlaybackSpeedStep
import io.github.bilibox.core.playback.quantizePlaybackSpeed
import java.util.Locale
import kotlin.math.abs

/*
 * 播放倍速面板。
 *
 * 设计参考了「小电视在刻度轨道上奔跑」的方案：连续的 0.05x 刻度、蓝粉渐变的进度轨、
 * 跑动节奏与当前倍速绑定的小电视，以及一句随速度变化的心情文案。
 */

internal enum class SpeedPanelMode { Drawer, Sheet }

internal val SpeedBlue = Color(0xFF00AEEC)
internal val SpeedInk = Color(0xFF18191C)
internal val SpeedPinkDeep = Color(0xFFE8547F)
internal val SpeedPinkDark = Color(0xFFC9416B)
internal val SpeedScreen = Color(0xFFFDFCFB)

internal const val SpeedTickCount = 50

internal fun formatSpeedReadout(value: Float): String = String.format(Locale.US, "%.2f", value)

internal fun speedMood(value: Float): String = when {
    value < 0.6f -> "慢慢挪"
    value < 0.9f -> "溜达中"
    value < 1.1f -> "散步中"
    value < 1.4f -> "小跑中"
    value < 1.8f -> "快跑中"
    value < 2.5f -> "冲刺！"
    else -> "起飞！！"
}

@Composable
internal fun SpeedPanel(
    speed: Float,
    mode: SpeedPanelMode,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val current = quantizePlaybackSpeed(speed)
    val haptics = LocalHapticFeedback.current
    var lastEmitted by remember { mutableFloatStateOf(current) }
    LaunchedEffect(current) {
        if (abs(current - lastEmitted) > 0.0005f) lastEmitted = current
    }

    fun emit(value: Float) {
        val next = quantizePlaybackSpeed(value)
        if (abs(next - lastEmitted) < 0.0005f) return
        lastEmitted = next
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSpeedChange(next)
    }

    // The drawer is edge-to-edge: no outer margins, square corners on the
    // screen edges. Only the modal sheet keeps its rounded top.
    val shape = if (mode == SpeedPanelMode.Sheet) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    val panelBrush = Brush.verticalGradient(
        listOf(Color(0xF91C1E29), Color(0xFC0C0D13))
    )
    val scrimSource = remember { MutableInteractionSource() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = (maxWidth * 0.50f).coerceIn(340.dp, 440.dp)
        // Short landscape windows (and split-screen) get a tighter rhythm so
        // the header and the presets always fit without scrolling.
        val compact = maxHeight < 340.dp
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = if (mode == SpeedPanelMode.Sheet) .46f else .30f))
                .clickable(
                    interactionSource = scrimSource,
                    indication = null,
                    onClick = onDismiss
                )
                .semantics { contentDescription = "关闭倍速面板" }
        )
        val placement = when (mode) {
            SpeedPanelMode.Drawer ->
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth)

            SpeedPanelMode.Sheet ->
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
        }
        Column(
            placement
                .clip(shape)
                .background(panelBrush)
                .then(
                    if (mode == SpeedPanelMode.Drawer) {
                        // A hairline instead of a full border, so the panel
                        // separates from the video without drawing a seam
                        // against the top / right / bottom screen edges.
                        Modifier.drawBehind {
                            drawLine(
                                color = Color.White.copy(alpha = .10f),
                                start = Offset.Zero,
                                end = Offset(0f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.Center
        ) {
            if (mode == SpeedPanelMode.Sheet) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 9.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = .18f), CircleShape)
                )
            }
            SpeedPanelContent(
                speed = current,
                compact = compact,
                onDismiss = onDismiss,
                onScrub = { fraction ->
                    emit(PlaybackSpeedMin + fraction * (PlaybackSpeedMax - PlaybackSpeedMin))
                },
                onStep = { direction -> emit(current + direction * PlaybackSpeedStep) },
                onPreset = { value ->
                    emit(value)
                    // A modal sheet hands the stage back to the video once the
                    // user has made a choice; the side drawer is a live control
                    // and stays open so the runner can be watched.
                    if (mode == SpeedPanelMode.Sheet) onDismiss()
                },
            )
        }
    }
}
