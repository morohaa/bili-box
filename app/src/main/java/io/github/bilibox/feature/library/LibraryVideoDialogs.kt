package io.github.bilibox.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.util.formatBytes
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.localVideoNameError
import io.github.bilibox.data.localvideo.localVideoRenamedFileName

@Composable
internal fun LibraryVideoDialogs(state: LibraryVideoActionsState) {
    state.renaming?.let { video ->
        RenameVideoDialog(video, state.busy, state.error, state::dismiss, state::rename)
    }
    state.deleting?.let { video ->
        DeleteVideoDialog(video, state.busy, state.error, state::dismiss, state::delete)
    }
}

@Composable
internal fun RenameVideoDialog(
    video: LocalVideo,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val palette = LocalLibraryPalette.current
    var title by rememberSaveable(video.key) { mutableStateOf(video.title) }
    val validation = localVideoNameError(title, video.extension)
    val canSave = !busy && validation == null && localVideoRenamedFileName(video, title) != video.name
    LibraryDetailDialog(
        title = "重命名视频",
        subtitle = "编辑本地视频的文件名",
        icon = AppIcon.EDIT,
        onDismiss = { if (!busy) onDismiss() },
        actions = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.ink2),
            ) { Text("取消") }
            Button(
                onClick = { onSave(title) },
                enabled = canSave,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.chip,
                    disabledContentColor = palette.ink3,
                ),
            ) { Text(if (busy) "保存中…" else "保存", fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("文件名", color = palette.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (video.extension.isNotBlank()) {
                    Text(
                        ".${video.extension}",
                        color = palette.ink2,
                        fontSize = 12.sp,
                        modifier = Modifier.background(palette.chip, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "视频文件名" },
                minLines = 3,
                maxLines = 5,
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                isError = validation != null,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.ink, unfocusedTextColor = palette.ink,
                    focusedContainerColor = palette.chip, unfocusedContainerColor = palette.chip,
                    disabledTextColor = palette.ink2, disabledContainerColor = palette.chip,
                    focusedBorderColor = palette.accent.copy(alpha = .65f), unfocusedBorderColor = palette.line,
                    cursorColor = palette.accent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(title) }),
            )
            Text("仅修改名称，文件格式保持不变", color = palette.ink3, fontSize = 12.sp, lineHeight = 18.sp)
            (validation ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 18.sp) }
        }
    }
}

@Composable
internal fun DeleteVideoDialog(video: LocalVideo, busy: Boolean, error: String?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalLibraryPalette.current
    val danger = if (palette.dark) Color(0xFFFF8F99) else Color(0xFFC84051)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.card,
        shape = RoundedCornerShape(26.dp),
        icon = { DialogGlyph(AppIcon.DELETE, danger) },
        title = { Text("删除这个视频？", color = palette.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().background(palette.chip, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(video.name, color = palette.ink, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(formatBytes(video.sizeBytes), color = palette.ink3, fontSize = 12.sp)
                }
                Text("将从设备中删除视频文件，此操作无法撤销。", color = palette.ink2, fontSize = 13.sp, lineHeight = 20.sp)
                error?.let { Text(it, color = danger, fontSize = 12.sp, lineHeight = 18.sp) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消", color = palette.ink2) } },
        confirmButton = {
            Button(onClick = onDelete, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = danger, contentColor = if (palette.dark) Color(0xFF301014) else Color.White)) {
                Text(if (busy) "删除中…" else "删除视频")
            }
        },
    )
}

@Composable
private fun DialogGlyph(icon: AppIcon, tint: Color) {
    Box(Modifier.size(48.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        AppGlyphIcon(icon, tint, Modifier.size(24.dp))
    }
}
