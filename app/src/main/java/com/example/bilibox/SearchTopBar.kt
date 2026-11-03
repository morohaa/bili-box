package com.example.bilibox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 搜索页固定顶栏：返回 + 搜索框 + 搜索按钮，以及排序、筛选、单双列切换。
 *
 * 样式对齐设计稿 v2：搜索行 44dp、输入框 40dp 圆角 20dp、
 * 控制行 34dp 胶囊，排序弹层与筛选面板紧贴顶栏。
 */

private val SearchAccentLight = Color(0xFFC92C63)
private val SearchAccentDark = Color(0xFFFF9AB6)
private val SearchSoftLight = Color(0xFFFFF0F5)
private val SearchSoftDark = Color(0xFF2A1A21)
private val SearchFillLight = Color(0xFFF2F3F5)
private val SearchFillDark = Color(0xFF24272D)
private val SearchLineLight = Color(0xFFE9EDF2)
private val SearchLineDark = Color(0xFF30343A)
private val SearchElevatedLight = Color.White
private val SearchElevatedDark = Color(0xFF1F2226)

private class SearchHeaderPalette(
    val background: Color,
    val elevated: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val soft: Color,
    val fill: Color,
    val line: Color,
    val clearFill: Color,
    val clearInk: Color,
    val onAccent: Color,
)

private fun searchHeaderPalette(darkMode: Boolean): SearchHeaderPalette = if (darkMode) {
    SearchHeaderPalette(
        background = Color(0xFF17191D),
        elevated = SearchElevatedDark,
        ink = Color(0xFFE7E9ED),
        muted = Color(0xFF9BA3AE),
        accent = SearchAccentDark,
        soft = SearchSoftDark,
        fill = SearchFillDark,
        line = SearchLineDark,
        clearFill = Color(0xFF3A3A40),
        clearInk = Color(0xFFE8E8EC),
        onAccent = Color(0xFF2A0A14),
    )
} else {
    SearchHeaderPalette(
        background = Color.White,
        elevated = SearchElevatedLight,
        ink = Ink,
        muted = Muted,
        accent = SearchAccentLight,
        soft = SearchSoftLight,
        fill = SearchFillLight,
        line = SearchLineLight,
        clearFill = Color(0xFFDCDCE1),
        clearInk = Color(0xFF4A4A50),
        onAccent = Color.White,
    )
}

@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    order: SearchOrder,
    onOrderChange: (SearchOrder) -> Unit,
    duration: SearchDuration?,
    timeRange: SearchTimeRange?,
    onFiltersChange: (SearchDuration?, SearchTimeRange?) -> Unit,
    gridLayout: Boolean,
    onToggleLayout: () -> Unit,
    darkMode: Boolean = false,
) {
    val palette = searchHeaderPalette(darkMode)
    val focusManager = LocalFocusManager.current
    val activeFilters = listOfNotNull(duration, timeRange).size
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    Column(
        Modifier.fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(6.dp))
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = submit,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            SubmitButton(palette, submit)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip(order, palette, onOrderChange)
            Spacer(Modifier.weight(1f))
            FilterChip(palette, activeCount = activeFilters) { filterSheetOpen = true }
            Spacer(Modifier.width(8.dp))
            LayoutButton(gridLayout, palette, onToggleLayout)
        }
        HorizontalDivider(color = palette.line, thickness = 1.dp)
    }

    if (filterSheetOpen) {
        SearchFilterSheet(
            duration = duration,
            timeRange = timeRange,
            palette = palette,
            onDismiss = { filterSheetOpen = false },
            onApply = { selectedDuration, selectedRange ->
                filterSheetOpen = false
                if (selectedDuration != duration || selectedRange != timeRange) {
                    onFiltersChange(selectedDuration, selectedRange)
                }
            },
        )
    }
}

@Composable
private fun BackButton(palette: SearchHeaderPalette, onBack: () -> Unit) {
    Box(
        Modifier.size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "返回" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path().apply {
                moveTo(size.width * .64f, size.height * .18f)
                lineTo(size.width * .30f, size.height * .5f)
                lineTo(size.width * .64f, size.height * .82f)
            }
            drawPath(path, palette.ink, style = stroke)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    palette: SearchHeaderPalette,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.height(40.dp)
            .clip(shape)
            .background(if (focused) palette.background else palette.fill)
            .border(1.5.dp, if (focused) palette.accent else Color.Transparent, shape)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(Modifier.size(17.dp), palette.muted)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = palette.ink, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "搜索视频、UP主",
                            color = palette.muted,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            ClearButton(palette) { onQueryChange("") }
        }
    }
}

@Composable
private fun ClearButton(palette: SearchHeaderPalette, onClear: () -> Unit) {
    Box(
        Modifier.size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClear)
            .semantics { contentDescription = "清空搜索词" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(palette.clearFill)
            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                palette.clearInk,
                Offset(size.width * .34f, size.height * .34f),
                Offset(size.width * .66f, size.height * .66f),
                stroke.width,
                StrokeCap.Round,
            )
            drawLine(
                palette.clearInk,
                Offset(size.width * .66f, size.height * .34f),
                Offset(size.width * .34f, size.height * .66f),
                stroke.width,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SubmitButton(palette: SearchHeaderPalette, onSubmit: () -> Unit) {
    Box(
        Modifier.height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onSubmit)
            .semantics { contentDescription = "搜索" }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("搜索", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SortChip(
    order: SearchOrder,
    palette: SearchHeaderPalette,
    onSelect: (SearchOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (expanded) palette.soft else palette.fill,
        label = "sort chip background",
    )
    val content by animateColorAsState(
        if (expanded) palette.accent else palette.ink,
        label = "sort chip content",
    )
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sort arrow")

    Box {
        Row(
            Modifier.height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(background)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { contentDescription = "排序方式：${order.label}" }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(order.label, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(10.dp).rotate(arrowRotation)) {
                val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val path = Path().apply {
                    moveTo(size.width * .06f, size.height * .28f)
                    lineTo(size.width * .5f, size.height * .74f)
                    lineTo(size.width * .94f, size.height * .28f)
                }
                drawPath(path, content, style = stroke)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(152.dp),
            offset = DpOffset(0.dp, 2.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = palette.elevated,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(Modifier.padding(horizontal = 5.dp)) {
                SearchOrder.entries.forEach { option ->
                    val selected = option == order
                    Row(
                        Modifier.fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) palette.soft else Color.Transparent)
                            .clickable(role = Role.Button) {
                                expanded = false
                                if (!selected) onSelect(option)
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            color = if (selected) palette.accent else palette.ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) CheckGlyph(Modifier.size(14.dp), palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    palette: SearchHeaderPalette,
    activeCount: Int,
    onClick: () -> Unit,
) {
    val active = activeCount > 0
    val content = if (active) palette.accent else palette.muted
    Row(
        Modifier.height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) palette.soft else palette.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (active) "筛选，已启用" else "筛选" }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FunnelGlyph(Modifier.size(14.dp), content)
        Spacer(Modifier.width(5.dp))
        Text("筛选", color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (active) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(activeCount.toString(), color = palette.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LayoutButton(
    gridLayout: Boolean,
    palette: SearchHeaderPalette,
    onToggle: () -> Unit,
) {
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(palette.fill)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (gridLayout) "切换单列布局" else "切换双列布局" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val unit = size.minDimension / 24f
            val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tint = palette.muted
            if (gridLayout) {
                val corner = CornerRadius(1.2f * unit)
                drawRoundRect(tint, Offset(3f * unit, 4f * unit), Size(5f * unit, 4.6f * unit), corner, style = stroke)
                drawRoundRect(tint, Offset(3f * unit, 14.4f * unit), Size(5f * unit, 4.6f * unit), corner, style = stroke)
                drawLine(tint, Offset(11.5f * unit, 6.3f * unit), Offset(21f * unit, 6.3f * unit), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(11.5f * unit, 16.7f * unit), Offset(21f * unit, 16.7f * unit), stroke.width, StrokeCap.Round)
            } else {
                val corner = CornerRadius(1.6f * unit)
                val cell = 7.2f * unit
                drawRoundRect(tint, Offset(3f * unit, 3f * unit), Size(cell, cell), corner, style = stroke)
                drawRoundRect(tint, Offset(13.8f * unit, 3f * unit), Size(cell, cell), corner, style = stroke)
                drawRoundRect(tint, Offset(3f * unit, 13.8f * unit), Size(cell, cell), corner, style = stroke)
                drawRoundRect(tint, Offset(13.8f * unit, 13.8f * unit), Size(cell, cell), corner, style = stroke)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterSheet(
    duration: SearchDuration?,
    timeRange: SearchTimeRange?,
    palette: SearchHeaderPalette,
    onDismiss: () -> Unit,
    onApply: (SearchDuration?, SearchTimeRange?) -> Unit,
) {
    var draftDuration by remember { mutableStateOf(duration) }
    var draftTimeRange by remember { mutableStateOf(timeRange) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = palette.elevated,
        contentColor = palette.ink,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("筛选", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = "关闭筛选" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(
                            palette.ink,
                            Offset(size.width * .18f, size.height * .18f),
                            Offset(size.width * .82f, size.height * .82f),
                            stroke.width,
                            StrokeCap.Round,
                        )
                        drawLine(
                            palette.ink,
                            Offset(size.width * .82f, size.height * .18f),
                            Offset(size.width * .18f, size.height * .82f),
                            stroke.width,
                            StrokeCap.Round,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                Text("时长", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterOptionChip("全部", selected = draftDuration == null, palette = palette) { draftDuration = null }
                    SearchDuration.entries.forEach { option ->
                        FilterOptionChip(option.label, selected = draftDuration == option, palette = palette) {
                            draftDuration = if (draftDuration == option) null else option
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("发布时间", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterOptionChip("全部", selected = draftTimeRange == null, palette = palette) { draftTimeRange = null }
                    SearchTimeRange.entries.forEach { option ->
                        FilterOptionChip(option.label, selected = draftTimeRange == option, palette = palette) {
                            draftTimeRange = if (draftTimeRange == option) null else option
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            HorizontalDivider(color = palette.line, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetButton("重置", palette, primary = false) {
                    draftDuration = null
                    draftTimeRange = null
                }
                SheetButton("应用筛选", palette, primary = true) { onApply(draftDuration, draftTimeRange) }
            }
        }
    }
}

@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    palette: SearchHeaderPalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.soft else palette.fill)
            .border(1.dp, if (selected) palette.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) palette.accent else palette.ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.SheetButton(
    label: String,
    palette: SearchHeaderPalette,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) palette.accent else palette.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) palette.onAccent else palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            tint,
            radius = size.minDimension * .3f,
            center = Offset(size.width * .44f, size.height * .44f),
            style = stroke,
        )
        drawLine(
            tint,
            Offset(size.width * .65f, size.height * .65f),
            Offset(size.width * .9f, size.height * .9f),
            stroke.width,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun FunnelGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * .08f, size.height * .16f)
            lineTo(size.width * .92f, size.height * .16f)
            lineTo(size.width * .6f, size.height * .55f)
            lineTo(size.width * .6f, size.height * .88f)
            lineTo(size.width * .4f, size.height * .76f)
            lineTo(size.width * .4f, size.height * .55f)
            close()
        }
        drawPath(path, tint, style = stroke)
    }
}

@Composable
private fun CheckGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * .16f, size.height * .54f)
            lineTo(size.width * .4f, size.height * .78f)
            lineTo(size.width * .84f, size.height * .24f)
        }
        drawPath(path, tint, style = stroke)
    }
}
