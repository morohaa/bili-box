package io.github.bilibox.feature.player

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.R
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackCard
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.localvideo.LocalVideoBookmark

@Composable
internal fun LocalVideoBookmarks(state: LocalVideoBookmarkState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_player_bookmark), null, Modifier.size(21.dp), tint = OnlinePink)
            Spacer(Modifier.width(8.dp))
            Text("视频标记", color = PlaybackInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = state::add, enabled = state.canAdd) {
                Text("添加标记")
            }
        }
        if (state.items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有标记", color = PlaybackInk, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("播放到关键位置时，点击“添加标记”", color = PlaybackMuted, fontSize = 13.sp)
            }
        } else {
            Text("点击跳转 · 按时间排序", color = PlaybackMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            state.items.forEach { bookmark ->
                key(bookmark.id) { BookmarkRow(bookmark, state) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: LocalVideoBookmark, state: LocalVideoBookmarkState) {
    val time = formatPlaybackTime(bookmark.positionMs)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(PlaybackCard, RoundedCornerShape(12.dp)),
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
                color = PlaybackInk,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "管理标记 $time" }) {
                AppGlyphIcon(AppIcon.MORE, PlaybackMuted, Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("修改备注") }, onClick = {
                    menuOpen = false
                    state.editing = bookmark
                })
                DropdownMenuItem(text = { Text("删除标记") }, onClick = {
                    menuOpen = false
                    state.deleting = bookmark
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVideoBookmarkOverlays(state: LocalVideoBookmarkState) {
    if (state.sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { state.sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = PlaybackSurface,
        ) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                LocalVideoBookmarks(state)
            }
        }
    }
    state.editing?.let { bookmark ->
        var note by remember(bookmark.id) { mutableStateOf(bookmark.note) }
        AlertDialog(
            onDismissRequest = { state.editing = null },
            title = { Text("修改标记备注") },
            text = {
                Column {
                    Text(formatPlaybackTime(bookmark.positionMs), color = OnlinePink)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        label = { Text("备注（可选）") },
                        supportingText = { Text("${note.length}/200") },
                        maxLines = 4,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { state.rename(bookmark, note) }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { state.editing = null }) { Text("取消") } },
        )
    }
    state.deleting?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { state.deleting = null },
            title = { Text("删除标记？") },
            text = { Text("将删除 ${formatPlaybackTime(bookmark.positionMs)} 的标记。") },
            confirmButton = { TextButton(onClick = { state.delete(bookmark) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { state.deleting = null }) { Text("取消") } },
        )
    }
}
