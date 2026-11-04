package io.github.bilibox.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.ui.theme.DynamicColors
import io.github.bilibox.feature.dynamic.DynamicPage

/**
 * 搜索结果上方的排序 / 筛选 / 单双列控制条。
 *
 * 搜索行本身由 [DynamicPage] 绘制；这里只保留结果页需要的那一行，
 * 样式沿用参考稿的白色胶囊 + 粉色高亮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SearchControlsBar(
    order: SearchOrder,
    onOrderChange: (SearchOrder) -> Unit,
    duration: SearchDuration?,
    timeRange: SearchTimeRange?,
    onFiltersChange: (SearchDuration?, SearchTimeRange?) -> Unit,
    gridLayout: Boolean,
    onToggleLayout: () -> Unit,
    colors: DynamicColors,
) {
    val activeFilters = listOfNotNull(duration, timeRange).size
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip(order, colors, onOrderChange)
            Spacer(Modifier.weight(1f))
            FilterChip(colors, activeCount = activeFilters) { filterSheetOpen = true }
            Spacer(Modifier.width(8.dp))
            LayoutButton(gridLayout, colors, onToggleLayout)
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }

    if (filterSheetOpen) {
        SearchFilterSheet(
            duration = duration,
            timeRange = timeRange,
            colors = colors,
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
private fun SortChip(
    order: SearchOrder,
    colors: DynamicColors,
    onSelect: (SearchOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (expanded) colors.pinkSoft else colors.chip,
        label = "sort chip background",
    )
    val content by animateColorAsState(
        if (expanded) colors.pink else colors.ink,
        label = "sort chip content",
    )
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sort arrow")

    Box {
        Row(
            Modifier.height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(background)
                .border(
                    1.dp,
                    if (expanded) colors.pink.copy(alpha = .35f) else colors.chipBorder,
                    RoundedCornerShape(15.dp),
                )
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { contentDescription = "排序方式：${order.label}" }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(order.label, color = content, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(9.dp).rotate(arrowRotation)) {
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
            containerColor = colors.card,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Column(Modifier.padding(horizontal = 5.dp)) {
                SearchOrder.entries.forEach { option ->
                    val selected = option == order
                    Row(
                        Modifier.fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) colors.pinkSoft else Color.Transparent)
                            .clickable(role = Role.Button) {
                                expanded = false
                                if (!selected) onSelect(option)
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            color = if (selected) colors.pink else colors.ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) CheckGlyph(Modifier.size(14.dp), colors.pink)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    colors: DynamicColors,
    activeCount: Int,
    onClick: () -> Unit,
) {
    val active = activeCount > 0
    val content = if (active) colors.pink else colors.ink2
    Row(
        Modifier.height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (active) colors.pinkSoft else colors.chip)
            .border(
                1.dp,
                if (active) colors.pink.copy(alpha = .35f) else colors.chipBorder,
                RoundedCornerShape(15.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (active) "筛选，已启用" else "筛选" }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FunnelGlyph(Modifier.size(14.dp), content)
        Spacer(Modifier.width(5.dp))
        Text("筛选", color = content, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        if (active) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(colors.pink),
                contentAlignment = Alignment.Center,
            ) {
                Text(activeCount.toString(), color = colors.onPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LayoutButton(
    gridLayout: Boolean,
    colors: DynamicColors,
    onToggle: () -> Unit,
) {
    Box(
        Modifier.size(30.dp)
            .clip(CircleShape)
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (gridLayout) "切换单列布局" else "切换双列布局" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val unit = size.minDimension / 24f
            val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tint = colors.ink2
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
    colors: DynamicColors,
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
        containerColor = colors.card,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("筛选", color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                            colors.ink,
                            Offset(size.width * .18f, size.height * .18f),
                            Offset(size.width * .82f, size.height * .82f),
                            stroke.width,
                            StrokeCap.Round,
                        )
                        drawLine(
                            colors.ink,
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
                Text("时长", color = colors.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterOptionChip("全部", selected = draftDuration == null, colors = colors) { draftDuration = null }
                    SearchDuration.entries.forEach { option ->
                        FilterOptionChip(option.label, selected = draftDuration == option, colors = colors) {
                            draftDuration = if (draftDuration == option) null else option
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("发布时间", color = colors.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterOptionChip("全部", selected = draftTimeRange == null, colors = colors) { draftTimeRange = null }
                    SearchTimeRange.entries.forEach { option ->
                        FilterOptionChip(option.label, selected = draftTimeRange == option, colors = colors) {
                            draftTimeRange = if (draftTimeRange == option) null else option
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            HorizontalDivider(color = colors.line, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetButton("重置", colors, primary = false) {
                    draftDuration = null
                    draftTimeRange = null
                }
                SheetButton("应用筛选", colors, primary = true) { onApply(draftDuration, draftTimeRange) }
            }
        }
    }
}

@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    colors: DynamicColors,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.pinkSoft else colors.background)
            .border(1.dp, if (selected) colors.pink else Color.Transparent, RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.pink else colors.ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.SheetButton(
    label: String,
    colors: DynamicColors,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) colors.pink else colors.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) colors.onPink else colors.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
