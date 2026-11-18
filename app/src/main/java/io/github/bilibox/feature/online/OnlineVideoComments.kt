package io.github.bilibox.feature.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.bilibox.core.ui.component.RemoteCover
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import io.github.bilibox.data.bili.VideoCommentPage
import io.github.bilibox.feature.player.PlayerExtraDetailPane

@Composable
internal fun rememberOnlineVideoComments(
    client: BiliClient,
    aid: Long?,
    awaitingVideoInfo: Boolean,
    onRetryVideoInfo: () -> Unit,
): PlayerExtraDetailPane = onlineVideoCommentsPane(
    videoKey = client to aid,
    canLoad = aid != null && aid > 0,
    awaitingVideoInfo = awaitingVideoInfo,
    onRetryVideoInfo = onRetryVideoInfo,
    loadComments = { order, offset -> client.videoComments(requireNotNull(aid), offset, order) },
    loadReplies = { root, page -> client.videoCommentReplies(requireNotNull(aid), root, page) },
)

@Composable
internal fun onlineVideoCommentsPane(
    videoKey: Any,
    canLoad: Boolean,
    awaitingVideoInfo: Boolean,
    onRetryVideoInfo: () -> Unit,
    loadComments: suspend (VideoCommentOrder, String) -> VideoCommentPage<String>,
    loadReplies: suspend (Long, Int) -> VideoCommentPage<Int>,
): PlayerExtraDetailPane {
    val scope = rememberCoroutineScope()
    var order by remember(videoKey) { mutableStateOf(VideoCommentOrder.HOT) }
    var selectedThread by remember(videoKey) { mutableStateOf<VideoComment?>(null) }
    val pager = remember(videoKey, order, scope) {
        val selectedOrder = order
        OnlineCommentPager("", scope) { offset -> loadComments(selectedOrder, offset) }
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    selectedThread?.let { root ->
        CommentRepliesDialog(root, loadReplies) { selectedThread = null }
    }

    return PlayerExtraDetailPane.ScrollableList {
        if (!canLoad) {
            item {
                if (awaitingVideoInfo) {
                    CommentLoading()
                } else {
                    CommentNotice("暂时无法获取视频信息") {
                        CommentButton("重试", onRetryVideoInfo)
                    }
                }
            }
        } else {
            item(key = "comment-order") {
                LaunchedEffect(pager) { pager.loadInitial() }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(order == VideoCommentOrder.HOT, { order = VideoCommentOrder.HOT }, { Text("热门") })
                    FilterChip(order == VideoCommentOrder.NEWEST, { order = VideoCommentOrder.NEWEST }, { Text("最新") })
                }
            }
            commentItems(pager, "暂无评论") { selectedThread = it }
        }
    }
}

@Composable
private fun CommentRepliesDialog(
    root: VideoComment,
    loadReplies: suspend (Long, Int) -> VideoCommentPage<Int>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = remember(root.id, scope) { OnlineCommentPager(1, scope) { page -> loadReplies(root.id, page) } }
    LaunchedEffect(pager) { pager.loadInitial() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.8f),
            shape = RoundedCornerShape(20.dp),
            color = PlaybackSurface,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("评论回复", Modifier.weight(1f), color = PlaybackInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    CommentButton("关闭", onDismiss)
                }
                HorizontalDivider(color = PlaybackDivider)
                LazyColumn(Modifier.weight(1f)) {
                    item(key = "root-comment") { CommentRow(root) }
                    commentItems(pager, "暂无回复")
                }
            }
        }
    }
}

private fun LazyListScope.commentItems(
    pager: OnlineCommentPager<*>,
    emptyMessage: String,
    onOpenReplies: ((VideoComment) -> Unit)? = null,
) {
    items(pager.comments, key = { it.id }) { comment ->
        CommentRow(comment, onOpenReplies?.let { { it(comment) } })
    }
    item(key = "comment-status") {
        val error = pager.error
        when {
            error != null -> CommentNotice(error) { CommentButton("重试", pager::loadMore) }
            pager.isLoading || pager.awaitingInitialLoad -> CommentLoading()
            pager.hasMore -> CommentNotice { CommentButton("加载更多", pager::loadMore) }
            pager.comments.isEmpty() -> CommentNotice(emptyMessage)
            else -> CommentNotice("没有更多了")
        }
    }
}

@Composable
private fun CommentRow(comment: VideoComment, onOpenReplies: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RemoteCover(normalizeImageUrl(comment.avatarUrl).takeIf { it.startsWith("https://") }.orEmpty(), Modifier.size(32.dp), CircleShape) {
            Text(comment.author.take(1), color = PlaybackMuted, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (comment.pinned) Text("置顶", color = OnlinePink, fontSize = 11.sp)
                Text(comment.author, color = PlaybackMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(comment.message.ifBlank { "（无文字内容）" }, color = PlaybackInk, fontSize = 14.sp, lineHeight = 21.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (comment.timestampSeconds > 0) {
                    Text(remember(comment.timestampSeconds) { formatPublishDateTime(comment.timestampSeconds) }, color = PlaybackMuted, fontSize = 11.sp)
                }
                Text("赞 ${formatCompactCount(comment.likes)}", color = PlaybackMuted, fontSize = 11.sp)
            }
            if (onOpenReplies != null && comment.replyCount > 0) {
                CommentButton("查看 ${formatCompactCount(comment.replyCount.toLong())} 条回复", onOpenReplies)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 58.dp, end = 16.dp), color = PlaybackDivider)
}

@Composable
private fun CommentButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = OnlinePink, fontSize = 13.sp) }
}

@Composable
private fun CommentNotice(message: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        message?.let { Text(it, color = PlaybackMuted, fontSize = 13.sp) }
        action?.invoke()
    }
}

@Composable
private fun CommentLoading() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = OnlinePink, strokeWidth = 2.dp)
    }
}
