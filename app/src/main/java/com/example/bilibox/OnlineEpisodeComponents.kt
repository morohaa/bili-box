package com.example.bilibox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibox.bili.Page

@Composable
internal fun OnlineEpisodeCard(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.width(148.dp).height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) OnlinePink.copy(alpha = .10f) else OnlineDivider)
            .border(1.dp, if (selected) OnlinePink else OnlineDivider, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            EqualizerBars()
            Spacer(Modifier.width(7.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "P${page.page}",
                color = if (selected) OnlinePink else OnlineSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                page.part.ifBlank { "第 ${page.page} 部分" },
                color = if (selected) OnlineText else OnlineSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else OnlineMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun OnlineEpisodesBottomSheet(
    pages: List<Page>,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp).semantics { contentDescription = "关闭选集" }
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 1.8.dp.toPx()
                    drawLine(OnlineSecondary, Offset(3.dp.toPx(), 3.dp.toPx()),
                        Offset(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
                    drawLine(OnlineSecondary, Offset(size.width - 3.dp.toPx(), 3.dp.toPx()),
                        Offset(3.dp.toPx(), size.height - 3.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
        }
        HorizontalDivider(color = OnlineDivider)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lazyItems(pages, key = { it.cid }) { page ->
                OnlineSheetEpisodeRow(page, page.page == selectedPage) {
                    onPageSelected(page.page)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun OnlineSheetEpisodeRow(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) OnlinePink.copy(alpha = .10f) else OnlineDivider)
            .border(1.dp, if (selected) OnlinePink else OnlineDivider, RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            EqualizerBars()
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "P${page.page} ${page.part.ifBlank { "第 ${page.page} 部分" }}",
                color = if (selected) OnlinePink else OnlineText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else OnlineMuted,
            fontSize = 11.sp
        )
    }
}
