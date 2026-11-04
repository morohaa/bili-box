package io.github.bilibox.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoField
import io.github.bilibox.data.localvideo.LocalVideoLayout
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSort
import io.github.bilibox.data.localvideo.LocalVideoViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVideoSettingsSheet(
    applied: LocalVideoSettings,
    draft: LocalVideoSettings,
    sample: LocalVideo?,
    samplePlayback: LocalVideoPlayback,
    onDraftChange: (LocalVideoSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onRescan: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun toggleSection(id: String) {
        collapsed = if (id in collapsed) collapsed - id else collapsed + id
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = .34f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 2.dp).width(36.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFFDADCE0)),
            )
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "显示设置",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onReset)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocalGlyphIcon(LocalIcon.REFRESH, Muted, Modifier.size(13.dp), 2f)
                    Spacer(Modifier.width(4.dp))
                    Text("恢复默认", color = Muted, fontSize = 11.5.sp)
                }
            }
            HorizontalDivider(color = Color(0xFFF1F2F3))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                SettingsSection(
                    title = "视图",
                    subtitle = null,
                    collapsed = "view" in collapsed,
                    onToggle = { toggleSection("view") },
                ) {
                    ViewModePicker(draft, onDraftChange)
                }
                SettingsSection(
                    title = "排序",
                    subtitle = null,
                    collapsed = "sort" in collapsed,
                    onToggle = { toggleSection("sort") },
                ) {
                    SortPicker(draft, onDraftChange)
                }
                SettingsSection(
                    title = "显示字段",
                    subtitle = "实时预览",
                    collapsed = "fields" in collapsed,
                    onToggle = { toggleSection("fields") },
                ) {
                    FieldPicker(draft, onDraftChange)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF6F7F8))
                            .padding(vertical = 2.dp),
                    ) {
                        if (sample != null) {
                            VideoRow(
                                video = sample,
                                settings = draft,
                                playback = samplePlayback,
                                onClick = {},
                                onShare = {},
                                onInfo = {},
                                compact = true,
                            )
                        } else {
                            Text(
                                "暂无视频可预览",
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
                SettingsSection(
                    title = "高级",
                    subtitle = null,
                    collapsed = "advanced" in collapsed,
                    onToggle = { toggleSection("advanced") },
                ) {
                    ToggleRow(
                        title = "在缩略图上显示长度",
                        subtitle = "关闭后长度显示在信息行",
                        checked = draft.durationOnThumbnail,
                        onChange = { onDraftChange(draft.copy(durationOnThumbnail = it)) },
                    )
                    ToggleRow(
                        title = "显示隐藏文件和文件夹",
                        subtitle = "以 “.” 开头的目录，部分系统不会索引",
                        checked = draft.showHidden,
                        onChange = { onDraftChange(draft.copy(showHidden = it)) },
                    )
                    ToggleRow(
                        title = "识别 .nomedia",
                        subtitle = "跳过含 .nomedia 的目录，部分系统不会索引",
                        checked = draft.recognizeNomedia,
                        onChange = { onDraftChange(draft.copy(recognizeNomedia = it)) },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("重新扫描媒体库", color = Ink, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("视频有增减时使用，会保留当前设置", color = Muted, fontSize = 10.5.sp)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(13.dp)).background(LibrarySoftPink)
                                .clickable(onClick = onRescan)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LocalGlyphIcon(LocalIcon.REFRESH, Pink, Modifier.size(13.dp), 2f)
                            Spacer(Modifier.width(4.dp))
                            Text("扫描", color = Pink, fontSize = 11.5.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = Color(0xFFF1F2F3))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetButton("取消", false, Modifier.weight(1f), onDismiss)
                SheetButton(if (draft == applied) "完成" else "应用", true, Modifier.weight(1f), onApply)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (collapsed) 180f else 0f, label = "section chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.width(8.dp))
                Text(subtitle, color = Muted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.weight(1f))
            LocalGlyphIcon(
                LocalIcon.CHEVRON_DOWN,
                Muted,
                Modifier.size(16.dp).rotate(rotation),
                2f,
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) { content() }
        }
        HorizontalDivider(color = Color(0xFFF1F2F3))
    }
}

@Composable
private fun ViewModePicker(
    draft: LocalVideoSettings,
    onDraftChange: (LocalVideoSettings) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedGroup(
            options = LocalVideoViewMode.entries.map { ModeOption(it, modeIcon(it), it.label) },
            selected = draft.mode,
            modifier = Modifier.weight(3f),
        ) { mode -> onDraftChange(draft.copy(mode = mode)) }
        SegmentedGroup(
            options = LocalVideoLayout.entries.map { ModeOption(it, layoutIcon(it), it.label) },
            selected = draft.layout,
            modifier = Modifier.weight(2f),
        ) { layout -> onDraftChange(draft.copy(layout = layout)) }
    }
}

private data class ModeOption<T>(val value: T, val icon: LocalIcon, val label: String)

private fun modeIcon(mode: LocalVideoViewMode): LocalIcon = when (mode) {
    LocalVideoViewMode.ALL_FOLDERS -> LocalIcon.FOLDER
    LocalVideoViewMode.FILES -> LocalIcon.FILE
    LocalVideoViewMode.FOLDERS -> LocalIcon.FOLDER
}

private fun layoutIcon(layout: LocalVideoLayout): LocalIcon = when (layout) {
    LocalVideoLayout.LIST -> LocalIcon.LIST
    LocalVideoLayout.GRID -> LocalIcon.GRID
}

@Composable
private fun <T> SegmentedGroup(
    options: List<ModeOption<T>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF6F7F8))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option.value == selected
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(option.value) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LocalGlyphIcon(option.icon, if (active) Pink else LibrarySecondary, Modifier.size(19.dp), 1.8f)
                Spacer(Modifier.height(3.dp))
                Text(
                    option.label,
                    color = if (active) Pink else LibrarySecondary,
                    fontSize = 9.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun sortIcon(sort: LocalVideoSort): LocalIcon = when (sort) {
    LocalVideoSort.TITLE -> LocalIcon.TITLE
    LocalVideoSort.DATE -> LocalIcon.CALENDAR
    LocalVideoSort.PLAYED -> LocalIcon.PLAYED
    LocalVideoSort.STATUS -> LocalIcon.STATUS
    LocalVideoSort.DURATION -> LocalIcon.TIMER
    LocalVideoSort.SIZE -> LocalIcon.SIZE
    LocalVideoSort.RESOLUTION -> LocalIcon.FRAME
    LocalVideoSort.PATH -> LocalIcon.PIN
    LocalVideoSort.FPS -> LocalIcon.FILM
    LocalVideoSort.TYPE -> LocalIcon.DOCUMENT
}

@Composable
private fun SortPicker(
    draft: LocalVideoSettings,
    onDraftChange: (LocalVideoSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LocalVideoSort.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { sort ->
                    val active = draft.sort == sort
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (draft.sort == sort) {
                                    onDraftChange(draft.copy(ascending = !draft.ascending))
                                } else {
                                    onDraftChange(draft.copy(sort = sort, ascending = sort.defaultAscending))
                                }
                            }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) LibrarySoftPink else Color(0xFFF6F7F8))
                                .then(
                                    if (active) {
                                        Modifier.border(1.5.dp, Pink, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LocalGlyphIcon(
                                sortIcon(sort),
                                if (active) Pink else Color(0xFF505660),
                                Modifier.size(22.dp),
                                1.8f,
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            sort.label,
                            color = if (active) Pink else LibrarySecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF6F7F8))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            DirectionButton(
                label = draft.sort.ascendingLabel,
                icon = LocalIcon.ARROW_UP,
                active = draft.ascending,
                modifier = Modifier.weight(1f),
            ) { onDraftChange(draft.copy(ascending = true)) }
            DirectionButton(
                label = draft.sort.descendingLabel,
                icon = LocalIcon.ARROW_DOWN,
                active = !draft.ascending,
                modifier = Modifier.weight(1f),
            ) { onDraftChange(draft.copy(ascending = false)) }
        }
    }
}

@Composable
private fun DirectionButton(
    label: String,
    icon: LocalIcon,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalGlyphIcon(icon, if (active) Pink else LibrarySecondary, Modifier.size(15.dp), 2f)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = if (active) Pink else LibrarySecondary,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun FieldPicker(
    draft: LocalVideoSettings,
    onDraftChange: (LocalVideoSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LocalVideoField.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { field ->
                    val active = draft.fields.isEnabled(field)
                    Row(
                        Modifier.weight(1f).height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) LibrarySoftPink else Color(0xFFF6F7F8))
                            .clickable { onDraftChange(draft.copy(fields = draft.fields.with(field, !active))) }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            LocalGlyphIcon(LocalIcon.CHECK, Pink, Modifier.size(13.dp), 2.4f)
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            field.label,
                            color = if (active) Pink else LibrarySecondary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Muted, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Pink,
                checkedBorderColor = Pink,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE3E5E7),
                uncheckedBorderColor = Color(0xFFE3E5E7),
            ),
        )
    }
}

@Composable
private fun SheetButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Pink else Color(0xFFF4F6F8))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.White else LibrarySecondary,
            fontSize = 14.5.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
