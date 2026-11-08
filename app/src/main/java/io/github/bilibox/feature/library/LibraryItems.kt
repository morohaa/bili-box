package io.github.bilibox.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.VideoCoverPlaceholder
import io.github.bilibox.core.ui.component.coverBrush
import io.github.bilibox.core.ui.component.rememberVideoThumbnail
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
import io.github.bilibox.data.localvideo.cachedVideoThumbnail
import io.github.bilibox.data.localvideo.loadVideoThumbnail

/** 封面圆角，比卡片小一号。 */
private val CoverShape = RoundedCornerShape(10.dp)

/**
 * 白卡片底：只用底色和圆角分层，去掉投影与描边。
 *
 * 阴影在浅色画布上会发灰、显脏，这个页面靠「浅灰画布 + 纯白卡片 + 充足留白」
 * 区分层级，夜间模式才补一条极淡的描边。
 */
@Composable
private fun Modifier.libraryCard(radius: Dp = 16.dp): Modifier {
    val palette = LocalLibraryPalette.current
    return this.fillMaxWidth().clip(RoundedCornerShape(radius)).background(palette.card)
}

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
    ResetScroll(
        state,
        settings.layout,
        settings.sort,
        settings.ascending,
        view.currentPath,
        view.searching,
        view.activeFilter,
        view.videos.size,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderRow(
                folder = folder,
                settings = settings,
                modifier = Modifier.animateItem(),
                onClick = { onFolder(folder) },
            )
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoRow(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                modifier = Modifier.animateItem(),
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
    ResetScroll(
        state,
        settings.layout,
        settings.sort,
        settings.ascending,
        view.currentPath,
        view.searching,
        view.activeFilter,
        view.videos.size,
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderCard(
                folder = folder,
                settings = settings,
                modifier = Modifier.animateItem(),
                onClick = { onFolder(folder) },
            )
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoCard(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Row(
        modifier.libraryCard()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderCoverTile(folder, settings, Modifier.width(116.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 15.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            MetaText(folderFootParts(folder, settings), fontSize = 11.5.sp, maxLines = 1)
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                FolderPathHint(folder)
            }
        }
        Spacer(Modifier.width(6.dp))
        AppGlyphIcon(AppIcon.CHEVRON_RIGHT, palette.ink3, Modifier.size(17.dp), 2.2f)
    }
}

/** 文件夹封面：优先用最新视频的缩略图，没有就画默认插画。 */
@Composable
internal fun FolderCoverTile(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    val cover = folder.cover
    val bitmap = cover?.let { video ->
        rememberVideoThumbnail(video.key, cachedVideoThumbnail(video.key)) { context ->
            loadVideoThumbnail(context, video.uri, video.key)
        }
    }
    Box(
        modifier.aspectRatio(16f / 9f)
            .clip(CoverShape)
            .background(coverBrush(folder.path)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            VideoCoverPlaceholder(Modifier.fillMaxSize())
        }
        if (settings.folderFields.status && folder.unplayedCount > 0) {
            UnreadDot(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

/** 有新内容的文件夹：一枚带白色描边的小圆点，代替大块 NEW 标签。 */
@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
    val palette = LocalLibraryPalette.current
    Box(
        modifier.size(9.dp)
            .background(Color.White.copy(alpha = .92f), CircleShape)
            .padding(1.5.dp)
            .background(palette.accent, CircleShape),
    )
}

@Composable
internal fun VideoRow(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalLibraryPalette.current
    if (compact) {
        CompactVideoRow(video, settings, playback, Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
        return
    }
    val watched = playback.state == LocalVideoPlaybackState.Watched
    Row(
        modifier.libraryCard()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.fields.thumbnail) {
            VideoThumbnail(
                video = video,
                settings = settings,
                playback = playback,
                modifier = Modifier.width(116.dp),
            )
        } else {
            FileGlyphTile()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = if (watched) palette.ink2 else palette.ink,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                MetaText(meta, fontSize = 12.sp, maxLines = 2)
            }
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}",
                    color = palette.ink3,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        VideoMoreMenu(onPlay = onClick, onShare = onShare, onInfo = onInfo)
    }
}

/** 设置弹窗里的紧凑预览行，只保留封面、标题与信息行。 */
@Composable
internal fun CompactVideoRow(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (settings.fields.thumbnail) {
            VideoThumbnail(
                video = video,
                settings = settings,
                playback = playback,
                modifier = Modifier.width(96.dp),
            )
        } else {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphIcon(AppIcon.FILE, palette.accent, Modifier.size(22.dp), 1.7f)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** 设置弹窗里的紧凑文件夹预览行。 */
@Composable
internal fun CompactFolderRow(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FolderCoverTile(folder, settings, Modifier.width(96.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                MetaText(meta, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            MetaText(folderFootParts(folder, settings), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FileGlyphTile() {
    val palette = LocalLibraryPalette.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(AppIcon.FILE, palette.accent, Modifier.size(23.dp), 1.7f)
    }
}

@Composable
internal fun VideoMoreMenu(
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onThumbnail: Boolean = false,
) {
    val palette = LocalLibraryPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(26.dp)
                .clip(CircleShape)
                .background(if (onThumbnail) Color.Black.copy(alpha = .3f) else Color.Transparent)
                .semantics { contentDescription = "更多操作" }
                .pressFeedback(onClick = { expanded = true }, shape = CircleShape, scaleTo = .88f),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(
                AppIcon.MORE,
                if (onThumbnail) Color.White else palette.ink3,
                Modifier.size(15.dp),
                2f,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = palette.card,
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppIcon.PLAY, palette.accent, Modifier.size(15.dp), 1.8f)
                        Spacer(Modifier.width(9.dp))
                        Text("播放", color = palette.ink, fontSize = 13.sp)
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
                        AppGlyphIcon(AppIcon.SHARE, palette.ink2, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("分享", color = palette.ink, fontSize = 13.sp)
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
                        AppGlyphIcon(AppIcon.INFO, palette.ink2, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("文件信息", color = palette.ink, fontSize = 13.sp)
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
    showDuration: Boolean = true,
) {
    val palette = LocalLibraryPalette.current
    val bitmap = rememberVideoThumbnail(video.key, cachedVideoThumbnail(video.key)) { context ->
        loadVideoThumbnail(context, video.uri, video.key)
    }
    Box(
        modifier.aspectRatio(16f / 9f)
            .clip(CoverShape)
            .background(coverBrush(video.title)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            VideoCoverPlaceholder(Modifier.fillMaxSize())
        }
        if (showStatus) {
            when (playback.state) {
                LocalVideoPlaybackState.Unplayed -> UnreadDot(
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                )

                LocalVideoPlaybackState.Watched -> Unit

                is LocalVideoPlaybackState.Watching -> Unit
            }
        }
        if (showDuration && settings.fields.duration && settings.durationOnThumbnail && video.durationMs > 0L) {
            Text(
                formatVideoDuration(video.durationMs),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = if (playback.state is LocalVideoPlaybackState.Watching) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = .5f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (playback.state is LocalVideoPlaybackState.Watching) {
            val fraction = playback.state.fraction.coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.5.dp)
                    .background(Color.White.copy(alpha = .32f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction).background(palette.accent),
                )
            }
        }
    }
}

internal data class MetaPart(val text: String, val highlight: Boolean = false)

@Composable
internal fun MetaText(
    parts: List<MetaPart>,
    fontSize: TextUnit = 12.sp,
    maxLines: Int = 2,
) {
    if (parts.isEmpty()) return
    val palette = LocalLibraryPalette.current
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                withStyle(SpanStyle(color = palette.ink3.copy(alpha = .75f))) { append(" · ") }
            }
            withStyle(SpanStyle(color = if (part.highlight) palette.accent else palette.ink3)) {
                append(part.text)
            }
        }
    }
    Text(
        text,
        fontSize = fontSize,
        lineHeight = fontSize * 1.4f,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun VideoCard(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val watched = playback.state == LocalVideoPlaybackState.Watched
    Column(
        modifier.libraryCard()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (settings.fields.thumbnail) {
                VideoThumbnail(video, settings, playback, Modifier.fillMaxWidth())
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(CoverShape)
                        .background(coverBrush(video.title)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppIcon.FILE, Color.White.copy(alpha = .9f), Modifier.size(26.dp), 1.5f)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                video.title,
                color = if (watched) palette.ink2 else palette.ink,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 36.dp),
            )
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                MetaText(meta, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun FolderCard(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(
        modifier.libraryCard()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape)
            .padding(8.dp),
    ) {
        FolderCoverTile(folder, settings, Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                MetaText(meta, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            MetaText(folderFootParts(folder, settings), fontSize = 11.sp, maxLines = 1)
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
    val palette = LocalLibraryPalette.current
    Row(
        modifier.fillMaxWidth().height(58.dp)
            .shadow(
                12.dp,
                RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF101215).copy(alpha = .10f),
                spotColor = Color(0xFF101215).copy(alpha = .12f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(24.dp), scaleTo = .985f)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).height(46.dp).clip(RoundedCornerShape(16.dp))) {
            VideoThumbnail(
                video = video,
                settings = settings,
                playback = LocalVideoPlayback(playback),
                modifier = Modifier.fillMaxSize(),
                showStatus = false,
                showDuration = false,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "继续观看 ${formatVideoDuration(playback.positionMs)} / ${formatVideoDuration(video.durationMs)}",
                color = palette.ink3,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.PLAY, palette.onAccent, Modifier.size(16.dp), 1.8f)
        }
        Spacer(Modifier.width(4.dp))
    }
}
