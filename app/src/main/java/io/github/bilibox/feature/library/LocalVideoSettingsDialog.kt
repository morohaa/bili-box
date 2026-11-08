package io.github.bilibox.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoField
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoFolderField
import io.github.bilibox.data.localvideo.LocalVideoLayout
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSort
import io.github.bilibox.data.localvideo.LocalVideoViewMode
import kotlinx.coroutines.delay

/** 排序面板首行展示的高频排序，其余收进「更多排序」。 */
private val PrimarySorts = listOf(
    LocalVideoSort.TITLE,
    LocalVideoSort.DURATION,
    LocalVideoSort.SIZE,
    LocalVideoSort.DATE,
    LocalVideoSort.STATUS,
)

private val ExtraSorts = LocalVideoSort.entries.filter { it !in PrimarySorts }

private val DialogShape = RoundedCornerShape(24.dp)
private val PanelShape = RoundedCornerShape(16.dp)

/**
 * 本地视频显示设置：居中卡片式弹窗。
 *
 * 参考设计稿把「视图 / 布局」并排、排序单独成块、字段开关折叠在底部；
 * 这里保留该结构，并把 10 个排序项拆成首行 5 个 + 可展开的「更多排序」，
 * 避免手机宽度下图标挤成一条。改动全部写入草稿，点「完成」才生效。
 */
@Composable
internal fun LocalVideoSettingsDialog(
    applied: LocalVideoSettings,
    draft: LocalVideoSettings,
    sampleVideo: LocalVideo?,
    samplePlayback: LocalVideoPlayback,
    sampleFolder: LocalVideoFolder?,
    onDraftChange: (LocalVideoSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onRescan: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .88f
    var expandedSections by remember { mutableStateOf(emptySet<String>()) }
    var moreSorts by remember { mutableStateOf(draft.sort in ExtraSorts) }
    // 弹窗进出：淡入 + 轻微缩放回弹，关闭时先播完退场再真正消失。
    var shown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var leaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        delay(150)
        leaveAction?.invoke()
    }
    val visible = shown && !leaving
    val enterScale by animateFloatAsState(
        targetValue = if (visible) 1f else .96f,
        animationSpec = spring(dampingRatio = .82f, stiffness = 420f),
        label = "dialog scale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 160 else 140),
        label = "dialog alpha",
    )

    fun close(action: () -> Unit) {
        if (leaving) return
        leaveAction = action
        leaving = true
    }

    fun toggleSection(id: String) {
        expandedSections = if (id in expandedSections) expandedSections - id else expandedSections + id
    }

    Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .42f * enterAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { close(onDismiss) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(.92f)
                    .widthIn(max = 350.dp)
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        scaleX = enterScale
                        scaleY = enterScale
                        alpha = enterAlpha
                    }
                    .clip(DialogShape)
                    .background(palette.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DialogHeader(onReset)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BentoPanel(Modifier.weight(3f)) {
                        PanelTitle("视图")
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            LocalVideoViewMode.entries.forEach { mode ->
                                OptionTile(
                                    icon = modeIcon(mode),
                                    label = modeLabel(mode),
                                    active = draft.mode == mode,
                                    modifier = Modifier.weight(1f),
                                ) { onDraftChange(draft.copy(mode = mode)) }
                            }
                        }
                    }
                    BentoPanel(Modifier.weight(2f)) {
                        PanelTitle("布局")
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            LocalVideoLayout.entries.forEach { layout ->
                                OptionTile(
                                    icon = layoutIcon(layout),
                                    label = layout.label,
                                    active = draft.layout == layout,
                                    modifier = Modifier.weight(1f),
                                ) { onDraftChange(draft.copy(layout = layout)) }
                            }
                        }
                    }
                }

                SortPanel(
                    draft = draft,
                    moreSorts = moreSorts,
                    onToggleMore = { moreSorts = !moreSorts },
                    onDraftChange = onDraftChange,
                )

                Column(
                    Modifier.fillMaxWidth()
                        .clip(PanelShape)
                        .background(palette.chip)
                        .border(1.dp, palette.line.copy(alpha = .7f), PanelShape),
                ) {
                    AccordionSection(
                        title = "视频字段显示",
                        count = draft.fields.enabledCount,
                        expanded = "video" in expandedSections,
                        onToggle = { toggleSection("video") },
                    ) {
                        PreviewBox {
                            if (sampleVideo != null) {
                                CompactVideoRow(sampleVideo, draft, samplePlayback)
                            } else {
                                PreviewHint("暂无可预览的视频")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FieldChips(
                            fields = LocalVideoField.entries,
                            label = { it.label },
                            isEnabled = { draft.fields.isEnabled(it) },
                            onToggle = { field ->
                                onDraftChange(
                                    draft.copy(
                                        fields = draft.fields.with(field, !draft.fields.isEnabled(field)),
                                    ),
                                )
                            },
                        )
                    }
                    SectionDivider()
                    AccordionSection(
                        title = "文件夹字段显示",
                        count = draft.folderFields.enabledCount,
                        expanded = "folder" in expandedSections,
                        onToggle = { toggleSection("folder") },
                    ) {
                        PreviewBox {
                            if (sampleFolder != null) {
                                CompactFolderRow(sampleFolder, draft)
                            } else {
                                PreviewHint("暂无可预览的文件夹")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FieldChips(
                            fields = LocalVideoFolderField.entries,
                            label = { it.label },
                            isEnabled = { draft.folderFields.isEnabled(it) },
                            onToggle = { field ->
                                onDraftChange(
                                    draft.copy(
                                        folderFields = draft.folderFields.with(
                                            field,
                                            !draft.folderFields.isEnabled(field),
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                    SectionDivider()
                    AccordionSection(
                        title = "高级选项",
                        count = advancedCount(draft),
                        expanded = "advanced" in expandedSections,
                        onToggle = { toggleSection("advanced") },
                    ) {
                        ToggleRow(
                            title = "在缩略图上显示长度",
                            subtitle = "关闭后长度显示在信息行",
                            checked = draft.durationOnThumbnail,
                            onChange = { onDraftChange(draft.copy(durationOnThumbnail = it)) },
                        )
                        ToggleRow(
                            title = "显示隐藏文件",
                            subtitle = "以 “.” 开头的目录",
                            checked = draft.showHidden,
                            onChange = { onDraftChange(draft.copy(showHidden = it)) },
                        )
                        ToggleRow(
                            title = "识别 .nomedia",
                            subtitle = "跳过含 .nomedia 的目录",
                            checked = draft.recognizeNomedia,
                            onChange = { onDraftChange(draft.copy(recognizeNomedia = it)) },
                        )
                        RescanRow(onRescan)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DialogButton(
                        "取消",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = { close(onDismiss) },
                    )
                    DialogButton(
                        if (draft == applied) "完成" else "应用",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = { close(onApply) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(onReset: () -> Unit) {
    val palette = LocalLibraryPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "显示设置",
            color = palette.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyphIcon(AppIcon.REFRESH, palette.ink3, Modifier.size(12.dp), 2f)
            Spacer(Modifier.width(4.dp))
            Text("恢复默认", color = palette.ink3, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BentoPanel(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(
        modifier.clip(PanelShape)
            .background(if (tinted) palette.accent.copy(alpha = .07f) else palette.chip)
            .then(
                if (tinted) {
                    Modifier.border(1.dp, palette.accent.copy(alpha = .16f), PanelShape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 11.dp),
        content = content,
    )
}

@Composable
private fun PanelTitle(text: String, withDot: Boolean = false) {
    val palette = LocalLibraryPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withDot) {
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(palette.accent),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptionTile(
    icon: AppIcon,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) palette.card else Color.Transparent)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(12.dp), scaleTo = .94f)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppGlyphIcon(icon, if (active) palette.accent else palette.ink2, Modifier.size(20.dp), 1.8f)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) palette.accent else palette.ink2,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun SortPanel(
    draft: LocalVideoSettings,
    moreSorts: Boolean,
    onToggleMore: () -> Unit,
    onDraftChange: (LocalVideoSettings) -> Unit,
) {
    val palette = LocalLibraryPalette.current
    BentoPanel(tinted = true) {
        PanelTitle("排序方式", withDot = true)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PrimarySorts.forEach { sort ->
                SortTile(
                    sort = sort,
                    active = draft.sort == sort,
                    modifier = Modifier.weight(1f),
                ) {
                    onDraftChange(
                        if (draft.sort == sort) {
                            draft.copy(ascending = !draft.ascending)
                        } else {
                            draft.copy(sort = sort, ascending = sort.defaultAscending)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.card)
                .border(1.dp, palette.accent.copy(alpha = .16f), RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            DirectionButton(
                label = "↑ ${draft.sort.ascendingLabel}",
                active = draft.ascending,
                modifier = Modifier.weight(1f),
            ) { onDraftChange(draft.copy(ascending = true)) }
            DirectionButton(
                label = "↓ ${draft.sort.descendingLabel}",
                active = !draft.ascending,
                modifier = Modifier.weight(1f),
            ) { onDraftChange(draft.copy(ascending = false)) }
        }
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleMore)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (moreSorts) "收起更多排序" else "更多排序",
                color = palette.ink2,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
            )
            AppGlyphIcon(
                AppIcon.CHEVRON_DOWN,
                palette.ink3,
                Modifier.size(14.dp).rotate(chevronRotation(moreSorts)),
                2.2f,
            )
        }
        AnimatedVisibility(
            visible = moreSorts,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExtraSorts.forEach { sort ->
                    SortTile(
                        sort = sort,
                        active = draft.sort == sort,
                        modifier = Modifier.weight(1f),
                    ) {
                        onDraftChange(
                            if (draft.sort == sort) {
                                draft.copy(ascending = !draft.ascending)
                            } else {
                                draft.copy(sort = sort, ascending = sort.defaultAscending)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortTile(
    sort: LocalVideoSort,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) palette.card else Color.Transparent)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(12.dp), scaleTo = .94f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppGlyphIcon(sortIcon(sort), if (active) palette.accent else palette.ink2, Modifier.size(20.dp), 1.8f)
        Spacer(Modifier.height(4.dp))
        Text(
            sort.label,
            color = if (active) palette.accent else palette.ink2,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DirectionButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val background by animateColorAsState(
        if (active) palette.accent else Color.Transparent,
        animationSpec = tween(160),
        label = "sort direction background",
    )
    Box(
        modifier.height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(9.dp), scaleTo = .97f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) palette.onAccent else palette.ink2,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccordionSection(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .pressFeedback(onClick = onToggle, scaleTo = .995f)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("$count 项", color = palette.ink2, fontSize = 11.5.sp)
            Spacer(Modifier.width(4.dp))
            AppGlyphIcon(
                AppIcon.CHEVRON_DOWN,
                palette.ink3,
                Modifier.size(15.dp).rotate(chevronRotation(expanded)),
                2.2f,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    val palette = LocalLibraryPalette.current
    HorizontalDivider(color = palette.line.copy(alpha = .7f))
}

@Composable
private fun PreviewBox(content: @Composable () -> Unit) {
    val palette = LocalLibraryPalette.current
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.card)
            .border(1.dp, palette.line.copy(alpha = .6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        content()
    }
}

@Composable
private fun PreviewHint(text: String) {
    val palette = LocalLibraryPalette.current
    Text(
        text,
        color = palette.ink3,
        fontSize = 11.sp,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
private fun <T> FieldChips(
    fields: List<T>,
    label: (T) -> String,
    isEnabled: (T) -> Boolean,
    onToggle: (T) -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        fields.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { field ->
                    val active = isEnabled(field)
                    Row(
                        Modifier.weight(1f).height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) palette.accentSoft else palette.card)
                            .pressFeedback(onClick = { onToggle(field) }, shape = RoundedCornerShape(10.dp), scaleTo = .96f)
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label(field),
                            color = if (active) palette.accent else palette.ink2,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            AppGlyphIcon(AppIcon.CHECK, palette.accent, Modifier.size(12.dp), 2.6f)
                        }
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
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
    val palette = LocalLibraryPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = palette.ink3, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(10.dp))
        LibrarySwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun LibrarySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalLibraryPalette.current
    val track by animateColorAsState(
        if (checked) palette.accent else palette.ink3.copy(alpha = .5f),
        animationSpec = tween(180),
        label = "switch track",
    )
    val offset by animateDpAsState(
        if (checked) 18.dp else 0.dp,
        animationSpec = tween(180),
        label = "switch thumb",
    )
    Box(
        Modifier.width(40.dp).height(22.dp)
            .clip(CircleShape)
            .background(track)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = 2.dp + offset)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun RescanRow(onRescan: () -> Unit) {
    val palette = LocalLibraryPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("重新扫描媒体库", color = palette.ink, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text("视频有增减时使用，会保留当前设置", color = palette.ink3, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(13.dp))
                .background(palette.accentSoft)
                .pressFeedback(onClick = onRescan, shape = RoundedCornerShape(13.dp), scaleTo = .95f)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyphIcon(AppIcon.REFRESH, palette.accent, Modifier.size(12.dp), 2f)
            Spacer(Modifier.width(4.dp))
            Text("扫描", color = palette.accent, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Box(
        modifier.height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) palette.accent else palette.chip)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(14.dp), scaleTo = .97f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) palette.onAccent else palette.ink2,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun advancedCount(draft: LocalVideoSettings): Int = listOf(
    draft.durationOnThumbnail,
    draft.showHidden,
    draft.recognizeNomedia,
).count { it }

private fun modeLabel(mode: LocalVideoViewMode): String = when (mode) {
    LocalVideoViewMode.ALL_FOLDERS -> "所有"
    LocalVideoViewMode.FILES -> "视频"
    LocalVideoViewMode.FOLDERS -> "文件夹"
}

private fun modeIcon(mode: LocalVideoViewMode): AppIcon = when (mode) {
    LocalVideoViewMode.ALL_FOLDERS -> AppIcon.FOLDER_ALL
    LocalVideoViewMode.FILES -> AppIcon.FILE
    LocalVideoViewMode.FOLDERS -> AppIcon.FOLDER
}

private fun layoutIcon(layout: LocalVideoLayout): AppIcon = when (layout) {
    LocalVideoLayout.LIST -> AppIcon.LIST
    LocalVideoLayout.GRID -> AppIcon.GRID
}

private fun sortIcon(sort: LocalVideoSort): AppIcon = when (sort) {
    LocalVideoSort.TITLE -> AppIcon.TITLE
    LocalVideoSort.DATE -> AppIcon.CALENDAR
    LocalVideoSort.PLAYED -> AppIcon.PLAYED
    LocalVideoSort.STATUS -> AppIcon.STATUS
    LocalVideoSort.DURATION -> AppIcon.CLOCK
    LocalVideoSort.SIZE -> AppIcon.SIZE
    LocalVideoSort.RESOLUTION -> AppIcon.FRAME
    LocalVideoSort.PATH -> AppIcon.PIN
    LocalVideoSort.FPS -> AppIcon.FILM
    LocalVideoSort.TYPE -> AppIcon.DOCUMENT
}
