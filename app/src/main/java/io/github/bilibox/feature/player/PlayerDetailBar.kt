package io.github.bilibox.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.data.player.LightsOffMode

/*
 * The operation bar between the video and the creator card.
 *
 * The left capsule switches detail panes; the right side mirrors the player
 * state with a danmaku toggle and the lights-off button. All controls share
 * the same grey capsule base, and only the active tab/badge carries colour.
 */

private val BarButtonBackground = Color(0xFFEAECEF)
private val BarInactiveText = Color(0xFF61666D)
private val BarActiveText = Color(0xFF18191C)
private val BarBadgeOff = Color(0xFFC9CCD0)
private val BarSourcePill = Color(0xFFF6F7F8)
private val BarSourceStroke = Color(0xFFE3E5E7)
private val DanmakuPillExpandedWidth = 116.dp
private val DanmakuPillCollapsedWidth = 30.dp
private val DanmakuPillEasing = CubicBezierEasing(.25f, .46f, .45f, .94f)

@Composable
internal fun PlayerDetailBar(
    tabs: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    sourcePill: Boolean,
    sourceLabel: String,
    danmakuEnabled: Boolean,
    onOpenDanmakuComposer: (() -> Unit)?,
    onToggleDanmaku: (() -> Unit)?,
    lightsOffMode: LightsOffMode,
    onToggleLights: (() -> Unit)?,
    onSelectLightsOffMode: ((LightsOffMode) -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(30.dp)
                .background(BarButtonBackground, RoundedCornerShape(15.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedTab
                val shape = RoundedCornerShape(13.dp)
                val textColor by animateColorAsState(
                    targetValue = if (selected) BarActiveText else BarInactiveText,
                    animationSpec = tween(160),
                    label = "详情标签文字颜色",
                )
                Box(
                    Modifier.fillMaxHeight()
                        .then(if (selected) Modifier.shadow(2.dp, shape) else Modifier)
                        .clip(shape)
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelectTab(index) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (onOpenDanmakuComposer != null && onToggleDanmaku != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DanmakuButton(danmakuEnabled, onOpenDanmakuComposer, onToggleDanmaku)
                if (onToggleLights != null && onSelectLightsOffMode != null) {
                    LightsButton(
                        mode = lightsOffMode,
                        onToggle = onToggleLights,
                        onSelectMode = onSelectLightsOffMode,
                    )
                }
            }
        } else if (sourcePill) {
            SourcePill(sourceLabel)
        } else if (sourceLabel.isNotBlank()) {
            Text(sourceLabel, color = OnlineMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DanmakuButton(
    enabled: Boolean,
    onOpenComposer: () -> Unit,
    onToggleDanmaku: () -> Unit,
) {
    // One container drives both states: the text capsule grows out of the
    // round toggle while its label fades in, and the icon always stays at the
    // right edge so the badge slides with the collapsing edge.
    val expansion by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(320, easing = DanmakuPillEasing),
        label = "弹幕胶囊展开",
    )
    val pillWidth = DanmakuPillCollapsedWidth +
        (DanmakuPillExpandedWidth - DanmakuPillCollapsedWidth) * expansion
    Box(Modifier.height(30.dp)) {
        Row(
            Modifier.height(30.dp).width(pillWidth)
                .clip(RoundedCornerShape(15.dp))
                .background(BarButtonBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onOpenComposer)
                    .then(
                        if (enabled) {
                            Modifier.semantics(mergeDescendants = true) { contentDescription = "发弹幕" }
                        } else {
                            Modifier.clearAndSetSemantics { }
                        }
                    )
                    .padding(start = 12.dp, end = 6.dp)
                    .alpha(expansion),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "点我发弹幕",
                    color = BarInactiveText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Box(
                Modifier.size(26.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onToggleDanmaku)
                    .semantics {
                        contentDescription = if (enabled) "关闭弹幕" else "开启弹幕"
                    },
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphIcon(AppIcon.DANMAKU, BarInactiveText, Modifier.size(16.dp), 1.8f)
            }
            Spacer(Modifier.width(2.dp))
        }
        DanmakuBadge(
            enabled = enabled,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 1.dp)
                .offset(x = 3.dp, y = 3.dp),
        )
    }
}

@Composable
private fun DanmakuBadge(enabled: Boolean, modifier: Modifier = Modifier) {
    val badgeColor by animateColorAsState(
        targetValue = if (enabled) OnlinePink else BarBadgeOff,
        animationSpec = tween(220),
        label = "弹幕角标颜色",
    )
    Box(
        modifier.size(12.dp)
            .background(badgeColor, CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = enabled,
            animationSpec = tween(180),
            label = "弹幕角标图标",
        ) { on ->
            Text(
                text = if (on) "✓" else "−",
                color = Color.White,
                fontSize = if (on) 7.sp else 9.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LightsButton(
    mode: LightsOffMode,
    onToggle: () -> Unit,
    onSelectMode: (LightsOffMode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(30.dp)
                .clip(CircleShape)
                .background(BarButtonBackground)
                .combinedClickable(
                    onClickLabel = "关灯",
                    role = Role.Button,
                    onLongClickLabel = "选择关灯模式",
                    onLongClick = { menuOpen = true },
                    onClick = onToggle,
                )
                .semantics { contentDescription = "关灯，长按选择模式" },
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.BULB, BarInactiveText, Modifier.size(16.dp), 1.8f)
        }
        LightsOffModeMenu(
            expanded = menuOpen,
            selected = mode,
            onDismiss = { menuOpen = false },
            onSelect = { selected ->
                menuOpen = false
                onSelectMode(selected)
            },
            offset = DpOffset((-77).dp, 8.dp),
        )
    }
}

@Composable
private fun SourcePill(label: String) {
    Row(
        Modifier.height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BarSourcePill)
            .border(1.dp, BarSourceStroke, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnlineMuted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Color.White)
                .border(1.dp, BarSourceStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.TV, OnlinePink, Modifier.size(13.dp), 1.7f)
        }
    }
}
