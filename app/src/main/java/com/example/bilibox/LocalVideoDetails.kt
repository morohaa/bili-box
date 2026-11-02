package com.example.bilibox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 本地视频播放页下半部分的详情设计。
 *
 * 参考「B 站本地播放器」原型：来源卡、可展开标题、图标元信息、五个快捷操作、
 * 续播条，以及同文件夹队列；第二个标签页直接展示完整的媒体信息。
 */

private val DetailBlue = Color(0xFF00AEEC)
private val DetailBlueSoft = Color(0xFFE8F7FD)
private val DetailLine = Color(0xFFE9EAEC)
private val DetailCard = Color(0xFFF6F7F8)
private val DetailSecondary = Color(0xFF61666D)
private val DetailOrange = Color(0xFFFF8C00)
private val DetailOrangeSoft = Color(0xFFFFF7E8)
private val DetailPinkSoft = Color(0xFFFFF1F5)

/** How playback continues when the current local video reaches its end. */
internal enum class LocalQueueMode(
    val label: String,
    val description: String,
    val icon: LocalIcon,
) {
    LIST_LOOP("列表循环", "播完自动下一个，列表结束后从头继续", LocalIcon.LOOP),
    SINGLE_LOOP("单个循环", "循环播放当前视频", LocalIcon.LOOP_ONE),
    STOP_AFTER("播完暂停", "播放完当前视频后停止", LocalIcon.WAVEFORM),
    SHUFFLE("随机播放", "随机播放同文件夹里的下一个视频", LocalIcon.SHUFFLE),
}

/** Bottom sheet matching the reference "播放顺序 / 同文件夹" card. */
@Composable
internal fun PlaybackModeSheet(
    selected: LocalQueueMode,
    onDismiss: () -> Unit,
    onSelect: (LocalQueueMode) -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .40f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = "关闭播放顺序" }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.White)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFFDADCE0))
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("播放顺序", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("同文件夹", color = Muted, fontSize = 12.sp)
            }
            LocalQueueMode.entries.forEach { mode ->
                val active = mode == selected
                Row(
                    Modifier.fillMaxWidth().height(50.dp)
                        .clickable(role = Role.Button) { onSelect(mode) }
                        .padding(horizontal = 18.dp)
                        .semantics { contentDescription = mode.label },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocalGlyphIcon(
                        mode.icon,
                        if (active) OnlinePink else Color(0xFF61666D),
                        Modifier.size(21.dp),
                        1.8f,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        mode.label,
                        color = if (active) OnlinePink else Ink,
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.weight(1f))
                    if (active) {
                        LocalGlyphIcon(LocalIcon.CHECK, OnlinePink, Modifier.size(18.dp), 2.2f)
                    }
                }
            }
        }
    }
}

internal fun localVideoFolderPath(video: LocalVideo): String {
    val folder = video.relativeDirectory.split('/')
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "根目录" }
    return "内部存储 / $folder"
}

internal fun localVideoFullPath(video: LocalVideo): String =
    "${localVideoFolderPath(video)} / ${video.name}"

/**
 * The 简介 tab of the portrait local player: everything between the tab strip
 * and the same-folder queue that had been a single flat "视频信息" block before.
 */
@Composable
internal fun LocalVideoIntro(
    video: LocalVideo,
    playlist: List<LocalVideo>,
    durationMs: Long,
    resumeMs: Long,
    saved: Boolean,
    queueMode: LocalQueueMode,
    playbackByKey: Map<String, LocalVideoPlayback>,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit,
    onSelectVideo: (LocalVideo) -> Unit,
    onResumeDismiss: () -> Unit,
) {
    val actions = LocalPlayerDetailActions.current
    val speed = actions?.speed ?: 1f
    val positionMs = actions?.positionMs ?: 0L
    var titleExpanded by remember(video.uri) { mutableStateOf(false) }
    var titleOverflows by remember(video.uri) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 20.dp)) {
        LocalSourceHeader(video)

        // Title + quality tag; tapping anywhere on the row toggles the long title.
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp)
                .clickable(enabled = titleExpanded || titleOverflows, role = Role.Button) {
                    titleExpanded = !titleExpanded
                },
            verticalAlignment = Alignment.Top,
        ) {
            val quality = if (video.width > 0 && video.height > 0) {
                resolutionLabel(video.width, video.height)
            } else ""
            if (quality.isNotBlank()) {
                Box(
                    Modifier.padding(top = 1.dp).clip(RoundedCornerShape(4.dp))
                        .background(DetailBlueSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(quality, color = DetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                video.title,
                color = Ink,
                fontSize = 16.5.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    // Only expose the expand affordance when the collapsed title
                    // actually hides something.
                    if (!titleExpanded) titleOverflows = result.hasVisualOverflow
                },
                modifier = Modifier.weight(1f),
            )
            if (titleExpanded || titleOverflows) {
                val chevron by animateFloatAsState(
                    targetValue = if (titleExpanded) 180f else 0f,
                    animationSpec = tween(180),
                    label = "标题展开箭头",
                )
                LocalGlyphIcon(
                    LocalIcon.CHEVRON_DOWN,
                    Muted,
                    Modifier.padding(start = 4.dp, top = 4.dp).size(16.dp).rotate(chevron),
                    2f,
                )
            }
        }

        LocalMetaRow(video, durationMs)

        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val speedActive = abs(speed - 1f) > .009f
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = LocalIcon.SPEED,
                label = if (speedActive) formatSpeedBadge(speed) else "倍速",
                active = speedActive,
                enabled = actions != null,
                onClick = { actions?.openSpeedPanel?.invoke() },
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = queueMode.icon,
                label = queueMode.label,
                enabled = actions != null,
                onClick = { actions?.openPlaybackModeSheet?.invoke() },
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = LocalIcon.CAMERA,
                label = "截图",
                enabled = actions?.screenshotEnabled == true,
                onClick = { actions?.takeScreenshot?.invoke() },
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = LocalIcon.STAR,
                label = if (saved) "已收藏" else "收藏",
                active = saved,
                filled = saved,
                onClick = onToggleSaved,
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = LocalIcon.SHARE,
                label = "分享",
                onClick = onShare,
            )
        }

        if (resumeMs > 3_000L) {
            LocalResumeBar(
                resumeMs = resumeMs,
                onRestart = {
                    actions?.seekTo?.invoke(0L)
                    onResumeDismiss()
                },
                onDismiss = onResumeDismiss,
            )
        }

        if (playlist.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 16.dp), color = DetailLine, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("同文件夹", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("${playlist.size} 个视频", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("按文件顺序", color = Muted, fontSize = 10.5.sp)
            }
            // Keep the inline queue bounded; folders with hundreds of clips would
            // otherwise eagerly compose a thumbnail for every row.
            val shown = playlist.take(24)
            shown.forEach { item ->
                LocalQueueRow(
                    item = item,
                    isCurrent = item.uri == video.uri,
                    playback = playbackByKey[item.key],
                    currentPositionMs = positionMs,
                    currentDurationMs = durationMs,
                    onClick = { onSelectVideo(item) },
                )
            }
            if (playlist.size == 1) {
                Text(
                    "这个文件夹里只有这一个视频",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            } else if (playlist.size > shown.size) {
                Text(
                    "还有 ${playlist.size - shown.size} 个视频，返回文件夹可以查看全部",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalSourceHeader(video: LocalVideo) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(DetailPinkSoft),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.FOLDER, OnlinePink, Modifier.size(22.dp), 1.7f)
            if (video.isBiliCache) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(16.dp)
                        .clip(CircleShape).background(DetailBlue).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("B", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (video.isBiliCache) "哔哩哔哩缓存" else "本机文件",
                color = OnlinePink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                localVideoFolderPath(video),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (video.extension.isNotBlank()) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(DetailCard)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    video.extension.uppercase(),
                    color = DetailSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalMetaRow(video: LocalVideo, durationMs: Long) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DetailMeta(LocalIcon.CLOCK, formatVideoDuration(durationMs).ifBlank { "未知" })
        if (video.sizeBytes > 0L) DetailMeta(LocalIcon.SIZE, formatBytes(video.sizeBytes))
        if (video.width > 0 && video.height > 0) {
            DetailMeta(LocalIcon.FRAME, "${video.width}×${video.height}")
        }
        if (video.dateModifiedMs > 0L) {
            DetailMeta(LocalIcon.CALENDAR, formatRelativeDate(video.dateModifiedMs))
        }
    }
}

@Composable
private fun DetailMeta(icon: LocalIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LocalGlyphIcon(icon, Muted, Modifier.size(13.dp), 1.8f)
        Spacer(Modifier.width(3.dp))
        Text(text, color = Muted, fontSize = 11.5.sp, maxLines = 1)
    }
}

@Composable
private fun LocalDetailAction(
    modifier: Modifier = Modifier,
    icon: LocalIcon,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Muted.copy(alpha = .38f)
        active -> OnlinePink
        else -> DetailSecondary
    }
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LocalGlyphIcon(icon, tint, Modifier.size(23.dp), 1.75f, fill = filled)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
    }
}

@Composable
private fun LocalResumeBar(
    resumeMs: Long,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
            .background(DetailOrangeSoft)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalGlyphIcon(LocalIcon.HISTORY, DetailOrange, Modifier.size(15.dp), 2f)
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("上次看到 ", color = DetailOrange, fontSize = 12.sp)
            Text(
                formatVideoDuration(resumeMs),
                color = DetailOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "从头播放",
            color = DetailOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onRestart)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "关闭续播提示" },
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.CLOSE, DetailOrange.copy(alpha = .7f), Modifier.size(12.dp), 2f)
        }
    }
}

@Composable
private fun LocalQueueRow(
    item: LocalVideo,
    isCurrent: Boolean,
    playback: LocalVideoPlayback?,
    currentPositionMs: Long,
    currentDurationMs: Long,
    onClick: () -> Unit,
) {
    val watching = playback?.state as? LocalVideoPlaybackState.Watching
    val fraction = if (isCurrent) {
        if (currentDurationMs > 0L) {
            (currentPositionMs.toFloat() / currentDurationMs).coerceIn(0f, 1f)
        } else 0f
    } else {
        watching?.fraction ?: 0f
    }
    val watched = playback?.state == LocalVideoPlaybackState.Watched
    val thumbnail = rememberVideoThumbnail(item)

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) DetailPinkSoft.copy(alpha = .55f) else Color.Transparent)
            .clickable(enabled = !isCurrent, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(116.dp).height(65.dp).clip(RoundedCornerShape(8.dp))
                .background(coverBrush(item.title)),
        ) {
            thumbnail?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .44f)))
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EqualizerBars()
                    Spacer(Modifier.width(3.dp))
                    Text("播放中", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                }
            } else if (item.durationMs > 0L) {
                Text(
                    formatVideoDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)
                        .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (fraction > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.White.copy(alpha = .35f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .background(OnlinePink),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = if (isCurrent) OnlinePink else Ink,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.extension.isNotBlank()) {
                    Text(
                        item.extension.uppercase(),
                        color = Muted,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier.border(1.dp, DetailLine, RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                QueueMeta(
                    item = item,
                    isCurrent = isCurrent,
                    watching = watching,
                    watched = watched,
                    currentPositionMs = currentPositionMs,
                )
            }
        }
        if (!isCurrent) {
            LocalGlyphIcon(
                LocalIcon.CHEVRON_RIGHT,
                Muted.copy(alpha = .55f),
                Modifier.padding(start = 2.dp).size(14.dp),
                1.8f,
            )
        }
    }
}

@Composable
private fun QueueMeta(
    item: LocalVideo,
    isCurrent: Boolean,
    watching: LocalVideoPlaybackState.Watching?,
    watched: Boolean,
    currentPositionMs: Long,
) {
    val parts = buildList {
        if (item.width > 0 && item.height > 0) add(resolutionLabel(item.width, item.height))
        if (item.sizeBytes > 0L) add(formatBytes(item.sizeBytes))
    }
    val progress = when {
        isCurrent -> "看到 ${formatVideoDuration(currentPositionMs)}"
        watching != null -> "看到 ${formatVideoDuration(watching.positionMs)}"
        watched -> "已看完"
        else -> null
    }
    val text = remember(item.key, progress, isCurrent, parts) {
        buildAnnotatedString {
            parts.forEachIndexed { index, part ->
                if (index > 0) withStyle(SpanStyle(color = Muted.copy(alpha = .6f))) { append("  ·  ") }
                withStyle(SpanStyle(color = Muted)) { append(part) }
            }
            if (progress != null) {
                if (parts.isNotEmpty()) withStyle(SpanStyle(color = Muted.copy(alpha = .6f))) { append("  ·  ") }
                withStyle(SpanStyle(color = if (isCurrent || watching != null) OnlinePink else Muted)) { append(progress) }
            }
        }
    }
    Text(text, fontSize = 10.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The 媒体信息 tab: file, stream and playback facts in compact tables. */
@Composable
internal fun LocalVideoMediaInfo(
    video: LocalVideo,
    durationMs: Long,
) {
    val context = LocalContext.current
    val actions = LocalPlayerDetailActions.current
    val positionMs = actions?.positionMs ?: 0L
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val path = localVideoFullPath(video)
    val sizeText = if (video.sizeBytes > 0L) {
        "${Formatter.formatFileSize(context, video.sizeBytes)}（${String.format(Locale.US, "%,d", video.sizeBytes)} 字节）"
    } else "未知"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 22.dp)) {
        MediaInfoSection(
            title = "文件",
            icon = LocalIcon.FILE,
            rows = listOf(
                "文件名" to video.name,
                "路径" to path,
                "大小" to sizeText,
                "修改时间" to formatDetailDate(video.dateModifiedMs),
                "添加时间" to formatDetailDate(video.dateAddedMs),
                "容器" to video.extension.uppercase().ifBlank { "未知" },
            ),
        )
        MediaInfoSection(
            title = "视频",
            icon = LocalIcon.FRAME,
            rows = listOf(
                "分辨率" to if (video.width > 0 && video.height > 0) {
                    "${video.width} × ${video.height}（${resolutionLabel(video.width, video.height)}）"
                } else "未知",
                "帧率" to if (video.frameRate > 0f) frameRateLabel(video.frameRate) else "未知",
                "类型" to video.mimeType.ifBlank { "未知" },
            ),
        )
        MediaInfoSection(
            title = "播放",
            icon = LocalIcon.HISTORY,
            rows = listOf(
                "进度" to if (durationMs > 0L) {
                    "${formatVideoDuration(positionMs)} / ${formatVideoDuration(durationMs)}" +
                        "（${(fraction * 100).roundToInt()}%）"
                } else "未知",
                "状态" to when {
                    fraction >= .995f -> "已看完"
                    positionMs > 0L -> "观看中"
                    else -> "未开始"
                },
                "文件夹" to video.relativeDirectory.ifBlank { "根目录" },
            ),
        )
        Box(
            Modifier.fillMaxWidth().padding(top = 18.dp).height(42.dp)
                .clip(RoundedCornerShape(21.dp)).background(DetailCard)
                .clickable(role = Role.Button) {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText("视频路径", path))
                        Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocalGlyphIcon(LocalIcon.COPY, DetailSecondary, Modifier.size(14.dp), 1.8f)
                Spacer(Modifier.width(6.dp))
                Text("复制文件路径", color = DetailSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    icon: LocalIcon,
    rows: List<Pair<String, String>>,
) {
    val visible = rows.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalGlyphIcon(icon, Muted, Modifier.size(14.dp), 1.8f)
        Spacer(Modifier.width(5.dp))
        Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(DetailCard)) {
        visible.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(row.first, color = Muted, fontSize = 11.5.sp, modifier = Modifier.width(64.dp))
                Text(
                    row.second,
                    color = Ink,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index != visible.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 12.dp),
                    color = Color(0xFFEDEEF0),
                    thickness = 1.dp,
                )
            }
        }
    }
}

private fun formatDetailDate(milliseconds: Long): String {
    if (milliseconds <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(milliseconds))
}
