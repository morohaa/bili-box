package com.example.bilibox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineEpisodesBottomSheet(
    pages: List<Page>,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    var gridMode by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp)
                .clip(CircleShape).background(OnlineDivider))
        }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("视频选集", color = OnlineText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("($selectedPage/${pages.size})", color = OnlineMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { gridMode = !gridMode }) {
                    Text(if (gridMode) "列表" else "宫格", color = OnlinePink, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = OnlineSecondary, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = OnlineDivider)
            if (gridMode) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(pages, key = { it.cid }) { page ->
                        OnlineSheetEpisodeRow(page, page.page == selectedPage) {
                            onPageSelected(page.page)
                            onDismiss()
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp).selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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
    }
}

@Composable
private fun OnlineSheetEpisodeRow(page: Page, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp)
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
