package io.github.bilibox.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.EqualizerBars
import io.github.bilibox.core.ui.component.playback.PlayerSidePanel
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.bili.BiliQn
import io.github.bilibox.data.bili.Dolby
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.bili.PlayData
import io.github.bilibox.feature.player.PlayerQualityOption

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
    PlayerSidePanel(
        title = "分集",
        titleBadge = "(${pages.size}P)",
        onDismiss = onDismiss,
        closeDescription = "收起选集",
        scrimDescription = "关闭选集",
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).selectableGroup(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pages, key = { _, page -> page.cid }) { _, page ->
                OnlineEpisodeRow(page, page.page == selectedPage) {
                    onPageSelected(page)
                    onDismiss()
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
        val label = page.part.ifBlank { "第 ${page.page} 部分" }
        Text(
            if (selected) label else "P${page.page} $label",
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
