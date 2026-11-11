package io.github.bilibox.feature.offline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.VideoCoverBadge
import io.github.bilibox.core.ui.component.VideoCoverPlaceholder
import io.github.bilibox.core.ui.component.VideoListCardLayout
import io.github.bilibox.core.ui.component.coverBrush
import io.github.bilibox.core.ui.component.rememberVideoThumbnail
import io.github.bilibox.core.util.formatBytes
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.cachedVideoThumbnail
import io.github.bilibox.data.localvideo.loadVideoThumbnail
import io.github.bilibox.data.online.OfflineCacheEntry
import io.github.bilibox.data.online.OfflineCacheGroup

@Composable
internal fun OfflineGroupRow(
    group: OfflineCacheGroup,
    playback: LocalVideoPlayback?,
    managing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = group.entries.firstOrNull { !it.audioOnly } ?: group.entries.first()
    val badge = if (group.isCollection) "${group.entries.map { it.cid }.distinct().size} 个分集" else playbackBadge(entry, playback)
    OfflineCacheRow(
        entry = entry, title = group.title,
        author = group.author,
        info = "${formatBytes(group.sizeBytes)} · ${if (group.entries.all { it.audioOnly }) "音频" else "已缓存"}",
        badge = badge, managing = managing, selected = selected, enabled = enabled, onClick = onClick, onDelete = onDelete,
    )
}

@Composable
internal fun OfflineEpisodeRow(
    entry: OfflineCacheEntry,
    author: String,
    playback: LocalVideoPlayback?,
    managing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    OfflineCacheRow(
        entry = entry, title = "P${entry.page}  ${entry.title}${if (entry.audioOnly) " · 音频" else ""}",
        author = author,
        info = listOf(formatBytes(entry.media.sizeBytes), entry.quality).filter { it.isNotBlank() }.joinToString(" · "),
        badge = playbackBadge(entry, playback), managing = managing, selected = selected, enabled = enabled,
        onClick = onClick, onDelete = onDelete,
    )
}

private fun playbackBadge(entry: OfflineCacheEntry, playback: LocalVideoPlayback?): String = when (val state = playback?.state) {
    LocalVideoPlaybackState.Watched -> "已看完"

    is LocalVideoPlaybackState.Watching -> "${formatPlaybackTime(state.positionMs)} / ${formatPlaybackTime(entry.media.durationMs, true)}"

    else -> if (entry.media.durationMs > 0L) {
        formatPlaybackTime(entry.media.durationMs)
    } else if (entry.audioOnly) {
        "音频"
    } else {
        "未播放"
    }
}

@Composable
private fun OfflineCacheRow(
    entry: OfflineCacheEntry,
    title: String,
    author: String,
    info: String,
    badge: String,
    managing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (managing) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled, modifier = Modifier.size(36.dp).padding(end = 6.dp))
        }
        VideoListCardLayout(
            title = title,
            author = author.ifBlank { "未知 UP 主" },
            darkMode = colors.background.luminance() < .5f,
            cover = { OfflineArtwork(entry, badge, Modifier.fillMaxSize()) },
            metadata = { color ->
                Text(info, color = color, fontSize = 11.sp, lineHeight = 14.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            onClick = onClick,
            action = if (managing) {
                null
            } else {
                {
                    Box {
                        OfflineIconButton(AppIcon.MORE, "更多：$title", { if (enabled) menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("删除缓存", color = colors.error) }, onClick = {
                                menu = false
                                onDelete()
                            }, enabled = enabled)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun OfflineArtwork(entry: OfflineCacheEntry, badge: String, modifier: Modifier) {
    val media = entry.media
    val bitmap = rememberVideoThumbnail(media.key, cachedVideoThumbnail(media.key)) { context ->
        if (entry.audioOnly) null else loadVideoThumbnail(context, media.uri, media.key)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (entry.audioOnly) {
            Box(Modifier.fillMaxSize().background(coverBrush(entry.bvid)), contentAlignment = Alignment.Center) {
                AppGlyphIcon(AppIcon.WAVEFORM, Color.White.copy(alpha = .85f), Modifier.size(30.dp))
            }
        } else {
            VideoCoverPlaceholder(Modifier.fillMaxSize())
        }
        VideoCoverBadge(badge, Modifier.align(Alignment.BottomEnd))
    }
}
