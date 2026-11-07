package io.github.bilibox.core.ui.component.playback

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.playback.PlaybackSleepTimer
import io.github.bilibox.core.playback.SleepTimerMode
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlinePinkSoft
import io.github.bilibox.core.util.formatPlaybackTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val SleepCardPink = Color(0xFFFFF0F4)
private val SleepCardLilac = Color(0xFFF6F1FF)
internal val SleepField = Color(0xFFF6F7F8)
private val SleepLine = Color(0xFFF0F1F2)
internal val SleepTrack = Color(0xFFE3E5E7)
private val SleepGrabber = Color(0xFFDADCE0)
internal val SleepInk = Color(0xFF18191C)
internal val SleepMuted = Color(0xFF9499A0)
internal val SleepSecondary = Color(0xFF61666D)
internal val SleepPinkDeep = Color(0xFFFF4D7D)
internal val SleepBlue = Color(0xFF00AEEC)
internal val SleepBlueSoft = Color(0xFFE6F7FF)

private val SleepPresets = listOf(15, 30, 45, 60, 90, 120)

/** "定时关闭：剩余 24:31" line on the listen page. */
@Composable
internal fun SleepTimerLabel(timer: PlaybackSleepTimer, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer.mode, timer.endAt) {
        while (isActive && timer.mode == SleepTimerMode.TIME) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val text = when (timer.mode) {
        SleepTimerMode.TIME ->
            "定时关闭：剩余 ${formatPlaybackTime((timer.endAt - now).coerceAtLeast(0L))}"

        SleepTimerMode.VIDEOS ->
            if (timer.videoCount <= 1) {
                "定时关闭：播完当前视频后暂停"
            } else {
                "定时关闭：再播 ${timer.videosLeft} 个视频后暂停"
            }

        SleepTimerMode.OFF -> ""
    }
    if (text.isNotBlank()) {
        Text(text, color = Color(0xFFFFB3CA), fontSize = 12.sp, modifier = modifier)
    }
}

/**
 * White sleep-timer panel matching the reference design.
 *
 * When [topInset] is set (the player), the card fills the stage from the video's
 * bottom edge to the screen bottom so the picture stays visible; when it is
 * null (the listen page) it behaves as a regular bottom sheet.
 */
@Composable
internal fun SleepTimerSheet(
    timer: PlaybackSleepTimer,
    remainingVideos: Int,
    currentRemainingMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    topInset: Dp? = null,
) {
    val scrim = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMaxHeight = maxHeight * .92f
        if (topInset == null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .46f))
                    .clickable(interactionSource = scrim, indication = null, onClick = onDismiss)
                    .semantics { contentDescription = "关闭定时关闭面板" }
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                SleepTimerPanel(
                    timer = timer,
                    remainingVideos = remainingVideos,
                    currentRemainingMs = currentRemainingMs,
                    durationMs = durationMs,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight),
                )
            }
        } else {
            Box(Modifier.fillMaxSize().padding(top = topInset)) {
                SleepTimerPanel(
                    timer = timer,
                    remainingVideos = remainingVideos,
                    currentRemainingMs = currentRemainingMs,
                    durationMs = durationMs,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SleepTimerPanel(
    timer: PlaybackSleepTimer,
    remainingVideos: Int,
    currentRemainingMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragThreshold = with(density) { 72.dp.toPx() }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var customOpen by remember { mutableStateOf(false) }
    var pickedVideos by remember { mutableIntStateOf(timer.videoCount.coerceAtLeast(2)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val active = timer.active
    val maxVideos = remainingVideos.coerceAtLeast(2)
    val clockMs = when (timer.mode) {
        SleepTimerMode.TIME -> (timer.endAt - now).coerceAtLeast(0L)
        SleepTimerMode.VIDEOS -> if (timer.videosLeft <= 1) currentRemainingMs.coerceAtLeast(0L) else 0L
        SleepTimerMode.OFF -> 0L
    }
    val stateTitle = when (timer.mode) {
        SleepTimerMode.TIME -> "剩余 ${formatPlaybackTime(clockMs)}"

        SleepTimerMode.VIDEOS ->
            if (timer.videoCount <= 1) {
                "播完当前视频后暂停"
            } else {
                "再播 ${timer.videosLeft} 个视频后暂停"
            }

        SleepTimerMode.OFF -> "未开启"
    }
    val stateSubtitle = when (timer.mode) {
        SleepTimerMode.TIME -> "结束于 ${formatClock(timer.endAt)}"
        SleepTimerMode.VIDEOS -> "含当前视频，按同文件夹顺序"
        SleepTimerMode.OFF -> "选择一个时长，或播完视频后暂停"
    }
    val multiSelected = timer.mode == SleepTimerMode.VIDEOS && timer.videoCount > 1
    val shownVideos = if (multiSelected) timer.videoCount else pickedVideos
    val isCustomTime = timer.mode == SleepTimerMode.TIME && timer.minutes !in SleepPresets

    Column(
        modifier
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Color.White)
            .testTag("player-sleep-timer-sheet")
            .semantics { contentDescription = "定时关闭面板" }
    ) {
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragOffset > dragThreshold) {
                                onDismiss()
                            } else {
                                scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                            }
                        },
                        onDragCancel = {
                            scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(SleepGrabber)
            )
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 22.dp)
        ) {
            SleepHeader(onDismiss)

            SleepStatusCard(
                active = active,
                timeMode = timer.mode == SleepTimerMode.TIME,
                title = stateTitle,
                subtitle = stateSubtitle,
                clockMs = clockMs,
                onExtend = { timer.extend(it) },
                onCancel = { timer.cancel() },
            )

            SleepSectionLabel("按时长", top = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepPresets.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { minutes ->
                            SleepPresetButton(
                                minutes = minutes,
                                selected = timer.mode == SleepTimerMode.TIME && timer.minutes == minutes,
                                modifier = Modifier.weight(1f),
                                onClick = { timer.startMinutes(minutes) },
                            )
                        }
                    }
                }
            }

            CustomDurationButton(
                customTime = isCustomTime,
                label = if (isCustomTime) "自定义 · ${formatCustomMinutes(timer.minutes)}" else "自定义",
                onClick = { customOpen = true },
            )

            SleepSectionLabel("按内容", top = 22.dp)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { timer.startVideos(1) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepRadioDot(selected = timer.mode == SleepTimerMode.VIDEOS && timer.videoCount <= 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "播完当前视频",
                        color = SleepInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "剩余 ${formatPlaybackTime(currentRemainingMs, unknownWhenZero = durationMs <= 0L)}",
                        color = SleepMuted,
                        fontSize = 12.sp,
                    )
                }
                AppGlyphIcon(AppIcon.LIST, SleepMuted, Modifier.size(19.dp), 1.5f)
            }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    if (!multiSelected) timer.startVideos(shownVideos)
                }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepRadioDot(selected = multiSelected)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "播完多个视频",
                        color = SleepInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("含当前视频，按同文件夹顺序", color = SleepMuted, fontSize = 12.sp)
                }
                Row(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(SleepField),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleepStepButton("−", enabled = shownVideos > 2) {
                        val next = (shownVideos - 1).coerceAtLeast(2)
                        pickedVideos = next
                        if (multiSelected) timer.startVideos(next)
                    }
                    Text(
                        "$shownVideos",
                        color = SleepSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 20.dp),
                    )
                    SleepStepButton("+", enabled = shownVideos < maxVideos) {
                        val next = (shownVideos + 1).coerceAtMost(maxVideos)
                        pickedVideos = next
                        if (multiSelected) timer.startVideos(next)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 14.dp), color = SleepLine, thickness = 1.dp)
            SleepToggleRow(
                title = "音量渐弱",
                subtitle = "结束前 30 秒慢慢降低音量，不会被突然打断",
                checked = timer.fade,
                onToggle = { timer.fade = !timer.fade },
            )
            SleepToggleRow(
                title = "结束前提醒",
                subtitle = "最后 1 分钟在画面上提示，可一键再看 10 分钟",
                checked = timer.warn,
                onToggle = { timer.warn = !timer.warn },
            )
        }
    }

    if (customOpen) {
        SleepCustomDurationDialog(
            initialMinutes = if (isCustomTime) timer.minutes else 40,
            onDismiss = { customOpen = false },
            onConfirm = { minutes ->
                customOpen = false
                timer.startMinutes(minutes)
            },
        )
    }
}

@Composable
private fun SleepHeader(onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SleepAlarmGlyph(SleepInk, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("定时关闭", color = SleepInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            "完成",
            color = SleepMuted,
            fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SleepStatusCard(
    active: Boolean,
    timeMode: Boolean,
    title: String,
    subtitle: String,
    clockMs: Long,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(
                if (active) {
                    Brush.linearGradient(listOf(SleepCardPink, SleepCardLilac))
                } else {
                    Brush.linearGradient(listOf(SleepField, SleepField))
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SleepAnalogClock(remainingMs = clockMs, active = active, modifier = Modifier.size(62.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (active) SleepInk else SleepSecondary,
                fontSize = if (timeMode) 16.sp else 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = SleepMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Spacer(Modifier.width(10.dp))
            if (timeMode) {
                Column(Modifier.width(124.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SleepActionChip("+5 分钟", Modifier.weight(1f)) { onExtend(5) }
                        SleepActionChip("+10 分钟", Modifier.weight(1f)) { onExtend(10) }
                    }
                    SleepActionChip("关闭定时", Modifier.fillMaxWidth(), danger = true, onClick = onCancel)
                }
            } else {
                SleepActionChip("关闭定时", Modifier.width(76.dp), danger = true, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun SleepAnalogClock(remainingMs: Long, active: Boolean, modifier: Modifier = Modifier) {
    val animatedMs by animateFloatAsState(
        targetValue = if (active) remainingMs.coerceAtLeast(0L).toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "sleepTimerClock",
    )
    val face = if (active) OnlinePink.copy(alpha = .28f) else Color(0xFFE8EAEC)
    val tick = if (active) OnlinePink.copy(alpha = .55f) else Color(0xFFC9CCD0)
    val majorTick = if (active) OnlinePink else Color(0xFFB4B8BC)
    val hourHand = if (active) SleepPinkDeep else Color(0xFFB8BCC0)
    val minuteHand = if (active) OnlinePink else Color(0xFFC9CCD0)
    val pin = if (active) SleepPinkDeep else Color(0xFF9CA1A8)

    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - stroke
        drawCircle(face, radius, center, style = Stroke(stroke))

        for (index in 0 until 12) {
            val angle = index * 30f
            val major = index % 3 == 0
            val outer = radius - 2.dp.toPx()
            val inner = outer - if (major) 3.5.dp.toPx() else 2.dp.toPx()
            drawLine(
                color = if (major) majorTick else tick,
                start = pointOnCircle(center, angle, inner),
                end = pointOnCircle(center, angle, outer),
                strokeWidth = if (major) 1.8.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val seconds = animatedMs / 1000f
        val hourAngle = (seconds % 43_200f) / 43_200f * 360f
        val minuteAngle = (seconds % 3_600f) / 3_600f * 360f
        val secondAngle = (seconds % 60f) / 60f * 360f

        drawLine(
            hourHand,
            center,
            pointOnCircle(center, hourAngle, radius * .48f),
            3.5.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            minuteHand,
            center,
            pointOnCircle(center, minuteAngle, radius * .70f),
            2.2.dp.toPx(),
            StrokeCap.Round,
        )
        if (active) {
            drawLine(
                SleepBlue,
                center,
                pointOnCircle(center, secondAngle, radius * .82f),
                1.dp.toPx(),
                StrokeCap.Round,
            )
        }
        drawCircle(pin, 2.6.dp.toPx(), center)
        drawCircle(Color.White, 1.1.dp.toPx(), center)
    }
}

private fun pointOnCircle(center: Offset, angleDegrees: Float, radius: Float): Offset {
    val radians = Math.toRadians((angleDegrees - 90f).toDouble())
    return Offset(
        center.x + (cos(radians) * radius).toFloat(),
        center.y + (sin(radians) * radius).toFloat(),
    )
}

@Composable
private fun SleepActionChip(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.height(28.dp).clip(shape).background(Color.White)
            .border(1.dp, if (danger) SleepTrack else OnlinePink.copy(alpha = .38f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (danger) SleepMuted else SleepPinkDeep,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SleepSectionLabel(text: String, top: Dp) {
    Text(
        text,
        color = SleepMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = top, bottom = 10.dp),
    )
}

@Composable
private fun SleepPresetButton(
    minutes: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(50.dp).clip(shape)
            .background(if (selected) OnlinePinkSoft else SleepField)
            .border(1.5.dp, if (selected) OnlinePink else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$minutes 分钟定时" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$minutes 分钟",
            color = if (selected) SleepPinkDeep else SleepInk,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun CustomDurationButton(
    customTime: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp).clip(shape)
            .background(if (customTime) OnlinePinkSoft else SleepField)
            .border(1.5.dp, if (customTime) OnlinePink else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "自定义定时时长" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AppGlyphIcon(
            AppIcon.CLOCK,
            if (customTime) SleepPinkDeep else SleepSecondary,
            Modifier.size(16.dp),
            1.9f,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (customTime) SleepPinkDeep else SleepSecondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SleepRadioDot(selected: Boolean) {
    Box(
        Modifier.size(18.dp).clip(CircleShape)
            .border(1.5.dp, if (selected) OnlinePink else Color(0xFFC9CCD0), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(OnlinePink))
        }
    }
}

@Composable
private fun SleepStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) SleepSecondary else Color(0xFFD0D3D7), fontSize = 17.sp)
    }
}

@Composable
private fun SleepToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SleepInk, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = SleepMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        val thumbOffset by animateDpAsState(
            targetValue = if (checked) 22.dp else 2.dp,
            animationSpec = tween(180),
            label = "sleepToggleThumb",
        )
        Box(
            Modifier.width(48.dp).height(28.dp).clip(CircleShape)
                .background(if (checked) OnlinePink else SleepTrack),
        ) {
            Box(
                Modifier.offset(x = thumbOffset).padding(top = 2.dp)
                    .size(24.dp).clip(CircleShape).background(Color.White),
            )
        }
    }
}

@Composable
private fun SleepAlarmGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val stroke = side * .085f
        val center = Offset(side * .5f, side * .56f)
        drawCircle(color, side * .29f, center, style = Stroke(stroke))
        drawLine(
            color,
            Offset(side * .28f, side * .31f),
            Offset(side * .13f, side * .16f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(side * .72f, side * .31f),
            Offset(side * .87f, side * .16f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(color, center, Offset(side * .5f, side * .39f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(side * .65f, side * .59f), stroke, StrokeCap.Round)
        drawLine(
            color,
            Offset(side * .31f, side * .82f),
            Offset(side * .24f, side * .93f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(side * .69f, side * .82f),
            Offset(side * .76f, side * .93f),
            stroke,
            StrokeCap.Round,
        )
    }
}

internal fun formatCustomMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分钟"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分钟"
    }
}

internal fun formatClock(epochMillis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault())
    .format(Date(epochMillis))
