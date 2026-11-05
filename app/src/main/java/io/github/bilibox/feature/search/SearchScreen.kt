package io.github.bilibox.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.EmptyCard
import io.github.bilibox.core.ui.component.RemoteCover
import io.github.bilibox.core.ui.component.VideoMetaIcon
import io.github.bilibox.core.ui.theme.DarkInkSoft
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatSearchDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        state.items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { video ->
                    SearchGridCard(video, darkMode, Modifier.weight(1f)) { onVideoClick(video) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        state.items.forEach { video ->
            SearchResultCard(video, darkMode, Modifier.offset(x = (-4).dp)) { onVideoClick(video) }
            HorizontalDivider(color = if (darkMode) Color(0xFF30343A) else Color(0xFFDDE3E8), thickness = 1.dp)
        }
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

@Composable
private fun SearchResultCard(video: VideoSummary, darkMode: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(144.dp).height(90.dp).clip(RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            RemoteCover(video.coverUrl, Modifier.fillMaxSize())
            Text(
                formatSearchDuration(video.duration),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .padding(end = 5.dp, bottom = 5.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(90.dp)) {
            Text(
                video.title,
                color = if (darkMode) DarkInkSoft else Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 17.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                AuthorRow(video.author, darkMode)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    VideoMeta(icon = "play", value = video.playCount, darkMode = darkMode)
                    VideoMeta(icon = "comment", value = video.commentCount.toString(), darkMode = darkMode)
                }
            }
        }
    }
}

@Composable
private fun SearchGridCard(video: VideoSummary, darkMode: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(bottom = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            RemoteCover(video.coverUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
            Text(
                formatSearchDuration(video.duration),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .padding(end = 5.dp, bottom = 5.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            video.title,
            color = if (darkMode) DarkInkSoft else Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        AuthorRow(video.author, darkMode)
        Spacer(Modifier.height(5.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            VideoMeta(icon = "play", value = video.playCount, darkMode = darkMode)
            VideoMeta(icon = "comment", value = video.commentCount.toString(), darkMode = darkMode)
        }
    }
}

@Composable
private fun AuthorRow(author: String, darkMode: Boolean) {
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.padding(start = 2.dp)
                .size(width = 14.dp, height = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Transparent)
                .border(0.6.dp, muted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("UP", color = muted, fontSize = 6.sp, lineHeight = 7.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(4.dp))
        Text(author, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VideoMeta(icon: String, value: String, darkMode: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
        VideoMetaIcon(kind = icon, modifier = Modifier.size(width = 16.dp, height = 13.dp), tint = if (darkMode) Color(0xFF9BA3AE) else Muted)
        Spacer(Modifier.width(2.dp))
        Text(value, color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatPublishDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "--"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}
