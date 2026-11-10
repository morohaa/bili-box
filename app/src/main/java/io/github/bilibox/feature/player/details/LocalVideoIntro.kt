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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.playback.LocalQueueMode
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
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

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 20.dp)) {
        LocalSourceHeader(video)

        // Match the online intro's compact title, with the full filename one tap away.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp)
                .clickable(enabled = titleExpanded || titleOverflows, role = Role.Button) {
                    titleExpanded = !titleExpanded
                }
                .semantics {
                    stateDescription = if (titleExpanded) "已展开" else "已收起"
                },
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                video.title,
                color = PlaybackInk,
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.15).sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (titleExpanded) Int.MAX_VALUE else 1,
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
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.padding(top = 2.dp).size(24.dp)
                        .semantics { contentDescription = if (titleExpanded) "收起文件名" else "展开文件名" },
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppIcon.CHEVRON_DOWN, PlaybackMuted, Modifier.size(18.dp).rotate(chevron), 2f)
                }
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
                dimWhenDisabled = false,
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
                Text("同文件夹", color = PlaybackInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("${playlist.size} 个视频", color = PlaybackMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("按文件顺序", color = PlaybackMuted, fontSize = 10.5.sp)
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
                    color = PlaybackMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            } else if (playlist.size > shown.size) {
                Text(
                    "还有 ${playlist.size - shown.size} 个视频，返回文件夹可以查看全部",
                    color = PlaybackMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalSourceHeader(video: LocalVideo) {
    val accent = OnlinePink
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp)) {
            Box(
                Modifier.size(42.dp).shadow(4.dp, CircleShape)
                    .background(PlaybackSurface, CircleShape)
                    .border(2.dp, PlaybackSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(36.dp).background(DetailPinkSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppIcon.FOLDER, OnlinePink, Modifier.size(22.dp), 1.7f)
                }
            }
            if (video.isBiliCache) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(16.dp)
                        .clip(CircleShape).background(DetailBlue).border(2.dp, PlaybackSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("B", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (video.isBiliCache) "哔哩哔哩缓存" else "本机文件",
                    color = PlaybackInk,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (video.extension.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        video.extension.uppercase(),
                        color = DetailSecondary,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.background(DetailCard, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                localVideoFolderPath(video),
                color = PlaybackMuted,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (video.width > 0 && video.height > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(32.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFF7EA6), Color(0xFFFF6699))),
                        CircleShape,
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    resolutionLabel(video.width, video.height),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
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
        AppGlyphIcon(icon, PlaybackMuted, Modifier.size(13.dp), 1.8f)
        Spacer(Modifier.width(3.dp))
        Text(text, color = PlaybackMuted, fontSize = 11.5.sp, maxLines = 1)
    }
}

@Composable
private fun LocalDetailAction(
    modifier: Modifier = Modifier,
    icon: AppIcon,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    dimWhenDisabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled && dimWhenDisabled -> PlaybackMuted.copy(alpha = .38f)
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
