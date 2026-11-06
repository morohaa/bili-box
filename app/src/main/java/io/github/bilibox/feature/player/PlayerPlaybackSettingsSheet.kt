package io.github.bilibox.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatSpeedBadge
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * 播放器「更多」播放设置卡片。
 *
 * 卡片从视频画面下沿开始铺满剩余屏幕：上半部分是五个快捷入口，下半部分是
 * 播放偏好列表。卡片本身可以下拉收起，也可以通过遮罩、返回键关闭。
 */

internal enum class PlayerLoopMode {
    SINGLE_LOOP,
    LIST_LOOP,
    NONE,
}

internal data class PlayerSettingsState(
    val speed: Float,
    val autoPlayNext: Boolean,
    val loopMode: PlayerLoopMode,
    val backgroundAudio: Boolean,
    val qualityLabel: String,
    val subtitleEnabled: Boolean,
    val mirrorEnabled: Boolean,
    val cacheLabel: String = "",
)

internal class PlayerSettingsActions(
    val onDismiss: () -> Unit,
    val onSpeed: () -> Unit,
    val onWatchLater: () -> Unit,
    val onCache: () -> Unit,
    val onPictureInPicture: () -> Unit,
    val onSleepTimer: () -> Unit,
    val onAutoPlayNextChange: (Boolean) -> Unit,
    val onLoopModeChange: (PlayerLoopMode) -> Unit,
    val onBackgroundAudioChange: (Boolean) -> Unit,
    val onQuality: () -> Unit,
    val onDanmakuSettings: () -> Unit,
    val onSubtitleChange: (Boolean) -> Unit,
    val onMirrorChange: (Boolean) -> Unit,
    val onMoreSettings: () -> Unit,
)

private val SettingsSheetSurface = Color(0xFFF6F7F8)
private val SettingsCardSurface = Color.White
private val SettingsLine = Color(0xFFE7E8EA)
private val SettingsInk = Color(0xFF17191B)
private val SettingsSecondary = Color(0xFF5F6266)
private val SettingsMuted = Color(0xFF9499A0)
private val SettingsIdleTrack = Color(0xFFE1E4E6)
private val SettingsGrabber = Color(0xFFC8CBD0)
private val SettingsPink = OnlinePink
private val SettingsGlyphTint = Color(0xFF111315)

@Composable
internal fun PlayerPlaybackSettingsHost(
    visible: Boolean,
    topInset: Dp,
    state: PlayerSettingsState,
    qualityAvailable: Boolean,
    danmakuAvailable: Boolean,
    moreSettingsAvailable: Boolean,
    onDismiss: () -> Unit,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
    onQuality: () -> Unit,
    onDanmakuSettings: () -> Unit,
    onMoreSettings: () -> Unit,
    onBackgroundAudioChange: (Boolean) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onLoopModeChange: (PlayerLoopMode) -> Unit,
    onSubtitleChange: (Boolean) -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onCache: (() -> Unit)?,
    onUnavailable: (String) -> Unit,
) {
    fun dismissThen(action: () -> Unit) {
        onDismiss()
        action()
    }

    PlayerPlaybackSettingsSheet(
        visible = visible,
        topInset = topInset,
        state = state,
        actions = PlayerSettingsActions(
            onDismiss = onDismiss,
            onSpeed = { dismissThen(onSpeed) },
            onWatchLater = { dismissThen { onUnavailable("「稍后再看」暂未接入") } },
            onCache = {
                dismissThen {
                    if (onCache != null) onCache() else onUnavailable("当前视频暂不支持缓存")
                }
            },
            onPictureInPicture = { dismissThen { onUnavailable("小窗播放暂未支持") } },
            onSleepTimer = { dismissThen(onSleepTimer) },
            onAutoPlayNextChange = onAutoPlayNextChange,
            onLoopModeChange = onLoopModeChange,
            onBackgroundAudioChange = { enabled -> dismissThen { onBackgroundAudioChange(enabled) } },
            onQuality = {
                dismissThen {
                    if (qualityAvailable) onQuality() else onUnavailable("当前视频暂不支持切换画质")
                }
            },
            onDanmakuSettings = {
                dismissThen {
                    if (danmakuAvailable) onDanmakuSettings() else onUnavailable("本地视频没有弹幕")
                }
            },
            onSubtitleChange = onSubtitleChange,
            onMirrorChange = onMirrorChange,
            onMoreSettings = {
                dismissThen {
                    if (moreSettingsAvailable) onMoreSettings() else onUnavailable("更多播放设置暂未支持")
                }
            },
        ),
    )
}

@Composable
internal fun PlayerPlaybackSettingsSheet(
    visible: Boolean,
    topInset: Dp,
    state: PlayerSettingsState,
    actions: PlayerSettingsActions,
) {
    val scrim = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .42f))
                .clickable(
                    interactionSource = scrim,
                    indication = null,
                    onClick = actions.onDismiss,
                )
                .semantics { contentDescription = "关闭播放设置" }
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize().padding(top = topInset),
        enter = slideInVertically(
            animationSpec = tween(320, easing = CubicBezierEasing(.22f, .8f, .25f, 1f)),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ),
    ) {
        PlaybackSettingsCard(state, actions)
    }
}

@Composable
private fun PlaybackSettingsCard(
    state: PlayerSettingsState,
    actions: PlayerSettingsActions,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThreshold = with(density) { 72.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)

    Column(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .shadow(16.dp, shape)
            .clip(shape)
            .background(SettingsSheetSurface)
            .testTag("player-settings-sheet")
            .semantics { contentDescription = "播放设置" }
    ) {
        Box(
            Modifier.fillMaxWidth().height(29.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { actions.onDismiss() })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragOffset > dismissThreshold) {
                                actions.onDismiss()
                            } else {
                                scope.launch { dragOffset = 0f }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffset = 0f }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(50.dp).height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SettingsGrabber)
            )
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        ) {
            QuickActions(state, actions)
            Spacer(Modifier.height(16.dp))
            SettingsList(state, actions)
        }
    }
}

@Composable
private fun QuickActions(state: PlayerSettingsState, actions: PlayerSettingsActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(
            label = "倍速",
            description = "倍速 ${formatSpeedBadge(state.speed)}",
            modifier = Modifier.weight(1f),
            onClick = actions.onSpeed,
            glyph = {
                PlayerSpeedGlyph(state.speed, SettingsGlyphTint, Modifier.size(30.dp))
            },
        )
        QuickAction(
            label = "稍后再看",
            description = "稍后再看",
            modifier = Modifier.weight(1f),
            onClick = actions.onWatchLater,
        ) {
            QuickSettingIcon(PlayerSettingsIcon.REPLAY, strokeWidth = 24f)
        }
        QuickAction(
            label = "缓存",
            description = if (state.cacheLabel.isNotBlank()) state.cacheLabel else "缓存",
            modifier = Modifier.weight(1f),
            onClick = actions.onCache,
        ) {
            QuickSettingIcon(PlayerSettingsIcon.DOWNLOAD)
        }
        QuickAction(
            label = "小窗播放",
            description = "小窗播放",
            modifier = Modifier.weight(1f),
            onClick = actions.onPictureInPicture,
        ) {
            QuickSettingIcon(PlayerSettingsIcon.PIP, strokeWidth = 24f)
        }
        QuickAction(
            label = "定时关闭",
            description = "定时关闭",
            modifier = Modifier.weight(1f),
            onClick = actions.onSleepTimer,
        ) {
            QuickSettingIcon(PlayerSettingsIcon.TIMER, strokeWidth = 6f)
        }
    }
}

@Composable
private fun QuickSettingIcon(icon: PlayerSettingsIcon, strokeWidth: Float = 0f) {
    PlayerSettingsGlyph(icon, SettingsGlyphTint, Modifier.size(30.dp), strokeWidth)
}

@Composable
private fun RowSettingIcon(icon: PlayerSettingsIcon, enabled: Boolean = true) {
    val tint = if (enabled) SettingsGlyphTint else SettingsGlyphTint.copy(alpha = .35f)
    val strokeWidth = if (icon == PlayerSettingsIcon.MIRROR || icon == PlayerSettingsIcon.PLAYBACK_SETTINGS) {
        12f
    } else {
        0f
    }
    PlayerSettingsGlyph(icon, tint, Modifier.size(21.dp), strokeWidth)
}

@Composable
private fun QuickAction(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = .78f)),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = SettingsSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsList(state: PlayerSettingsState, actions: PlayerSettingsActions) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsCardSurface)
    ) {
        ToggleSettingRow(
            label = "自动连播",
            checked = state.autoPlayNext,
            icon = PlayerSettingsIcon.AUTOPLAY,
            onCheckedChange = actions.onAutoPlayNextChange,
        )
        SettingsDivider()
        LoopSettingRow(
            selected = state.loopMode,
            onSelect = actions.onLoopModeChange,
        )
        SettingsDivider()
        ToggleSettingRow(
            label = "后台听视频",
            checked = state.backgroundAudio,
            icon = PlayerSettingsIcon.HEADPHONES,
            onCheckedChange = actions.onBackgroundAudioChange,
        )
        SettingsDivider()
        ActionSettingRow(
            label = "画质",
            value = state.qualityLabel,
            icon = PlayerSettingsIcon.QUALITY,
            onClick = actions.onQuality,
        )
        SettingsDivider()
        ActionSettingRow(
            label = "弹幕设置",
            icon = PlayerSettingsIcon.DANMAKU,
            onClick = actions.onDanmakuSettings,
        )
        SettingsDivider()
        ToggleSettingRow(
            label = "字幕",
            checked = state.subtitleEnabled,
            icon = PlayerSettingsIcon.SUBTITLE,
            onCheckedChange = actions.onSubtitleChange,
        )
        SettingsDivider()
        ToggleSettingRow(
            label = "镜像翻转",
            checked = state.mirrorEnabled,
            icon = PlayerSettingsIcon.MIRROR,
            onCheckedChange = actions.onMirrorChange,
        )
        SettingsDivider()
        ActionSettingRow(
            label = "更多播放设置",
            icon = PlayerSettingsIcon.PLAYBACK_SETTINGS,
            onClick = actions.onMoreSettings,
        )
    }
}

@Composable
private fun ToggleSettingRow(
    label: String,
    checked: Boolean,
    icon: PlayerSettingsIcon,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowSettingIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = SettingsInk,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        SettingsSwitch(checked)
    }
}

@Composable
private fun LoopSettingRow(
    selected: PlayerLoopMode,
    onSelect: (PlayerLoopMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowSettingIcon(PlayerSettingsIcon.LOOP)
        Spacer(Modifier.width(12.dp))
        Text("循环播放", color = SettingsInk, fontSize = 15.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            PlayerLoopMode.entries.forEachIndexed { index, mode ->
                if (index > 0) {
                    Box(
                        Modifier.width(1.dp).height(12.dp)
                            .background(SettingsLine)
                    )
                }
                val active = mode == selected
                Text(
                    text = when (mode) {
                        PlayerLoopMode.SINGLE_LOOP -> "单集循环"
                        PlayerLoopMode.LIST_LOOP -> "列表循环"
                        PlayerLoopMode.NONE -> "不循环"
                    },
                    color = if (active) SettingsPink else Color(0xFF73777B),
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .selectable(
                            selected = active,
                            role = Role.RadioButton,
                            onClick = { onSelect(mode) },
                        )
                        .semantics {
                            contentDescription = when (mode) {
                                PlayerLoopMode.SINGLE_LOOP -> "单集循环"
                                PlayerLoopMode.LIST_LOOP -> "列表循环"
                                PlayerLoopMode.NONE -> "不循环"
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionSettingRow(
    label: String,
    icon: PlayerSettingsIcon,
    value: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) SettingsInk else SettingsInk.copy(alpha = .35f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowSettingIcon(icon, enabled)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = contentColor,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotBlank()) {
            Text(
                value,
                color = if (enabled) SettingsMuted else SettingsMuted.copy(alpha = .45f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(4.dp))
        AppGlyphIcon(
            AppIcon.CHEVRON_RIGHT,
            if (enabled) Color(0xFFB6BBC1) else Color(0xFFB6BBC1).copy(alpha = .4f),
            Modifier.size(14.dp),
            2f,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = SettingsLine,
        thickness = 1.dp,
    )
}

@Composable
private fun SettingsSwitch(checked: Boolean) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "播放设置开关滑块",
    )
    Box(
        Modifier.width(44.dp).height(24.dp)
            .clip(CircleShape)
            .background(if (checked) SettingsPink else SettingsIdleTrack)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier.offset(x = thumbOffset, y = 2.dp)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** 竖屏播放器的画质选择弹层，和横屏抽屉共用同一组清晰度数据。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQualitySheet(
    options: List<PlayerQualityOption>,
    selectedQuality: Int?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = .34f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 2.dp).width(36.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFFDADCE0))
            )
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp)
        ) {
            Text(
                "清晰度与音质",
                color = SettingsInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "切换后保持当前播放进度",
                color = SettingsMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
            )
            options.forEach { option ->
                val active = option.qn == selectedQuality
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color(0xFFFFF1F5) else Color(0xFFF6F7F8))
                        .clickable(role = Role.RadioButton) { onQualitySelected(option.qn) }
                        .semantics(mergeDescendants = true) { contentDescription = "选择清晰度 ${option.label}" }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.label,
                                color = if (active) SettingsPink else SettingsInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            option.badge?.let { badge ->
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(SettingsPink.copy(alpha = .16f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        badge,
                                        color = SettingsPink,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (option.description.isNotBlank()) {
                            Text(
                                option.description,
                                color = SettingsMuted,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (active) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(SettingsPink),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppGlyphIcon(AppIcon.CHECK, Color.White, Modifier.size(11.dp), 2.6f)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}
