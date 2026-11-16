package io.github.bilibox.feature.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.playback.PlayerSidePanel
import io.github.bilibox.core.ui.component.playback.PlayerSidePanelMuted
import io.github.bilibox.core.ui.component.playback.PlayerSidePanelTitle
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackCard
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.localvideo.LocalVideoBookmark

@Composable
internal fun LocalVideoBookmarks(state: LocalVideoBookmarkState, inSidePanel: Boolean = false) {
    val ink = if (inSidePanel) PlayerSidePanelTitle else PlaybackInk
    Column(Modifier.fillMaxWidth().padding(horizontal = if (inSidePanel) 0.dp else 18.dp, vertical = 14.dp)) {
        if (state.items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有标记", color = ink, fontSize = 15.sp)
            }
        } else {
            state.items.forEach { bookmark ->
                key(bookmark.id) { BookmarkRow(bookmark, state, inSidePanel) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: LocalVideoBookmark, state: LocalVideoBookmarkState, inSidePanel: Boolean) {
    val time = formatPlaybackTime(bookmark.positionMs)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(if (inSidePanel) Color.White.copy(alpha = .05f) else PlaybackCard, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f)
                .clickable(role = Role.Button) { state.jump(bookmark) }
                .semantics { contentDescription = "跳转到标记 $time" }
                .padding(start = 14.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, color = OnlinePink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(14.dp))
            Text(
                bookmark.note.ifBlank { "未命名标记" },
                color = if (inSidePanel) PlayerSidePanelTitle else PlaybackInk,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "管理标记 $time" }) {
                AppGlyphIcon(AppIcon.MORE, if (inSidePanel) PlayerSidePanelMuted else PlaybackMuted, Modifier.size(20.dp))
            }
            BookmarkActionsMenu(bookmark, state, menuOpen, inSidePanel) { menuOpen = false }
        }
    }
}

@Composable
internal fun LocalVideoBookmarkOverlays(state: LocalVideoBookmarkState) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BackHandler(enabled = landscape && state.sheetOpen) { state.sheetOpen = false }
    if (landscape) {
        PlayerSidePanelHost(
            visible = state.sheetOpen,
            onDismiss = { state.sheetOpen = false },
            scrimDescription = "关闭标记面板",
        ) {
            PlayerSidePanel(
                title = "标记",
                titleBadge = "(${state.items.size})",
                onDismiss = { state.sheetOpen = false },
                closeDescription = "收起标记面板",
            ) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    LocalVideoBookmarks(state, inSidePanel = true)
                }
            }
        }
    }
    LocalVideoBookmarkDialogs(state, forceDark = landscape)
}
