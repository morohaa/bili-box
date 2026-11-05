package io.github.bilibox.feature.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.data.player.LightsOffMode
import kotlinx.coroutines.delay

/*
 * 关灯模式的遮罩、提示和模式选择 Popup。
 *
 * 交互约定：
 * - 灯泡本身：单击退出关灯，长按打开模式菜单；
 * - 暗层其他区域：单击只重新显示提示，长按不响应，避免误触退出；
 * - 提示在短暂停留后淡出，暗层单击可再次唤出。
 */

internal const val LIGHTS_OFF_HINT_MS = 2_500L
internal val supportsLightsOffBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val LightsOffBulb = Color(0xFFFFD166)
private val LightsOffHint = Color(0xFFB8BEC8)
private val MenuCard = Color.White
private val MenuLine = Color(0xFFE3E5E7)
private val MenuSelected = Color(0xFFFFF1F5)

private val modeEntries = listOf(
    LightsOffModeEntry(LightsOffMode.BLACK, "纯黑关灯"),
    LightsOffModeEntry(LightsOffMode.GAUSSIAN, "高斯模糊"),
    LightsOffModeEntry(LightsOffMode.FROSTED, "毛玻璃"),
    LightsOffModeEntry(LightsOffMode.DIM, "轻微变暗"),
)

private data class LightsOffModeEntry(
    val mode: LightsOffMode,
    val label: String,
)

internal fun LightsOffMode.contentBlurRadius(): Dp = if (supportsLightsOffBlur && (this == LightsOffMode.GAUSSIAN || this == LightsOffMode.FROSTED)) {
    24.dp
} else {
    0.dp
}

internal fun LightsOffMode.effectiveMode(): LightsOffMode = if (this == LightsOffMode.GAUSSIAN && !supportsLightsOffBlur) LightsOffMode.FROSTED else this

private fun LightsOffMode.scrimColor(): Color = when (effectiveMode()) {
    LightsOffMode.BLACK -> Color(0xFF0B0D10)
    LightsOffMode.GAUSSIAN -> Color.Black.copy(alpha = .28f)
    LightsOffMode.FROSTED -> Color(0xFFE9EDF2).copy(alpha = .42f)
    LightsOffMode.DIM -> Color.Black.copy(alpha = .55f)
}

/** 灯泡下方的模式菜单，播放器操作栏和关灯遮罩共用。 */
@Composable
internal fun LightsOffModeMenu(
    expanded: Boolean,
    selected: LightsOffMode,
    onDismiss: () -> Unit,
    onSelect: (LightsOffMode) -> Unit,
    offset: DpOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(184.dp),
        offset = offset,
        shape = RoundedCornerShape(14.dp),
        containerColor = MenuCard,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MenuLine),
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 5.dp)) {
            modeEntries.forEach { entry ->
                val isSelected = entry.mode == selected
                Row(
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) MenuSelected else Color.Transparent)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            onSelect(entry.mode)
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightsOffModeSwatch(entry.mode)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        entry.label,
                        color = if (isSelected) OnlinePink else Ink,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        AppGlyphIcon(AppIcon.CHECK, OnlinePink, Modifier.size(13.dp), 2.1f)
                    }
                }
            }
            if (!supportsLightsOffBlur) {
                Text(
                    "高斯模糊将降级为毛玻璃",
                    color = OnlineMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun LightsOffModeSwatch(mode: LightsOffMode) {
    val brush: Brush = when (mode) {
        LightsOffMode.BLACK -> Brush.linearGradient(listOf(Color(0xFF0B0D10), Color(0xFF2B3038)))
        LightsOffMode.GAUSSIAN -> Brush.linearGradient(listOf(Color(0xFFD5D9DF), Color.White))
        LightsOffMode.FROSTED -> Brush.linearGradient(listOf(Color(0xE6FFFFFF), Color(0xFFDCE0E5)))
        LightsOffMode.DIM -> Brush.linearGradient(listOf(Color(0xFF8B9097), Color(0xFF5F646B)))
    }
    Box(
        Modifier.size(16.dp)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, MenuLine, CircleShape),
    )
}

/** 覆盖详情区的关灯层：提示会自动淡出，灯泡是唯一可退出/长按的入口。 */
@Composable
internal fun PlayerLightsOffOverlay(
    mode: LightsOffMode,
    onExit: () -> Unit,
    onSelectMode: (LightsOffMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var revealToken by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(true) }
    val bulbInteraction = remember { MutableInteractionSource() }
    val bulbPressed by bulbInteraction.collectIsPressedAsState()
    LaunchedEffect(revealToken, menuOpen, bulbPressed) {
        if (menuOpen || bulbPressed) return@LaunchedEffect
        hintVisible = true
        delay(LIGHTS_OFF_HINT_MS)
        hintVisible = false
    }
    val effectiveMode = mode.effectiveMode()
    val scrimColor by animateColorAsState(
        targetValue = mode.scrimColor(),
        animationSpec = tween(220),
        label = "关灯遮罩颜色",
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(scrimColor)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "显示关灯提示",
                    onLongClick = {},
                    onClick = { revealToken++ },
                ),
        )
        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(
                        if (effectiveMode == LightsOffMode.BLACK) {
                            Color.Transparent
                        } else {
                            Color.Black.copy(alpha = .62f)
                        },
                        RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 26.dp, vertical = 16.dp),
            ) {
                Box {
                    Box(
                        Modifier.size(56.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = bulbInteraction,
                                indication = null,
                                onClickLabel = "退出关灯",
                                role = Role.Button,
                                onLongClickLabel = "选择关灯模式",
                                onLongClick = { menuOpen = true },
                                onClick = onExit,
                            )
                            .semantics { contentDescription = "灯泡，轻点恢复，长按切换模式" },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppGlyphIcon(AppIcon.BULB, LightsOffBulb, Modifier.size(30.dp), 1.6f)
                    }
                    LightsOffModeMenu(
                        expanded = menuOpen,
                        selected = mode,
                        onDismiss = { menuOpen = false },
                        onSelect = onSelectMode,
                        offset = DpOffset((-64).dp, 8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "关灯模式",
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Text("轻点灯泡恢复", color = LightsOffHint, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text("长按灯泡切换模式", color = LightsOffHint, fontSize = 12.sp)
            }
        }
    }
}
