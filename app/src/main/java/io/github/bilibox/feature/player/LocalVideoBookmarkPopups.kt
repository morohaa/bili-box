package io.github.bilibox.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.localvideo.LocalVideoBookmark

private data class BookmarkPopupColors(
    val surface: Color,
    val field: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val danger: Color,
)

@Composable
private fun bookmarkPopupColors(forceDark: Boolean): BookmarkPopupColors = if (forceDark || MaterialTheme.colorScheme.background.luminance() < .5f) {
    BookmarkPopupColors(Color(0xFF25272F), Color(0xFF1C1E25), Color(0xFFF2F3F6), Color(0xFF9B9EAA), Color(0xFF393C46), Color(0xFFFF7B85))
} else {
    BookmarkPopupColors(Color.White, Color(0xFFF5F6F8), Color(0xFF272A33), Color(0xFF7C8290), Color(0xFFE6E8ED), Color(0xFFD94A5B))
}

@Composable
internal fun BookmarkActionsMenu(
    bookmark: LocalVideoBookmark,
    state: LocalVideoBookmarkState,
    expanded: Boolean,
    forceDark: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = bookmarkPopupColors(forceDark)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(192.dp).testTag("bookmark-actions-menu"),
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlyphIcon(AppIcon.PIN, colors.muted, Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(formatPlaybackTime(bookmark.positionMs), color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        DropdownMenuItem(
            text = { Text("修改备注", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { AppGlyphIcon(AppIcon.EDIT, colors.ink, Modifier.size(18.dp)) },
            colors = MenuDefaults.itemColors(textColor = colors.ink),
            onClick = {
                onDismiss()
                state.editing = bookmark
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = colors.line)
        DropdownMenuItem(
            text = { Text("删除标记", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { AppGlyphIcon(AppIcon.DELETE, colors.danger, Modifier.size(18.dp)) },
            colors = MenuDefaults.itemColors(textColor = colors.danger),
            onClick = {
                onDismiss()
                state.deleting = bookmark
            },
        )
    }
}

@Composable
internal fun LocalVideoBookmarkDialogs(state: LocalVideoBookmarkState, forceDark: Boolean) {
    val colors = bookmarkPopupColors(forceDark)
    state.editing?.let { bookmark ->
        var note by rememberSaveable(bookmark.id) { mutableStateOf(bookmark.note) }
        BookmarkDialog(
            title = "修改标记备注",
            icon = AppIcon.EDIT,
            accent = OnlinePink,
            colors = colors,
            onDismiss = { state.editing = null },
            actions = { compact -> BookmarkDialogActions("保存", OnlinePink, colors, compact, { state.editing = null }) { state.rename(bookmark, note) } },
        ) { compact ->
            if (!compact) {
                BookmarkTimeCard(bookmark, colors)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("备注", color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text("可选", color = colors.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                placeholder = { Text("写下这一刻的重点…", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "标记备注" },
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                minLines = if (compact) 1 else 2,
                maxLines = if (compact) 1 else 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    focusedContainerColor = colors.field,
                    unfocusedContainerColor = colors.field,
                    focusedBorderColor = OnlinePink,
                    unfocusedBorderColor = colors.line,
                    cursorColor = OnlinePink,
                    focusedPlaceholderColor = colors.muted,
                    unfocusedPlaceholderColor = colors.muted,
                ),
            )
            if (!compact) Text("${note.length} / 200", color = colors.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
        }
    }
    state.deleting?.let { bookmark ->
        BookmarkDialog(
            title = "删除标记？",
            icon = AppIcon.DELETE,
            accent = colors.danger,
            colors = colors,
            onDismiss = { state.deleting = null },
            actions = { compact -> BookmarkDialogActions("删除", colors.danger, colors, compact, { state.deleting = null }) { state.delete(bookmark) } },
        ) { _ ->
            BookmarkTimeCard(bookmark, colors)
            Text(
                "删除后无法恢复，视频内容不受影响。",
                color = colors.muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun BookmarkDialog(
    title: String,
    icon: AppIcon,
    accent: Color,
    colors: BookmarkPopupColors,
    onDismiss: () -> Unit,
    actions: @Composable (compact: Boolean) -> Unit,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            BoxWithConstraints(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val compact = maxHeight < 280.dp
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(max = maxHeight)
                        .testTag("bookmark-dialog").pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    Column(Modifier.padding(if (compact) 8.dp else 22.dp)) {
                        if (!compact) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(accent.copy(alpha = .12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                    AppGlyphIcon(icon, accent, Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(title, color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content(compact) }
                        Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
                        actions(compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkTimeCard(bookmark: LocalVideoBookmark, colors: BookmarkPopupColors) {
    Row(
        Modifier.fillMaxWidth().background(colors.field, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppIcon.PIN, OnlinePink, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(formatPlaybackTime(bookmark.positionMs), color = OnlinePink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(bookmark.note.ifBlank { "未命名标记" }, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookmarkDialogActions(
    confirmText: String,
    accent: Color,
    colors: BookmarkPopupColors,
    compact: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.field, contentColor = colors.ink),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text("取消", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (accent.luminance() < .3f) Color.White else Color(0xFF181A20)),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text(confirmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}
