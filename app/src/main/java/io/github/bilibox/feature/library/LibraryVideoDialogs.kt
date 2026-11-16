package io.github.bilibox.feature.library

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
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.card,
        shape = RoundedCornerShape(26.dp),
        icon = { DialogGlyph(AppIcon.EDIT, palette.accent) },
        title = { Text("重命名视频", color = palette.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("修改文件名，保留原有视频格式。", color = palette.ink2, fontSize = 13.sp)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "视频文件名" },
                    label = { Text("文件名") },
                    suffix = { if (video.extension.isNotBlank()) Text(".${video.extension}", color = palette.ink3) },
                    singleLine = true,
                    isError = validation != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.ink,
                        unfocusedTextColor = palette.ink,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.line,
                        focusedLabelColor = palette.accent,
                        unfocusedLabelColor = palette.ink2,
                        cursorColor = palette.accent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(title) }),
                )
                (validation ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 18.sp) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消", color = palette.ink2) } },
        confirmButton = {
            Button(onClick = { onSave(title) }, enabled = canSave, colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)) {
                Text(if (busy) "保存中…" else "保存")
            }
        },
    )
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
