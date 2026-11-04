package io.github.bilibox.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.feature.online.EqualizerBars

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

@Composable
internal fun LocalQueueRow(
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
        } else {
            0f
        }
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
internal fun QueueMeta(
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
