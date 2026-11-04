package io.github.bilibox.feature.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlinePinkSoft
import io.github.bilibox.feature.library.LocalGlyphIcon
import io.github.bilibox.feature.library.LocalIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SleepCardIdle = Color(0xFFF6F7F8)
private val SleepCardPink = Color(0xFFFFF0F4)
private val SleepCardLilac = Color(0xFFF4F0FF)
private val SleepField = Color(0xFFF6F7F8)
private val SleepLine = Color(0xFFF0F1F2)
private val SleepTrack = Color(0xFFE3E5E7)
private val SleepInk = Color(0xFF18191C)
private val SleepMuted = Color(0xFF9499A0)
private val SleepSecondary = Color(0xFF61666D)

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
            if (timer.videoCount <= 1) "定时关闭：播完当前视频后暂停"
            else "定时关闭：再播 ${timer.videosLeft} 个视频后暂停"
        SleepTimerMode.OFF -> ""
    }
    if (text.isNotBlank()) {
        Text(text, color = Color(0xFFFFB3CA), fontSize = 12.sp, modifier = modifier)
    }
}

/**
 * The last-minute banner over the video: "再看 10 分钟" extends, closing keeps
 * the timer running so it still stops playback on time.
 */
@Composable
internal fun SleepTimerWarning(
    remainingMs: Long,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC141418))
            .semantics { contentDescription = "定时关闭提醒" }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalGlyphIcon(LocalIcon.MOON, Color(0xFFFFB4C8), Modifier.size(14.dp), 1.8f)
        Spacer(Modifier.width(6.dp))
        Text(formatPlaybackTime(remainingMs), color = Color(0xFFFFB4C8), fontSize = 12.sp)
        Text(" 后暂停", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(OnlinePink)
                .clickable(role = Role.Button, onClick = onExtend)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("再看 10 分钟", color = Color.White, fontSize = 12.sp)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.CLOSE, Color.White.copy(alpha = .7f), Modifier.size(13.dp), 2.2f)
        }
    }
}

private val SleepPresets = listOf(15, 30, 45, 60, 90, 120)

/** White bottom sheet matching the reference "定时关闭" panel. */
@Composable
internal fun SleepTimerSheet(
    timer: PlaybackSleepTimer,
    remainingVideos: Int,
    currentRemainingMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val scrim = remember { MutableInteractionSource() }
    var customOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableIntStateOf(40) }
    var pickedVideos by remember { mutableIntStateOf(timer.videoCount.coerceAtLeast(2)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val active = timer.active
    val maxVideos = remainingVideos.coerceAtLeast(1)
    val leftMs = when (timer.mode) {
        SleepTimerMode.TIME -> (timer.endAt - now).coerceAtLeast(0L)
        SleepTimerMode.VIDEOS -> if (timer.videosLeft <= 1) currentRemainingMs.coerceAtLeast(0L) else 0L
        SleepTimerMode.OFF -> 0L
    }
    val progress = when (timer.mode) {
        SleepTimerMode.TIME -> if (timer.totalMs > 0L) leftMs.toFloat() / timer.totalMs else 0f
        SleepTimerMode.VIDEOS -> when {
            timer.videosLeft > 1 -> timer.videosLeft.toFloat() / timer.videoCount.coerceAtLeast(1)
            durationMs > 0L -> 1f - currentRemainingMs.toFloat() / durationMs
            else -> 0f
        }
        SleepTimerMode.OFF -> 0f
    }
    val ringTitle = when (timer.mode) {
        SleepTimerMode.TIME -> formatPlaybackTime(leftMs)
        SleepTimerMode.VIDEOS -> if (timer.videosLeft <= 1) formatPlaybackTime(leftMs) else "${timer.videosLeft}"
        SleepTimerMode.OFF -> "—"
    }
    val ringSub = when (timer.mode) {
        SleepTimerMode.TIME -> "后暂停"
        SleepTimerMode.VIDEOS -> if (timer.videosLeft <= 1) "后暂停" else "个视频"
        SleepTimerMode.OFF -> null
    }
    val stateTitle = when (timer.mode) {
        SleepTimerMode.TIME -> "将于 ${clockAfter(leftMs)} 暂停播放"
        SleepTimerMode.VIDEOS ->
            if (timer.videoCount <= 1) "播完当前视频后暂停"
            else "再播 ${timer.videosLeft} 个视频后暂停"
        SleepTimerMode.OFF -> "未开启"
    }
    val stateSubtitle = when (timer.mode) {
        SleepTimerMode.TIME -> buildString {
            append("共 ${timer.minutes} 分钟")
            if (timer.fade) append(" · 最后 30 秒音量渐弱")
        }
        SleepTimerMode.VIDEOS -> "含当前视频，按同文件夹顺序"
        SleepTimerMode.OFF -> "选择一个时长，或播完视频后暂停"
    }
    val multiSelected = timer.mode == SleepTimerMode.VIDEOS && timer.videoCount > 1
    val shownVideos = if (multiSelected) timer.videoCount else pickedVideos
    val isCustomTime = timer.mode == SleepTimerMode.TIME && timer.minutes !in SleepPresets

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * .92f
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .46f))
                .clickable(interactionSource = scrim, indication = null, onClick = onDismiss)
                .semantics { contentDescription = "关闭定时关闭" }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFFDADCE0))
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocalGlyphIcon(LocalIcon.MOON, SleepInk, Modifier.size(20.dp), 1.9f)
                Spacer(Modifier.width(8.dp))
                Text("定时关闭", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "完成",
                    color = SleepMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) Brush.linearGradient(listOf(SleepCardPink, SleepCardLilac))
                        else Brush.linearGradient(listOf(SleepCardIdle, SleepCardIdle))
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepTimerRing(progress = progress, active = active, title = ringTitle, subtitle = ringSub)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stateTitle, color = if (active) SleepInk else SleepSecondary, fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                    Spacer(Modifier.height(3.dp))
                    Text(stateSubtitle, color = SleepMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (active) {
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (timer.mode == SleepTimerMode.TIME) {
                                SleepQuickChip("+5 分钟") { timer.extend(5) }
                                SleepQuickChip("+15 分钟") { timer.extend(15) }
                            }
                            SleepQuickChip("关闭定时", color = Color(0xFFF85A54)) { timer.cancel() }
                        }
                    }
                }
            }

            SleepSectionLabel("按时长", top = 18.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepPresets.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            val selected = timer.mode == SleepTimerMode.TIME && timer.minutes == minutes
                            SleepPresetButton(
                                minutes = minutes,
                                selected = selected,
                                modifier = Modifier.weight(1f),
                                onClick = { timer.startMinutes(minutes) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (customOpen || isCustomTime) OnlinePinkSoft else SleepField)
                    .clickable(role = Role.Button) { customOpen = !customOpen },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LocalGlyphIcon(
                    LocalIcon.CLOCK,
                    if (customOpen || isCustomTime) OnlinePink else SleepSecondary,
                    Modifier.size(16.dp),
                    1.9f,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isCustomTime) "自定义 · ${timer.minutes} 分钟" else "自定义",
                    color = if (customOpen || isCustomTime) OnlinePink else SleepSecondary,
                    fontSize = 14.sp,
                )
            }
            AnimatedVisibility(visible = customOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                        .background(SleepField).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("$customMinutes", color = OnlinePink, fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(" 分钟", color = SleepMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 3.dp))
                        Text("  ${clockAfter(customMinutes * 60_000L)} 暂停", color = SleepMuted,
                            fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp, start = 6.dp))
                    }
                    Slider(
                        value = customMinutes.toFloat(),
                        onValueChange = { customMinutes = (it / 5f).roundToInt().coerceIn(5, 180) * 5 },
                        valueRange = 5f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = OnlinePink,
                            activeTrackColor = OnlinePink,
                            inactiveTrackColor = SleepTrack,
                        ),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("5", "60", "120", "180 分钟").forEach {
                            Text(it, color = SleepMuted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp))
                            .background(OnlinePink).clickable(role = Role.Button) {
                                customOpen = false
                                timer.startMinutes(customMinutes)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("开始计时", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            SleepSectionLabel("按内容", top = 18.dp)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { timer.startVideos(1) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepRadioDot(selected = timer.mode == SleepTimerMode.VIDEOS && timer.videoCount <= 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("播完当前视频", color = SleepInk, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "剩余 ${formatPlaybackTime(currentRemainingMs, unknownWhenZero = durationMs <= 0L)}",
                        color = SleepMuted, fontSize = 11.sp,
                    )
                }
                LocalGlyphIcon(LocalIcon.FILM, SleepSecondary, Modifier.size(18.dp), 1.7f)
            }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    if (!multiSelected) timer.startVideos(shownVideos)
                }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepRadioDot(selected = multiSelected)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("播完多个视频", color = SleepInk, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("含当前视频，按同文件夹顺序", color = SleepMuted, fontSize = 11.sp)
                }
                Row(
                    Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F2F3)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleepStepButton("−", enabled = shownVideos > 2) {
                        pickedVideos = (shownVideos - 1).coerceAtLeast(2)
                        timer.startVideos(pickedVideos)
                    }
                    Text(
                        "$shownVideos",
                        color = SleepSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 18.dp),
                    )
                    SleepStepButton("+", enabled = shownVideos < maxVideos) {
                        pickedVideos = (shownVideos + 1).coerceAtMost(maxVideos)
                        timer.startVideos(pickedVideos)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 10.dp), color = SleepLine, thickness = 1.dp)
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
}

@Composable
private fun SleepTimerRing(progress: Float, active: Boolean, title: String, subtitle: String?) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawCircle(
                color = if (active) OnlinePink.copy(alpha = .18f) else SleepTrack,
                radius = diameter / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            if (active) {
                drawArc(
                    color = OnlinePink,
                    startAngle = -90f,
                    sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = if (active) OnlinePink else SleepMuted,
                fontSize = if (title == "—") 20.sp else 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(subtitle, color = SleepMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SleepQuickChip(
    text: String,
    color: Color = SleepSecondary,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 11.5.sp)
    }
}

@Composable
private fun SleepSectionLabel(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(text, color = SleepMuted, fontSize = 12.sp,
        modifier = Modifier.padding(top = top, bottom = 8.dp))
}

@Composable
private fun SleepPresetButton(
    minutes: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) OnlinePinkSoft else SleepField)
            .border(1.5.dp, if (selected) OnlinePink else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$minutes 分钟定时" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$minutes 分钟",
                color = if (selected) OnlinePink else SleepInk,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${clockAfter(minutes * 60_000L)} 暂停",
                color = if (selected) OnlinePink.copy(alpha = .8f) else SleepMuted,
                fontSize = 10.sp,
            )
        }
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(OnlinePink))
        }
    }
}

@Composable
private fun SleepStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) SleepSecondary else Color(0xFFD0D3D7), fontSize = 16.sp)
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
        Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SleepInk, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = SleepMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.width(40.dp).height(24.dp).clip(CircleShape)
                .background(if (checked) OnlinePink else SleepTrack),
        ) {
            Box(
                Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 3.dp).size(18.dp).clip(CircleShape).background(Color.White),
            )
        }
    }
}

private fun clockAfter(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(System.currentTimeMillis() + millis.coerceAtLeast(0L)))
