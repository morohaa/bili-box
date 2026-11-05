package io.github.bilibox.feature.player.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.playback.LocalQueueMode
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatBytes
import io.github.bilibox.core.util.formatRelativeDate
import io.github.bilibox.core.util.formatSpeedBadge
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.core.util.resolutionLabel
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.feature.player.LocalPlayerDetailActions
import kotlin.math.abs

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
    queueMode: LocalQueueMode,
    playbackByKey: Map<String, LocalVideoPlayback>,
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
            } else {
                ""
            }
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
                AppGlyphIcon(
                    AppIcon.CHEVRON_DOWN,
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
                icon = AppIcon.SPEED,
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
                icon = AppIcon.CAMERA,
                label = "截图",
                enabled = actions?.screenshotEnabled == true,
                onClick = { actions?.takeScreenshot?.invoke() },
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = AppIcon.MOON,
                label = "定时关闭",
                active = actions?.sleepTimerActive == true,
                enabled = actions != null,
                onClick = { actions?.openSleepTimerSheet?.invoke() },
            )
            LocalDetailAction(
                modifier = Modifier.weight(1f),
                icon = AppIcon.SHARE,
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
            AppGlyphIcon(AppIcon.FOLDER, OnlinePink, Modifier.size(22.dp), 1.7f)
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
        DetailMeta(AppIcon.CLOCK, formatVideoDuration(durationMs).ifBlank { "未知" })
        if (video.sizeBytes > 0L) DetailMeta(AppIcon.SIZE, formatBytes(video.sizeBytes))
        if (video.width > 0 && video.height > 0) {
            DetailMeta(AppIcon.FRAME, "${video.width}×${video.height}")
        }
        if (video.dateModifiedMs > 0L) {
            DetailMeta(AppIcon.CALENDAR, formatRelativeDate(video.dateModifiedMs))
        }
    }
}

@Composable
private fun DetailMeta(icon: AppIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppGlyphIcon(icon, Muted, Modifier.size(13.dp), 1.8f)
        Spacer(Modifier.width(3.dp))
        Text(text, color = Muted, fontSize = 11.5.sp, maxLines = 1)
    }
}

@Composable
private fun LocalDetailAction(
    modifier: Modifier = Modifier,
    icon: AppIcon,
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
        AppGlyphIcon(icon, tint, Modifier.size(23.dp), 1.75f, fill = filled)
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
        AppGlyphIcon(AppIcon.HISTORY, DetailOrange, Modifier.size(15.dp), 2f)
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
            AppGlyphIcon(AppIcon.CLOSE, DetailOrange.copy(alpha = .7f), Modifier.size(12.dp), 2f)
        }
    }
}
