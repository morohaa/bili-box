package io.github.bilibox.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.EmptyCard
import io.github.bilibox.core.ui.component.VideoGridCard
import io.github.bilibox.core.ui.component.VideoListCard
import io.github.bilibox.core.ui.theme.DarkInkSoft
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink

@Composable
internal fun SearchLoading(darkMode: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text("正在搜索视频…", color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun SearchError(message: String, darkMode: Boolean = false, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暂时没有搜到结果", color = if (darkMode) DarkInkSoft else Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("重试", color = Pink) }
    }
}

/**
 * 搜索结果列表：卡片自身承载底色、圆角与阴影，列表不再画分隔线；
 * 列表模式为左封面横卡，宫格模式为两列竖卡。
 */
@Composable
internal fun SearchResults(
    state: SearchUiState.Success,
    darkMode: Boolean = false,
    gridLayout: Boolean = false,
    onVideoClick: (VideoSummary) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    SearchResultBar(
        count = if (state.total > 0) state.total else state.items.size,
        darkMode = darkMode,
    )
    if (state.items.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        EmptyCard("没有找到视频", "换个关键词再试试，或调整筛选条件。", darkMode)
    } else if (gridLayout) {
        SearchGrid(state.items, darkMode, onVideoClick)
    } else {
        SearchList(state.items, darkMode, onVideoClick)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onPageChange(state.page - 1) }, enabled = state.page > 1) {
            Text("上一页", color = if (state.page > 1) Pink else Muted)
        }
        Text("第 ${state.page} 页", color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { onPageChange(state.page + 1) }, enabled = state.hasMore) {
            Text("下一页", color = if (state.hasMore) Pink else Muted)
        }
    }
}

@Composable
private fun SearchList(items: List<VideoSummary>, darkMode: Boolean, onVideoClick: (VideoSummary) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items.forEachIndexed { index, video ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            VideoListCard(video, darkMode) { onVideoClick(video) }
        }
    }
}

@Composable
private fun SearchGrid(items: List<VideoSummary>, darkMode: Boolean, onVideoClick: (VideoSummary) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { video ->
                    VideoGridCard(video, darkMode, Modifier.weight(1f)) { onVideoClick(video) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchResultBar(count: Int, darkMode: Boolean) {
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted
    val strong = if (darkMode) DarkInkSoft else Ink
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("找到 ") }
            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(count.toString()) }
            withStyle(SpanStyle(color = muted)) { append(" 个视频") }
        },
        fontSize = 12.sp,
        modifier = Modifier.offset(x = (-2).dp).padding(top = 12.dp, bottom = 2.dp),
    )
}
