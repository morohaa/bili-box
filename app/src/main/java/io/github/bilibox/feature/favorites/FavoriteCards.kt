package io.github.bilibox.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.RemoteCover
import io.github.bilibox.core.ui.component.VideoListCard
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.favorites.FavoriteFolder
import io.github.bilibox.data.favorites.FavoriteVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FavoriteFolderCard(folder: FavoriteFolder, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RemoteCover(folder.coverUrl, Modifier.width(108.dp).aspectRatio(16f / 10f)) {
                AppGlyphIcon(AppIcon.FOLDER, LocalAccentColor.current, Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(folder.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${folder.count} 个收藏 · ${if (folder.isPublic) "公开" else "私密"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppGlyphIcon(AppIcon.CHEVRON_RIGHT, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun FavoriteVideoCard(item: FavoriteVideo, darkMode: Boolean, onClick: () -> Unit) {
    val saved = if (item.savedAt > 0) {
        "收藏于 ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.savedAt * 1000))}"
    } else {
        "收藏时间未知"
    }
    val video = item.video ?: VideoSummary(item.title, item.author, "--", 0, 0, item.duration, "", item.coverUrl)
    VideoListCard(
        video = video,
        darkMode = darkMode,
        additionalInfo = saved,
        statusText = item.unavailableReason,
        enabled = item.video != null,
        onClick = onClick,
    )
}
