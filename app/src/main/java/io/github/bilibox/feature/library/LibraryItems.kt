package io.github.bilibox.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoLayout
import io.github.bilibox.data.localvideo.LocalVideoLibraryView
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSort

@Composable
internal fun VideoList(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    playback: Map<String, LocalVideoPlayback>,
    bottomPadding: Dp,
    onFolder: (LocalVideoFolder) -> Unit,
    onVideo: (LocalVideo) -> Unit,
    onShare: (LocalVideo) -> Unit,
    onInfo: (LocalVideo) -> Unit,
) {
    val state = rememberLazyListState()
    ResetScroll(state, settings.layout, settings.sort, settings.ascending, view.currentPath, view.searching, view.activeFilter, view.videos.size)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderRow(folder, settings, onClick = { onFolder(folder) })
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoRow(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onClick = { onVideo(video) },
                onShare = { onShare(video) },
                onInfo = { onInfo(video) },
            )
        }
    }
}

@Composable
internal fun VideoGrid(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    playback: Map<String, LocalVideoPlayback>,
    bottomPadding: Dp,
    onFolder: (LocalVideoFolder) -> Unit,
    onVideo: (LocalVideo) -> Unit,
    onShare: (LocalVideo) -> Unit,
    onInfo: (LocalVideo) -> Unit,
) {
    val state = rememberLazyGridState()
    ResetScroll(state, settings.layout, settings.sort, settings.ascending, view.currentPath, view.searching, view.activeFilter, view.videos.size)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderCard(folder, settings, onClick = { onFolder(folder) })
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoCard(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onClick = { onVideo(video) },
                onShare = { onShare(video) },
                onInfo = { onInfo(video) },
            )
        }
    }
}

@Composable
internal fun ResetScroll(
    state: LazyListState,
    layout: LocalVideoLayout,
    sort: LocalVideoSort,
    ascending: Boolean,
    path: String?,
    searching: Boolean,
    filter: LocalVideoQuickFilter?,
    count: Int,
) {
    LaunchedEffect(layout, sort, ascending, path, searching, filter, count) {
        if (layout == LocalVideoLayout.LIST) runCatching { state.scrollToItem(0) }
    }
}

@Composable
internal fun ResetScroll(
    state: LazyGridState,
    layout: LocalVideoLayout,
    sort: LocalVideoSort,
    ascending: Boolean,
    path: String?,
    searching: Boolean,
    filter: LocalVideoQuickFilter?,
    count: Int,
) {
    LaunchedEffect(layout, sort, ascending, path, searching, filter, count) {
        if (layout == LocalVideoLayout.GRID) runCatching { state.scrollToItem(0) }
    }
}

@Composable
internal fun FolderRow(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderIcon(folder)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                color = Ink,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            MetaText(folderMetaParts(folder, settings))
            if (settings.fields.path) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "内部存储 / ${folder.path}",
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        AppGlyphIcon(AppIcon.CHEVRON_RIGHT, Muted.copy(alpha = .62f), Modifier.size(17.dp), 1.8f)
    }
}

@Composable
internal fun FolderIcon(folder: LocalVideoFolder) {
    // Leave room above the tile so the unread badge can sit fully outside it.
    Box(Modifier.size(54.dp)) {
        Box(
            Modifier.size(46.dp).align(Alignment.BottomStart)
                .clip(RoundedCornerShape(14.dp))
                .background(LibrarySoftPink),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.FOLDER, Pink, Modifier.size(24.dp), 1.7f)
        }
        if (folder.unplayedCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 5.dp)
                    .widthIn(min = 18.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(LibraryBg)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Pink)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${folder.unplayedCount}",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun VideoRow(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    compact: Boolean = false,
) {
    val watched = playback.state == LocalVideoPlaybackState.Watched
    val thumbWidth = if (compact) 96.dp else 122.dp
    val thumbHeight = if (compact) 54.dp else 69.dp
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 0.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (settings.fields.thumbnail) {
            VideoThumbnail(
                video = video,
                settings = settings,
                playback = playback,
                modifier = Modifier.width(thumbWidth).height(thumbHeight),
            )
        } else {
            Box(
                Modifier.size(if (compact) 38.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LibrarySoftPink),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphIcon(AppIcon.FILE, Pink, Modifier.size(if (compact) 20.dp else 23.dp), 1.7f)
            }
        }
        Spacer(Modifier.width(if (compact) 9.dp else 11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    video.title,
                    color = if (watched) Muted else Ink,
                    fontSize = if (compact) 13.sp else 14.5.sp,
                    lineHeight = if (compact) 17.sp else 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (settings.fields.extension) ExtensionBadge(video.extension)
            }
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, maxLines = if (compact) 1 else 2)
            }
            if (settings.fields.path) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}",
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            VideoMoreMenu(onPlay = onClick, onShare = onShare, onInfo = onInfo)
        }
    }
}

@Composable
internal fun VideoMoreMenu(
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onThumbnail: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(28.dp)
                .clip(CircleShape)
                .background(if (onThumbnail) Color.Black.copy(alpha = .3f) else Color.Transparent)
                .semantics { contentDescription = "更多操作" }
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.MORE, if (onThumbnail) Color.White else Muted, Modifier.size(16.dp), 1.8f)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppIcon.PLAY, Pink, Modifier.size(15.dp), 1.8f)
                        Spacer(Modifier.width(9.dp))
                        Text("播放", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppIcon.SHARE, LibrarySecondary, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("分享", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppIcon.INFO, LibrarySecondary, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("文件信息", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onInfo()
                },
            )
        }
    }
}

@Composable
internal fun VideoThumbnail(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
) {
    val bitmap = rememberVideoThumbnail(video)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(coverBrush(video.title))) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Color.White.copy(alpha = .07f),
                radius = size.minDimension * .52f,
                center = Offset(size.width * .78f, size.height * .28f),
            )
            drawCircle(
                Color.Black.copy(alpha = .05f),
                radius = size.minDimension * .46f,
                center = Offset(size.width * .12f, size.height * .9f),
            )
        }
        if (showStatus) {
            when (playback.state) {
                LocalVideoPlaybackState.Unplayed -> ThumbBadge("NEW", Pink, Modifier.align(Alignment.TopStart))
                LocalVideoPlaybackState.Watched -> ThumbBadge("已看完", Color(0xFF4B5560), Modifier.align(Alignment.TopStart))
                is LocalVideoPlaybackState.Watching -> Unit
            }
        }
        if (settings.fields.duration && settings.durationOnThumbnail && video.durationMs > 0L) {
            Text(
                formatVideoDuration(video.durationMs),
                color = Color.White,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = if (playback.state is LocalVideoPlaybackState.Watching) 7.dp else 4.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (playback.state is LocalVideoPlaybackState.Watching) {
            val fraction = playback.state.fraction
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = .38f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(Pink),
                )
            }
        }
    }
}

@Composable
internal fun ThumbBadge(text: String, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(4.dp)
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
internal fun ExtensionBadge(extension: String) {
    if (extension.isBlank()) return
    Text(
        extension.uppercase(),
        color = Muted,
        fontSize = 8.5.sp,
        lineHeight = 12.sp,
        modifier = Modifier.padding(start = 5.dp, top = 2.dp)
            .border(1.dp, LibraryLine, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

internal data class MetaPart(val text: String, val highlight: Boolean = false)

@Composable
internal fun MetaText(parts: List<MetaPart>, maxLines: Int = 2) {
    if (parts.isEmpty()) return
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                withStyle(SpanStyle(color = Muted.copy(alpha = .65f))) { append("  ·  ") }
            }
            withStyle(SpanStyle(color = if (part.highlight) Pink else Muted)) { append(part.text) }
        }
    }
    Text(text, fontSize = 10.5.sp, lineHeight = 15.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun VideoCard(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
) {
    val watched = playback.state == LocalVideoPlaybackState.Watched
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LibraryCard)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (settings.fields.thumbnail) {
                VideoThumbnail(video, settings, playback, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(coverBrush(video.title)), contentAlignment = Alignment.Center) {
                    AppGlyphIcon(AppIcon.FILE, Color.White.copy(alpha = .9f), Modifier.size(28.dp), 1.5f)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                VideoMoreMenu(onPlay = onClick, onShare = onShare, onInfo = onInfo, onThumbnail = true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    video.title,
                    color = if (watched) Muted else Ink,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                )
                if (settings.fields.extension) ExtensionBadge(video.extension)
            }
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, maxLines = 1)
            }
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }}",
                    color = Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FolderCard(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LibraryCard)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (settings.fields.thumbnail) {
                FolderCover(folder)
            } else {
                Box(
                    Modifier.fillMaxSize().background(coverBrush(folder.path)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = .82f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppGlyphIcon(AppIcon.FOLDER, Pink, Modifier.size(26.dp), 1.7f)
                    }
                }
            }
            if (folder.unplayedCount > 0) {
                ThumbBadge("${folder.unplayedCount} 新", Pink, Modifier.align(Alignment.TopStart))
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .background(Color.Black.copy(alpha = .52f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphIcon(AppIcon.FOLDER, Color.White, Modifier.size(11.dp), 2f)
                Spacer(Modifier.width(3.dp))
                Text("${folder.count}", color = Color.White, fontSize = 9.5.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 9.dp)) {
            Text(
                folder.name,
                color = Ink,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            MetaText(folderMetaParts(folder, settings, includeDuration = false), maxLines = 1)
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "内部存储 / ${folder.path}",
                    color = Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FolderCover(folder: LocalVideoFolder) {
    val cover = folder.cover
    val bitmap = cover?.let { rememberVideoThumbnail(it) }
    Box(Modifier.fillMaxSize().background(coverBrush(folder.path))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .32f)),
                        startY = size.height * .4f,
                    )
                )
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Color.White.copy(alpha = .1f),
                    radius = size.minDimension * .5f,
                    center = Offset(size.width * .76f, size.height * .3f),
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .82f)),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphIcon(AppIcon.FOLDER, Pink, Modifier.size(26.dp), 1.7f)
            }
        }
    }
}

@Composable
internal fun ContinueWatchingBar(
    video: LocalVideo,
    playback: LocalVideoPlaybackState.Watching,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(60.dp)
            .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Color(0xFF5D7288).copy(alpha = .22f), spotColor = Color(0xFF5D7288).copy(alpha = .22f))
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = .94f))
            .border(1.dp, Color.White, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(68.dp).height(48.dp).clip(RoundedCornerShape(24.dp)),
        ) {
            VideoThumbnail(video, settings, LocalVideoPlayback(playback), Modifier.fillMaxSize(), showStatus = false)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = Ink,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "继续观看 · ${formatVideoDuration(playback.positionMs)} / ${formatVideoDuration(video.durationMs)}",
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(40.dp)
                .shadow(8.dp, CircleShape, spotColor = Pink.copy(alpha = .4f))
                .clip(CircleShape)
                .background(Pink),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.PLAY, Color.White, Modifier.size(18.dp), 1.8f)
        }
        Spacer(Modifier.width(4.dp))
    }
}
