package io.github.bilibox.feature.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlinePinkSoft
import io.github.bilibox.core.ui.theme.PlaybackCard
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSecondary
import io.github.bilibox.core.ui.theme.PlaybackStroke
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.core.ui.theme.PlaybackText
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.bili.Page

@Composable
internal fun OnlineEpisodeCard(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.width(148.dp).height(58.dp)
            .clip(shape)
            .background(if (selected) OnlinePinkSoft else PlaybackCard)
            .border(1.dp, if (selected) OnlinePink else PlaybackStroke, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            EpisodeEqualizer(barWidth = 2.dp, spacing = 1.5.dp, height = 12.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            page.part.ifBlank { "第 ${page.page} 部分" },
            color = if (selected) OnlinePink else PlaybackText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The multi-part drawer used under the online player, rebuilt from the
 * interaction reference: a compact header (title + fraction + auto-play
 * switch + list/grid toggle + close) over a list or 3-column grid of parts.
 */
@Composable
internal fun OnlineEpisodesBottomSheet(
    pages: List<Page>,
    selectedPage: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    gridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    val selectedIndex = pages.indexOfFirst { it.page == selectedPage }
    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PlaybackSurface)
    ) {
        EpisodesSheetHeader(
            fraction = if (selectedIndex >= 0) selectedIndex + 1 else selectedPage,
            total = pages.size,
            autoPlayNext = autoPlayNext,
            onAutoPlayNextChange = onAutoPlayNextChange,
            gridView = gridView,
            onGridViewChange = onGridViewChange,
            onDismiss = onDismiss,
        )
        HorizontalDivider(color = PlaybackDivider)
        if (gridView) {
            val gridState = rememberLazyGridState(
                initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
            )
            LaunchedEffect(selectedPage) {
                if (selectedIndex >= 0) gridState.scrollToItem(selectedIndex)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f).selectableGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(pages, key = { it.cid }) { page ->
                    OnlineSheetEpisodeCell(page, page.page == selectedPage) {
                        onPageSelected(page.page)
                    }
                }
            }
        } else {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
            )
            LaunchedEffect(selectedPage) {
                if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).selectableGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lazyItems(pages, key = { it.cid }) { page ->
                    OnlineSheetEpisodeRow(page, page.page == selectedPage) {
                        onPageSelected(page.page)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesSheetHeader(
    fraction: Int,
    total: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    gridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("视频选集", color = PlaybackText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text(
            "($fraction/$total)",
            color = PlaybackMuted,
            fontSize = 12.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        AutoPlayNextToggle(autoPlayNext, onAutoPlayNextChange)
        Spacer(Modifier.width(8.dp))
        EpisodeLayoutToggle(gridView, onGridViewChange)
        Spacer(Modifier.width(8.dp))
        EpisodesSheetCloseButton(onDismiss)
    }
}

@Composable
private fun AutoPlayNextToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) OnlinePink else PlaybackStroke,
        animationSpec = tween(180),
        label = "自动连播开关颜色",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 2.dp,
        animationSpec = tween(180),
        label = "自动连播滑块位置",
    )
    Row(
        Modifier.height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = "自动连播" }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("自动连播", color = PlaybackSecondary, fontSize = 11.5.sp, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.width(28.dp).height(16.dp).clip(CircleShape).background(trackColor)) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = knobOffset)
                    .size(12.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun EpisodeLayoutToggle(
    gridView: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(PlaybackDivider)
            .clickable(role = Role.Button) { onToggle(!gridView) }
            .semantics { contentDescription = if (gridView) "切换为列表视图" else "切换为宫格视图" }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeGridIcon(Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            if (gridView) "列表视图" else "宫格视图",
            color = PlaybackText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EpisodeGridIcon(modifier: Modifier = Modifier, color: Color = OnlinePink) {
    Canvas(modifier) {
        val strokeWidth = 1.2.dp.toPx()
        val gap = 2.2.dp.toPx()
        val corner = 0.6.dp.toPx()
        val cell = (size.minDimension - gap) / 2f
        listOf(0f, cell + gap).forEach { y ->
            listOf(0f, cell + gap).forEach { x ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

@Composable
private fun EpisodesSheetCloseButton(onDismiss: () -> Unit) {
    val closeTint = PlaybackSecondary
    Box(
        Modifier.size(28.dp)
            .clip(CircleShape)
            .background(PlaybackDivider)
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { contentDescription = "关闭选集" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(closeTint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(closeTint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun OnlineSheetEpisodeRow(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (selected) OnlinePinkSoft else PlaybackCard,
        animationSpec = tween(160),
        label = "分P行背景",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) OnlinePink else Color.Transparent,
        animationSpec = tween(160),
        label = "分P行描边",
    )
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
            if (selected) {
                EpisodeEqualizer()
            } else {
                Text(
                    "P${page.page}",
                    color = PlaybackMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        val partTitle = page.part.ifBlank { "第 ${page.page} 部分" }
        Text(
            // The leading column already identifies the part, so the title is
            // never prefixed with a second "Pn".
            partTitle,
            color = if (selected) OnlinePink else PlaybackText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else PlaybackMuted,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun OnlineSheetEpisodeCell(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (selected) OnlinePinkSoft else PlaybackCard,
        animationSpec = tween(160),
        label = "分P宫格背景",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) OnlinePink else PlaybackStroke,
        animationSpec = tween(160),
        label = "分P宫格描边",
    )
    Column(
        Modifier.fillMaxWidth().height(64.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                EpisodeEqualizer(barWidth = 2.dp, spacing = 1.5.dp, height = 12.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "P${page.page}",
                color = if (selected) OnlinePink else PlaybackText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            page.part.ifBlank { "第 ${page.page} 部分" },
            color = PlaybackMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Compact three-bar "playing" mark used by the selected part. */
@Composable
private fun EpisodeEqualizer(
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp,
    height: Dp = 14.dp,
) {
    val transition = rememberInfiniteTransition(label = "音律柱")
    val bars = listOf(
        transition.animateFloat(
            initialValue = .32f,
            targetValue = .92f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律柱一",
        ),
        transition.animateFloat(
            initialValue = .95f,
            targetValue = .3f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律柱二",
        ),
        transition.animateFloat(
            initialValue = .48f,
            targetValue = .88f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律柱三",
        ),
    )
    Row(
        Modifier.width(barWidth * 3 + spacing * 2).height(height),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        bars.forEach { bar ->
            Box(
                Modifier.width(barWidth)
                    .height(height * bar.value)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(OnlinePink)
            )
        }
    }
}
