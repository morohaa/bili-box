package io.github.bilibox.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.data.localvideo.LocalVideo

@Composable
internal fun VideoMoreMenu(
    video: LocalVideo,
    watched: Boolean,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    onThumbnail: Boolean = false,
) {
    val palette = LocalLibraryPalette.current
    var expanded by remember(video.key) { mutableStateOf(false) }
    val danger = if (palette.dark) Color(0xFFFF8F99) else Color(0xFFC84051)
    fun choose(action: () -> Unit) {
        expanded = false
        action()
    }
    Box(modifier) {
        Box(
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(if (onThumbnail) Color.Black.copy(alpha = .42f) else Color.Transparent)
                .semantics {
                    contentDescription = "更多操作"
                    role = Role.Button
                }
                .pressFeedback(onClick = { expanded = true }, shape = CircleShape, scaleTo = .9f),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.MORE, if (onThumbnail) Color.White else palette.ink2, Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(248.dp).testTag("library-video-menu"),
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.card,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, palette.line.copy(alpha = .7f)),
        ) {
            VideoMenuAction(
                label = if (watched) "设为未播放" else "设为已播完",
                icon = if (watched) AppIcon.REFRESH else AppIcon.CHECK,
                tint = lerp(palette.accent, palette.ink, if (palette.dark) .08f else .18f),
                highlighted = true,
                onClick = { choose(onToggleWatched) },
            )
            Spacer(Modifier.height(6.dp))
            VideoMenuAction("重命名", AppIcon.EDIT, onClick = { choose(onRename) })
            VideoMenuAction("分享", AppIcon.SHARE, onClick = { choose(onShare) })
            VideoMenuAction("文件信息", AppIcon.INFO, onClick = { choose(onInfo) })
            HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 7.dp), color = palette.line)
            VideoMenuAction("删除", AppIcon.DELETE, tint = danger, onClick = { choose(onDelete) })
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun VideoMenuAction(
    label: String,
    icon: AppIcon,
    tint: Color = LocalLibraryPalette.current.ink,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth()
            .background(if (highlighted) palette.accentSoft else Color.Transparent, shape)
            .pressFeedback(onClick, shape = shape, scaleTo = .985f)
            .heightIn(min = 46.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AppGlyphIcon(icon, tint, Modifier.size(20.dp), 1.7f)
        Text(label, color = tint, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium)
    }
}
