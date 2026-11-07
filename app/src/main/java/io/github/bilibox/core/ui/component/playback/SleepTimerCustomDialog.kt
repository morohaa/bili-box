package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlinePinkSoft
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SleepCustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hours by remember(initialMinutes) { mutableIntStateOf((initialMinutes / 60).coerceIn(0, 24)) }
    var minutes by remember(initialMinutes) {
        mutableIntStateOf(if (initialMinutes >= 24 * 60) 0 else (initialMinutes % 60).coerceIn(0, 59))
    }
    val totalMinutes = hours * 60 + minutes
    val shape = RoundedCornerShape(18.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .heightIn(max = maxHeight * .86f)
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SleepTvGlyph(SleepBlue, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "自定义时长",
                        color = SleepInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(SleepBlueSoft)
                            .border(1.dp, SleepBlue.copy(alpha = .2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("最长 24 小时", color = SleepBlue, fontSize = 10.sp)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        String.format(Locale.getDefault(), "%02d", hours),
                        color = SleepBlue,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " 时",
                        color = SleepMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 7.dp),
                    )
                    Text(
                        ":",
                        color = Color(0xFFD8DBDE),
                        fontSize = 28.sp,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    Text(
                        String.format(Locale.getDefault(), "%02d", minutes),
                        color = OnlinePink,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " 分",
                        color = SleepMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 7.dp),
                    )
                }

                val previewEnd = formatClock(
                    System.currentTimeMillis() + totalMinutes.coerceAtLeast(1) * 60_000L
                )
                Text(
                    "预计结束于 $previewEnd",
                    color = SleepMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
                )

                SleepDurationSlider(
                    value = hours,
                    max = 24,
                    bubble = "$hours 小时",
                    fill = SleepBlue,
                    bubbleColor = SleepBlue,
                    bubbleBackground = SleepBlueSoft,
                ) { value ->
                    hours = value
                    if (value >= 24) minutes = 0
                }
                SleepDurationSlider(
                    value = minutes,
                    max = 59,
                    bubble = "$minutes 分钟",
                    fill = OnlinePink,
                    bubbleColor = SleepPinkDeep,
                    bubbleBackground = OnlinePinkSoft,
                    enabled = hours < 24,
                ) { minutes = it }

                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DialogActionButton(
                        text = "取消",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = "确定",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onConfirm(totalMinutes.coerceAtLeast(1)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepDurationSlider(
    value: Int,
    max: Int,
    bubble: String,
    fill: Color,
    bubbleColor: Color,
    bubbleBackground: Color,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fraction = value.toFloat() / max
            val thumb = 16.dp
            val bubbleWidth = 64.dp
            val maxOffset = (maxWidth - bubbleWidth).coerceAtLeast(0.dp)
            val x = (thumb / 2 + (maxWidth - thumb) * fraction - bubbleWidth / 2)
                .coerceIn(0.dp, maxOffset)
            Box(
                Modifier.offset(x = x).width(bubbleWidth)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (enabled) bubbleBackground else SleepField)
                    .border(
                        1.dp,
                        if (enabled) bubbleColor.copy(alpha = .18f) else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    bubble,
                    color = if (enabled) bubbleColor else SleepMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, max)) },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = if (enabled) fill else SleepTrack,
                inactiveTrackColor = SleepTrack,
                disabledActiveTrackColor = SleepTrack,
                disabledInactiveTrackColor = SleepTrack,
                disabledThumbColor = Color.White,
            ),
            thumb = {
                Box(
                    Modifier.size(16.dp).shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0x14000000), CircleShape)
                )
            },
            track = { sliderState ->
                val fraction = (sliderState.value / max.toFloat()).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    drawLine(
                        SleepTrack,
                        Offset(0f, center.y),
                        Offset(size.width, center.y),
                        4.dp.toPx(),
                        StrokeCap.Round,
                    )
                    if (enabled) {
                        drawLine(
                            fill,
                            Offset(0f, center.y),
                            Offset(size.width * fraction, center.y),
                            4.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (primary) OnlinePink else SleepField)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.White else SleepSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SleepTvGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val stroke = side * .072f
        drawRoundRect(
            color,
            topLeft = Offset(side * .08f, side * .27f),
            size = Size(side * .84f, side * .55f),
            cornerRadius = CornerRadius(side * .14f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawLine(
            color,
            Offset(side * .34f, side * .1f),
            Offset(side * .47f, side * .27f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(side * .66f, side * .1f),
            Offset(side * .53f, side * .27f),
            stroke,
            StrokeCap.Round,
        )
        drawCircle(color, side * .055f, Offset(side * .36f, side * .55f))
        drawCircle(color, side * .055f, Offset(side * .64f, side * .55f))
    }
}
