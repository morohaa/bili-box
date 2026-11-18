package io.github.bilibox.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import io.github.bilibox.data.bili.VideoCommentPage
import io.github.bilibox.feature.player.PlayerExtraDetailPane

private const val InlineReplyLimit = 10

@Composable
internal fun rememberOnlineVideoComments(
    client: BiliClient,
    aid: Long?,
    ownerId: Long,
    totalCount: Long?,
    awaitingVideoInfo: Boolean,
    onRetryVideoInfo: () -> Unit,
): PlayerExtraDetailPane = onlineVideoCommentsPane(
    videoKey = client to aid,
    canLoad = aid != null && aid > 0,
    awaitingVideoInfo = awaitingVideoInfo,
    onRetryVideoInfo = onRetryVideoInfo,
    ownerId = ownerId,
    totalCount = totalCount,
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
    ownerId: Long = 0L,
    totalCount: Long? = null,
): PlayerExtraDetailPane {
    val scope = rememberCoroutineScope()
    var order by remember(videoKey) { mutableStateOf(VideoCommentOrder.HOT) }
    var inlineRoot by remember(videoKey, order) { mutableStateOf<Long?>(null) }
    var sheetRoot by remember(videoKey, order) { mutableStateOf<VideoComment?>(null) }
    val pager = remember(videoKey, order, scope) {
        val selectedOrder = order
        OnlineCommentPager("", scope) { offset -> loadComments(selectedOrder, offset) }
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    val inlineReplies = inlineRoot?.let { rememberCommentReplies(videoKey, it, loadReplies) }
    sheetRoot?.let { root ->
        CommentRepliesSheet(root, ownerId, rememberCommentReplies(videoKey, root.id, loadReplies)) { sheetRoot = null }
    }

    return PlayerExtraDetailPane.ScrollableList(
        header = {
            if (canLoad) {
                LaunchedEffect(pager) { pager.loadInitial() }
                CommentOrderBar(order, totalCount) { order = it }
            }
        },
    ) {
        if (!canLoad) {
            item {
                if (awaitingVideoInfo) {
                    CommentLoading()
                } else {
                    CommentNotice("暂时无法获取视频信息", "重试", onRetryVideoInfo)
                }
            }
            return@ScrollableList
        }

        items(pager.comments, key = { it.id }) { comment ->
            Column {
                CommentFloor(comment, ownerId) {
                    if (comment.id == inlineRoot && inlineReplies != null) {
                        InlineCommentReplies(inlineReplies, ownerId, onCollapse = { inlineRoot = null })
                    } else if (comment.replyCount > 0) {
                        CommentReplyPreview(comment) {
                            if (comment.replyCount <= InlineReplyLimit) inlineRoot = comment.id else sheetRoot = comment
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = PlaybackDivider)
            }
        }
        item(key = "comment-status") { CommentLoadState(pager, "暂无评论") }
    }
}

@Composable
private fun rememberCommentReplies(
    videoKey: Any,
    rootId: Long,
    loadReplies: suspend (Long, Int) -> VideoCommentPage<Int>,
): OnlineCommentPager<Int> {
    val scope = rememberCoroutineScope()
    val pager = remember(videoKey, rootId, scope) { OnlineCommentPager(1, scope) { page -> loadReplies(rootId, page) } }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    return pager
}

@Composable
private fun InlineCommentReplies(pager: OnlineCommentPager<Int>, ownerId: Long, onCollapse: () -> Unit) {
    LaunchedEffect(pager) { pager.loadInitial() }
    Column(
        Modifier.fillMaxWidth()
            .background(CommentInset, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        pager.comments.forEach { reply ->
            CommentFloor(reply, ownerId, compact = true, inset = true)
            HorizontalDivider(color = PlaybackDivider)
        }
        CommentLoadState(pager, "暂无回复", showEnd = false)
        CommentTextAction("收起回复", onCollapse)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentRepliesSheet(
    root: VideoComment,
    ownerId: Long,
    pager: OnlineCommentPager<Int>,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(pager) { pager.loadInitial() }
    val rootMaxHeight = (LocalConfiguration.current.screenHeightDp * .25f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PlaybackSurface,
        scrimColor = Color.Black.copy(alpha = .36f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 2.dp).width(36.dp).height(4.dp).background(PlaybackDivider, CircleShape))
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("评论详情", Modifier.weight(1f), color = PlaybackInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                CommentSheetClose(onDismiss)
            }
            HorizontalDivider(color = PlaybackDivider)
            Box(Modifier.fillMaxWidth().background(CommentInset).heightIn(max = rootMaxHeight).verticalScroll(rememberScrollState())) {
                CommentFloor(root, ownerId, compact = true)
            }
            HorizontalDivider(color = PlaybackDivider)
            Text(
                "全部回复（${formatCompactCount(root.replyCount.toLong())}）",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = PlaybackMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(Modifier.weight(1f)) {
                items(pager.comments, key = { it.id }) { reply ->
                    Column {
                        CommentFloor(reply, ownerId, compact = true)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = PlaybackDivider)
                    }
                }
                item(key = "reply-status-${pager.comments.size}") { CommentLoadState(pager, "暂无回复") }
            }
        }
    }
}

@Composable
private fun CommentLoadState(pager: OnlineCommentPager<*>, emptyMessage: String, showEnd: Boolean = true) {
    val error = pager.error
    when {
        error != null -> CommentNotice(error, "重试", pager::loadMore)
        pager.isLoading || pager.awaitingInitialLoad -> CommentLoading()
        pager.hasMore -> CommentNotice(action = "加载更多", onClick = pager::loadMore)
        pager.comments.isEmpty() -> CommentNotice(emptyMessage)
        showEnd -> CommentNotice("没有更多了")
    }
}

@Composable
private fun CommentNotice(message: String? = null, action: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        message?.let { Text(it, color = PlaybackMuted, fontSize = 13.sp, textAlign = TextAlign.Center) }
        if (action != null && onClick != null) CommentTextAction(action, onClick)
    }
}

@Composable
private fun CommentTextAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = Pink, fontSize = 13.sp) }
}

@Composable
private fun CommentLoading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Pink, strokeWidth = 2.dp)
    }
}
