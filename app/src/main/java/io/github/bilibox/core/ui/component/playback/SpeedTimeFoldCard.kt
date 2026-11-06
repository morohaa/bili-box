package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.util.formatPlaybackTime
import kotlin.math.roundToInt

/*
 * 倍速面板的「剩余时长折算」卡片。
 *
 * 左侧是原剩余时长（划线），中间是倍速变化比例与箭头，右侧是折算后的时长。
 * 快放显示绿色节省比例，慢放显示红色增加比例，原速显示 0%。
 */

private val FoldCardSurface = Color(0xFF1E1E24)
private val FoldCardBorder = Color(0xFF33333A)
private val FoldMuted = Color(0xFF8C8C96)
private val FoldOriginalTime = Color(0xFFA0A0A5)
private val FoldSavingGreen = Color(0xFF4ADE80)
private val FoldArrow = Color(0xFF555560)
private val FoldSpeedCyan = Color(0xFF00E5FF)
private val FoldSlowRed = Color(0xFFFF6B6B)

internal fun foldedRemainingMs(remainingMs: Long, speed: Float): Long = (remainingMs / speed.coerceAtLeast(0.01f)).toLong()

internal fun savedTimePercent(speed: Float): Int = ((1f - 1f / speed.coerceAtLeast(0.01f)) * 100f).roundToInt().coerceIn(0, 100)

@Composable
internal fun SpeedTimeFoldCard(
    remainingMs: Long,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    val foldedMs = foldedRemainingMs(remainingMs, speed)
    val percent = savedTimePercent(speed)
    val speedLabel = "${formatSpeedReadout(speed)}x"
    val shape = RoundedCornerShape(12.dp)
    val saving = speed > 1.001f
    val slower = speed < 0.999f
    val slowerPercent = if (slower) ((1f / speed - 1f) * 100f).roundToInt() else 0
    val badgeText = when {
        saving -> "$percent%"
        slower -> "$slowerPercent%"
        else -> "0%"
    }
    val badgeColor = when {
        saving -> FoldSavingGreen
        slower -> FoldSlowRed
        else -> FoldMuted
    }
    val description = buildString {
        append("原剩余 ")
        append(formatPlaybackTime(remainingMs))
        append("，")
        append(speedLabel)
        append(" 后约 ")
        append(formatPlaybackTime(foldedMs))
        append("，")
        append(
            when {
                saving -> "节省 $percent%"
                slower -> "慢放 $slowerPercent%"
                else -> "保持原速"
            }
        )
    }

    Row(
        modifier.fillMaxWidth()
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(FoldCardSurface)
            .border(1.dp, FoldCardBorder, shape)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.widthIn(min = 72.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("原剩余", color = FoldMuted, fontSize = 12.sp, maxLines = 1)
            Text(
                formatPlaybackTime(remainingMs),
                color = FoldOriginalTime,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textDecoration = TextDecoration.LineThrough,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }

        Column(
            Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = .15f))
                    .padding(horizontal = 5.dp)
            ) {
                Text(
                    badgeText,
                    color = badgeColor,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            FoldArrowShape(Modifier.width(72.dp).height(12.dp))
        }

        Column(
            Modifier.widthIn(min = 88.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(color = FoldSpeedCyan, fontWeight = FontWeight.SemiBold)
                    ) {
                        append(speedLabel)
                    }
                    append(" 后约")
                },
                color = FoldMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                formatPlaybackTime(foldedMs),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(
                        color = FoldSpeedCyan.copy(alpha = .30f),
                        blurRadius = 10f,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun FoldArrowShape(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ux = size.width / 72f
        val uy = size.height / 12f
        val stroke = 2.5.dp.toPx()
        drawLine(
            color = FoldArrow,
            start = Offset(0f, 6f * uy),
            end = Offset(66f * ux, 6f * uy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = FoldArrow,
            start = Offset(58f * ux, 1f * uy),
            end = Offset(66f * ux, 6f * uy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = FoldArrow,
            start = Offset(58f * ux, 11f * uy),
            end = Offset(66f * ux, 6f * uy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
