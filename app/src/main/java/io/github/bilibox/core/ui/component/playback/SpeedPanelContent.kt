package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.playback.PlaybackSpeedBoost
import io.github.bilibox.core.playback.PlaybackSpeedMax
import io.github.bilibox.core.playback.PlaybackSpeedMin
import io.github.bilibox.core.playback.PlaybackSpeedPresets
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatSpeedBadge
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
internal fun SpeedPanelContent(
    speed: Float,
    compact: Boolean,
    onDismiss: () -> Unit,
    onScrub: (Float) -> Unit,
    onStep: (Int) -> Unit,
    onPreset: (Float) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = if (compact) 12.dp else 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedMoodChip(speedMood(speed))
            Spacer(Modifier.weight(1f))
            SpeedCloseButton(onDismiss)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatSpeedReadout(speed),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Text(
                    "x",
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            SpeedStepButton(
                symbol = "−",
                description = "减慢 0.05 倍速",
                enabled = speed > PlaybackSpeedMin + 0.001f,
                onStep = { onStep(-1) }
            )
            Spacer(Modifier.width(8.dp))
            SpeedStepButton(
                symbol = "+",
                description = "加快 0.05 倍速",
                enabled = speed < PlaybackSpeedMax - 0.001f,
                onStep = { onStep(1) }
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SpeedRail(
                speed = speed,
                trackHeight = if (compact) 112.dp else 132.dp,
                onScrubFraction = onScrub
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            SpeedPresetRow(speed = speed, onPreset = onPreset)
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Text(
                "长按画面 · 临时 ${formatSpeedBadge(PlaybackSpeedBoost)} 播放",
                color = Color.White.copy(alpha = .38f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
internal fun SpeedMoodChip(mood: String) {
    Row(
        Modifier.clip(CircleShape)
            .background(OnlinePink.copy(alpha = .13f))
            .border(1.dp, OnlinePink.copy(alpha = .22f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(4.dp).background(OnlinePink, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            mood,
            color = OnlinePink,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun SpeedCloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "收起倍速面板" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.7.dp.toPx()
            val color = Color.White.copy(alpha = .66f)
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

@Composable
internal fun SpeedStepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(380)
        while (true) {
            repeated = true
            step()
            delay(70)
        }
    }
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) .08f else .035f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) .16f else .05f),
                CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { if (!repeated) step() }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = Color.White.copy(alpha = if (enabled) .92f else .28f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
    }
}

@Composable
internal fun SpeedPresetRow(
    speed: Float,
    onPreset: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PlaybackSpeedPresets.forEach { option ->
            val selected = abs(speed - option) < 0.001f
            Box(
                Modifier.weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            OnlinePink.copy(alpha = .20f)
                        } else {
                            Color.White.copy(alpha = .06f)
                        }
                    )
                    .border(
                        1.dp,
                        if (selected) OnlinePink else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onPreset(option) }
                    )
                    .semantics {
                        contentDescription = "选择倍速 ${formatSpeedBadge(option)}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatSpeedBadge(option),
                    color = if (selected) OnlinePink else Color.White.copy(alpha = .74f),
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
