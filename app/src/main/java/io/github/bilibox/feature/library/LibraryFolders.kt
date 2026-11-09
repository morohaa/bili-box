package io.github.bilibox.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.VideoCoverPlaceholder
import io.github.bilibox.core.ui.component.rememberVideoThumbnail
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.cachedVideoThumbnail
import io.github.bilibox.data.localvideo.loadVideoThumbnail

/** 封面、标题和信息沿同一基线排布，不再为每一行套一层白色胶囊。 */
@Composable
internal fun FolderRow(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Row(
        modifier.fillMaxWidth()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape, scaleTo = .99f)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderCoverTile(folder, settings, Modifier.width(108.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaText(folderFootParts(folder, settings), fontSize = 11.5.sp, maxLines = 1, color = palette.ink2)
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) MetaText(meta, fontSize = 10.5.sp, maxLines = 1)
            if (settings.fields.path) FolderPathHint(folder)
        }
        Spacer(Modifier.width(8.dp))
        AppGlyphIcon(AppIcon.CHEVRON_RIGHT, palette.ink3.copy(alpha = .6f), Modifier.size(14.dp), 1.7f)
    }
}

/** 两张轻薄的衬页露出封面上缘，让文件夹与单个视频自然区分。 */
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
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.aspectRatio(1.44f)) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(15.dp)
                .clip(RoundedCornerShape(8.dp)).background(palette.line.copy(alpha = .65f)),
        )
        Box(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp).height(15.dp)
                .clip(RoundedCornerShape(9.dp)).background(palette.line),
        )
        Box(
            Modifier.fillMaxSize().padding(top = 8.dp).clip(shape).background(palette.chip),
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .42f))),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppGlyphIcon(AppIcon.FOLDER, Color.White.copy(alpha = .9f), Modifier.size(12.dp), 1.8f)
                if (settings.folderFields.status && folder.unplayedCount > 0) {
                    Text(
                        "未看",
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier.semantics {
                            contentDescription = "${folder.unplayedCount} 个未播放视频"
                        },
                    )
                }
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
        modifier.fillMaxWidth()
            .pressFeedback(onClick = onClick, shape = LibraryCardShape, scaleTo = .98f),
    ) {
        FolderCoverTile(folder, settings, Modifier.fillMaxWidth())
        Column(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaText(folderFootParts(folder, settings), fontSize = 11.sp, maxLines = 1, color = palette.ink2)
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) MetaText(meta, fontSize = 10.sp, maxLines = 1)
            if (settings.fields.path) FolderPathHint(folder)
        }
    }
}

/** 显示设置的预览复用相同的封面与信息顺序。 */
@Composable
internal fun CompactFolderRow(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FolderCoverTile(folder, settings, Modifier.width(96.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                folder.name,
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaText(folderFootParts(folder, settings), fontSize = 11.sp, maxLines = 1, color = palette.ink2)
            val meta = folderMetaParts(folder, settings)
            if (meta.isNotEmpty()) MetaText(meta, fontSize = 10.sp, maxLines = 1)
            if (settings.fields.path) FolderPathHint(folder)
        }
    }
}
