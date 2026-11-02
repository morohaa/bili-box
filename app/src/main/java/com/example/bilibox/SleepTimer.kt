package com.example.bilibox

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * 定时关闭，参考「B 站本地播放器」原型：
 * 按时长（15–120 分钟或自定义）或按内容（播完当前 / 播完多个视频），
 * 结束前 30 秒渐弱音量，最后一分钟在画面上提醒并可再看 10 分钟。
 */

private const val SleepFadeMs = 30_000L
private const val SleepWarnMs = 60_000L

private val SleepCardIdle = Color(0xFFF6F7F8)
private val SleepCardPink = Color(0xFFFFF0F4)
private val SleepCardLilac = Color(0xFFF4F0FF)
private val SleepField = Color(0xFFF6F7F8)
private val SleepLine = Color(0xFFF0F1F2)
private val SleepTrack = Color(0xFFE3E5E7)
private val SleepInk = Color(0xFF18191C)
private val SleepMuted = Color(0xFF9499A0)
private val SleepSecondary = Color(0xFF61666D)

internal enum class SleepTimerMode { OFF, TIME, VIDEOS }

/** Sleep timer shared by the player details pane and the listen page. */
@Stable
internal class PlaybackSleepTimer {
    var mode by mutableStateOf(SleepTimerMode.OFF)
        private set

    /** elapsedRealtime deadline, or 0 when not counting minutes. */
    var endAt by mutableLongStateOf(0L)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var minutes by mutableIntStateOf(0)
        private set

    /** Configured and remaining item count for [SleepTimerMode.VIDEOS]. */
    var videoCount by mutableIntStateOf(0)
        private set
    var videosLeft by mutableIntStateOf(0)
        private set

    var fade by mutableStateOf(true)
    var warn by mutableStateOf(true)

    /** True once the last-minute banner should be on screen. */
    var warning by mutableStateOf(false)
        private set

    /** The banner was already offered once for this deadline. */
    internal var warned by mutableStateOf(false)
        private set

    val active: Boolean get() = mode != SleepTimerMode.OFF

    fun startMinutes(value: Int) {
        val safe = value.coerceAtLeast(1)
        mode = SleepTimerMode.TIME
        minutes = safe
        totalMs = safe * 60_000L
        endAt = SystemClock.elapsedRealtime() + totalMs
        videoCount = 0
        videosLeft = 0
        clearWarning()
    }

    fun startVideos(count: Int) {
        val safe = count.coerceAtLeast(1)
        mode = SleepTimerMode.VIDEOS
        endAt = 0L
        totalMs = 0L
        minutes = 0
        videoCount = safe
        videosLeft = safe
        clearWarning()
    }

    /** Adds minutes; a "播完多个视频" timer turns into a countdown on extend. */
    fun extend(extraMinutes: Int = 10, currentRemainingMs: Long = 0L) {
        when (mode) {
            SleepTimerMode.TIME -> {
                val extraMs = extraMinutes * 60_000L
                endAt += extraMs
                totalMs += extraMs
                minutes += extraMinutes
                clearWarning()
            }
            SleepTimerMode.VIDEOS -> {
                val extra = extraMinutes + ceil(currentRemainingMs / 60_000.0).toInt()
                startMinutes(extra.coerceAtLeast(extraMinutes))
            }
            SleepTimerMode.OFF -> startMinutes(extraMinutes)
        }
    }

    fun cancel() {
        mode = SleepTimerMode.OFF
        endAt = 0L
        totalMs = 0L
        minutes = 0
        videoCount = 0
        videosLeft = 0
        clearWarning()
    }

    internal fun showWarning() {
        warning = true
        warned = true
    }

    internal fun dismissWarning() {
        warning = false
    }

    internal fun clearWarning() {
        warning = false
        warned = false
    }

    /** One file finished while counting items; the caller decides about pausing. */
    internal fun onVideoFinished() {
        // The last file pauses at its end and is reported by the play-when-ready
        // listener, so only intermediate files are counted here.
        if (mode == SleepTimerMode.VIDEOS && videosLeft > 1) {
            videosLeft = (videosLeft - 1).coerceAtLeast(1)
        }
    }
}

/**
 * Drives [timer]: pauses when the wall-clock deadline is reached, fades the
 * volume, counts finished files and raises the last-minute warning.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun SleepTimerEffect(player: ExoPlayer, timer: PlaybackSleepTimer) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(player, timer.mode, timer.endAt) {
        if (timer.mode != SleepTimerMode.TIME) return@LaunchedEffect
        try {
            while (isActive) {
                val left = timer.endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    player.playWhenReady = false
                    timer.cancel()
                    Toast.makeText(context, "定时关闭：已暂停播放", Toast.LENGTH_SHORT).show()
                    break
                }
                if (timer.warn && !timer.warned && left <= SleepWarnMs) timer.showWarning()
                player.volume = if (timer.fade && left <= SleepFadeMs) {
                    (left.toFloat() / SleepFadeMs).coerceIn(0f, 1f)
                } else {
                    1f
                }
                delay(if (left <= SleepFadeMs) 200L else 1_000L)
            }
        } finally {
            player.volume = 1f
        }
    }

    // 按内容: count finished files; only the last one is allowed to pause at
    // its end, otherwise 连播 / 自动下一 P would never reach the next file.
    DisposableEffect(player, timer.mode, timer.videosLeft) {
        if (timer.mode != SleepTimerMode.VIDEOS) return@DisposableEffect onDispose { }
        player.pauseAtEndOfMediaItems = timer.videosLeft <= 1
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && timer.mode == SleepTimerMode.VIDEOS) {
                    timer.onVideoFinished()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    val count = timer.videoCount
                    timer.cancel()
                    Toast.makeText(
                        context,
                        if (count <= 1) "定时关闭：当前视频已播完" else "定时关闭：已播完 $count 个视频",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.pauseAtEndOfMediaItems = false
        }
    }

    // The last file of a "播完多个视频" timer still fades and warns like a countdown.
    LaunchedEffect(player, timer.mode, timer.videosLeft) {
        if (timer.mode != SleepTimerMode.VIDEOS) return@LaunchedEffect
        try {
            while (isActive) {
                if (timer.videosLeft > 1) {
                    player.volume = 1f
                } else {
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    val remaining = if (duration > 0L) {
                        (duration - player.currentPosition).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    if (timer.warn && !timer.warned && remaining in 1..SleepWarnMs) timer.showWarning()
                    player.volume = if (timer.fade && remaining in 0..SleepFadeMs) {
                        (remaining.toFloat() / SleepFadeMs).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                }
                delay(250L)
            }
        } finally {
            player.volume = 1f
        }
    }
}

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
