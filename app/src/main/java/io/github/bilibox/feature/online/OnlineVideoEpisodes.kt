package io.github.bilibox.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.data.bili.BiliQn
import io.github.bilibox.data.bili.Dolby
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.bili.PlayData
import io.github.bilibox.feature.player.PlayerQualityOption
import io.github.bilibox.feature.player.formatPlaybackTime

@Composable
internal fun OnlineVideoEpisodes(
    pages: List<Page>,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Page) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pages.indexOfFirst { it.page == selectedPage }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("分集(${pages.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages, key = { _, page -> page.cid }) { _, page ->
                    OnlineEpisodeRow(page, page.page == selectedPage) { onPageSelected(page); onDismiss() }
                }
            }
        }
    }
}

@Composable
internal fun OnlineEpisodeRow(page: Page, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) OnlinePink.copy(alpha = .18f) else Color.White.copy(alpha = .05f))
            .border(1.dp, if (selected) OnlinePink else Color.Transparent, RoundedCornerShape(11.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) EqualizerBars()
        Text(
            "P${page.page} ${page.part.ifBlank { "第 ${page.page} 部分" }}${if (selected) " (当前播放)" else ""}",
            color = if (selected) OnlinePink else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (selected) 4.dp else 0.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else Color.White.copy(alpha = .45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun EqualizerBars() {
    Row(Modifier.width(20.dp).height(16.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val heights = listOf(8.dp, 13.dp, 10.dp)
        heights.forEach { height -> Box(Modifier.width(3.dp).height(height).clip(RoundedCornerShape(2.dp)).background(OnlinePink)) }
    }
}

/**
 * Maps the playurl `accept_quality` list to the compact labels used by the
 * fullscreen quality drawer. Anonymous playback is reliable up to 1080P; the
 * 4K/8K/Dolby tiers need account privileges and are intentionally hidden for
 * now instead of offering an option that cannot start.
 */
internal fun buildQualityOptions(play: PlayData?, actualQn: Int): List<PlayerQualityOption> {
    val accepted = (play?.acceptQuality.orEmpty() + listOf(actualQn))
        .filter { it in BiliQn.P360..BiliQn.P1080 }
        .distinct()
    val qns = accepted.ifEmpty {
        listOf(BiliQn.P1080, BiliQn.P720, BiliQn.P480, BiliQn.P360)
    }.sortedDescending()
    return qns.map { qn ->
        when (qn) {
            74 -> PlayerQualityOption(qn, "720P 60帧", "高帧率")
            BiliQn.P1080 -> PlayerQualityOption(qn, "1080P", "高清 · 推荐")
            BiliQn.P720 -> PlayerQualityOption(qn, "720P", "高清 · 省流")
            BiliQn.P480 -> PlayerQualityOption(qn, "480P", "清晰")
            BiliQn.P360 -> PlayerQualityOption(qn, "360P", "流畅")
            else -> PlayerQualityOption(qn, "${qn}P", "")
        }
    }
}
