package io.github.bilibox.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.core.util.formatBytes

/** 缓存占用与可用空间的紧凑信息条，不用整行分隔线占据页面底部。 */
@Composable
internal fun OfflineStorageSummary(cacheBytes: Long, freeBytes: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.onBackground.copy(alpha = .035f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppGlyphIcon(AppIcon.DOWNLOAD, LocalAccentColor.current.copy(alpha = .8f), Modifier.size(16.dp))
        Text(
            "缓存 ${formatBytes(cacheBytes)}",
            color = colors.onBackground.copy(alpha = .78f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "可用 ${formatBytes(freeBytes)}",
            color = colors.onSurfaceVariant.copy(alpha = .75f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
