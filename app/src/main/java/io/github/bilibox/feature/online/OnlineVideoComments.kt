package io.github.bilibox.feature.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import io.github.bilibox.data.bili.VideoCommentPage
import io.github.bilibox.feature.player.PlayerDetailBarHeight
import io.github.bilibox.feature.player.PlayerDetailsOverlayEnter
import io.github.bilibox.feature.player.PlayerDetailsOverlayExit
import io.github.bilibox.feature.player.PlayerExtraDetailPane

@Composable
internal fun rememberOnlineVideoComments(
    client: BiliClient,
    aid: Long?,
    ownerId: Long,
    awaitingVideoInfo: Boolean,
    onRetryVideoInfo: () -> Unit,
): PlayerExtraDetailPane = onlineVideoCommentsPane(
    videoKey = client to aid,
    canLoad = aid != null && aid > 0,
    awaitingVideoInfo = awaitingVideoInfo,
    onRetryVideoInfo = onRetryVideoInfo,
    ownerId = ownerId,
    loadComments = { order, offset -> client.videoComments(requireNotNull(aid), offset, order) },
    loadReplies = { root, page -> client.videoCommentReplies(requireNotNull(aid), root, page) },
    loadDialog = { root, dialog, minFloor -> client.videoCommentDialog(requireNotNull(aid), root, dialog, minFloor) },
)

@Composable
internal fun onlineVideoCommentsPane(
    videoKey: Any,
    canLoad: Boolean,
    awaitingVideoInfo: Boolean,
    onRetryVideoInfo: () -> Unit,
    loadComments: suspend (VideoCommentOrder, String) -> VideoCommentPage<String>,
    loadReplies: suspend (Long, Int) -> VideoCommentPage<Int>,
    loadDialog: suspend (Long, Long, Long) -> VideoCommentPage<Long>,
    ownerId: Long = 0L,
): PlayerExtraDetailPane {
    val scope = rememberCoroutineScope()
    var order by remember(videoKey) { mutableStateOf(VideoCommentOrder.HOT) }
    var panelRoot by remember(videoKey, order) { mutableStateOf<VideoComment?>(null) }
    var selectedReply by remember(videoKey, order, panelRoot?.id) { mutableStateOf<VideoComment?>(null) }
    val pager = remember(videoKey, order, scope) {
        val selectedOrder = order
        OnlineCommentPager("", scope) { offset -> loadComments(selectedOrder, offset) }
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    val repliesOverlay = panelRoot?.let { root ->
        val dismiss = { if (selectedReply != null) selectedReply = null else panelRoot = null }
        PlayerExtraDetailPane.Overlay(onDismiss = dismiss) { modifier ->
            val replies = rememberCommentReplies(videoKey, root.id, loadReplies)
            CommentRepliesPanel(modifier, root, ownerId, replies, selectedReply, loadDialog, { selectedReply = it }, dismiss)
        }
    }

    return PlayerExtraDetailPane.ScrollableList(
        onVisible = {
            if (canLoad) LaunchedEffect(pager) { pager.loadInitial() }
        },
        overlay = repliesOverlay,
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

        item(key = "comment-order") { CommentOrderBar(order) { order = it } }
        items(pager.comments, key = { it.id }) { comment ->
            Column {
                CommentFloor(comment, ownerId, collapseLongMessage = true) {
                    if (comment.replyCount > 0) {
                        CommentReplyEntry(comment.replyCount) { panelRoot = comment }
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
    val pager = remember(videoKey, rootId, scope) {
        OnlineCommentPager(1, scope, loadAllPages = true) { page -> loadReplies(rootId, page) }
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    return pager
}

@Composable
private fun CommentRepliesPanel(
    modifier: Modifier,
    root: VideoComment,
    ownerId: Long,
    pager: OnlineCommentPager<Int>,
    selectedReply: VideoComment?,
    loadDialog: suspend (Long, Long, Long) -> VideoCommentPage<Long>,
    onOpenDialog: (VideoComment) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(pager) { pager.loadInitial() }
    var appliedSort by remember(pager) { mutableStateOf<ReplySort?>(null) }
    val listState = rememberLazyListState()
    val sharedDialogs = remember(pager.comments) {
        pager.comments.groupingBy(VideoComment::dialogId).eachCount()
    }
    val replies = remember(pager.comments, appliedSort) {
        val sorted = appliedSort?.replies.orEmpty()
        if (sorted.isEmpty()) pager.comments else sorted + pager.comments.drop(sorted.size)
    }
    AnimatedContent(
        targetState = selectedReply,
        modifier = modifier.clipToBounds().testTag("comment-replies-panel"),
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                (PlayerDetailsOverlayEnter togetherWith ExitTransition.KeepUntilTransitionsFinished)
                    .apply { targetContentZIndex = 1f }
            } else {
                EnterTransition.None togetherWith PlayerDetailsOverlayExit
            }
        },
        label = "评论对话面板切换",
    ) { dialogReply ->
        val inDialog = dialogReply != null
        val pageModifier = Modifier.fillMaxSize().background(PlaybackSurface)
        Column(if (inDialog) pageModifier.testTag("comment-dialog-panel") else pageModifier) {
            CommentPanelHeader(inDialog, onDismiss)
            if (dialogReply != null) {
                CommentDialogList(root.id, ownerId, dialogReply, loadDialog)
                return@Column
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                item(key = "thread-root") { CommentFloor(root, ownerId, inThread = true) }
                item(key = "thread-order") {
                    Column {
                        HorizontalDivider(thickness = 8.dp, color = CommentInset)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "相关回复共${root.replyCount}条",
                                Modifier.weight(1f).padding(end = 8.dp),
                                color = PlaybackMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            CommentOrderToggle(appliedSort?.order) { order ->
                                appliedSort = ReplySort(order, sortedReplies(pager.comments, order))
                            }
                        }
                    }
                }
                items(replies, key = { it.id }) { reply ->
                    val hasDialog = reply.dialogId > 0L && (reply.dialogId != reply.id || (sharedDialogs[reply.dialogId] ?: 0) > 1)
                    val onViewDialog: (() -> Unit)? = if (hasDialog) ({ onOpenDialog(reply) }) else null
                    CommentThreadReply(reply, ownerId, onViewDialog = onViewDialog)
                }
                item(key = "reply-status") {
                    CommentLoadState(pager, "暂无回复", loadingMoreMessage = "正在加载更多回复")
                }
            }
        }
    }
}

private data class ReplySort(val order: VideoCommentOrder, val replies: List<VideoComment>)

private fun sortedReplies(replies: List<VideoComment>, order: VideoCommentOrder): List<VideoComment> {
    val newestFirst = compareByDescending<VideoComment> { it.timestampSeconds }.thenByDescending { it.id }
    val ordering = if (order == VideoCommentOrder.HOT) {
        compareByDescending<VideoComment> { it.likes }.then(newestFirst)
    } else {
        newestFirst
    }
    return replies.sortedWith(ordering)
}

@Composable
private fun CommentPanelHeader(inDialog: Boolean, onDismiss: () -> Unit) {
    val title = if (inDialog) "对话列表" else "评论详情"
    val headerTag = if (inDialog) "comment-dialog-header" else "comment-replies-header"
    Row(
        Modifier.fillMaxWidth().height(PlayerDetailBarHeight).testTag(headerTag).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = PlaybackInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { contentDescription = "关闭$title" },
        ) {
            AppGlyphIcon(AppIcon.CLOSE, PlaybackMuted, Modifier.size(22.dp))
        }
    }
    HorizontalDivider(color = PlaybackDivider, thickness = 1.dp)
}

@Composable
private fun CommentDialogList(
    rootId: Long,
    ownerId: Long,
    selectedReply: VideoComment,
    loadDialog: suspend (Long, Long, Long) -> VideoCommentPage<Long>,
) {
    val scope = rememberCoroutineScope()
    val pager = remember(rootId, selectedReply.dialogId, scope) {
        OnlineCommentPager(0L, scope, loadAllPages = true) { minFloor -> loadDialog(rootId, selectedReply.dialogId, minFloor) }
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    LaunchedEffect(pager) { pager.loadInitial() }
    val replies = if (pager.hasMore) {
        emptyList()
    } else {
        remember(pager.comments) { pager.comments.sortedWith(compareBy<VideoComment> { it.timestampSeconds }.thenBy { it.id }) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedReply.id, replies) {
        val index = replies.indexOfFirst { it.id == selectedReply.id }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == selectedReply.id }) {
            listState.requestScrollToItem(index)
        }
    }
    LazyColumn(Modifier.fillMaxWidth().testTag("comment-dialog-list"), state = listState) {
        items(replies, key = { it.id }) { reply ->
            CommentThreadReply(reply, ownerId, highlighted = reply.id == selectedReply.id)
        }
        item(key = "dialog-status") { CommentLoadState(pager, "暂无对话") }
    }
}

@Composable
private fun CommentThreadReply(
    reply: VideoComment,
    ownerId: Long,
    highlighted: Boolean = false,
    onViewDialog: (() -> Unit)? = null,
) {
    val modifier = if (highlighted) Modifier.background(Pink.copy(alpha = .12f)).semantics { selected = true } else Modifier
    Column(modifier) {
        CommentFloor(reply, ownerId, inThread = true, onViewDialog = onViewDialog)
        HorizontalDivider(Modifier.padding(start = 54.dp, end = 12.dp), color = PlaybackDivider)
    }
}

@Composable
private fun CommentLoadState(pager: OnlineCommentPager<*>, emptyMessage: String, loadingMoreMessage: String? = null) {
    val error = pager.error
    when {
        error != null -> CommentNotice(error, "重试", pager::loadMore)
        pager.isLoading || pager.awaitingInitialLoad -> CommentLoading(loadingMoreMessage?.takeIf { pager.comments.isNotEmpty() })
        pager.hasMore -> CommentNotice(action = "加载更多", onClick = pager::loadMore)
        pager.comments.isEmpty() -> CommentNotice(emptyMessage)
        else -> CommentNotice("没有更多了")
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
private fun CommentLoading(message: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Pink, strokeWidth = 2.dp)
        if (message != null) Text(message, color = PlaybackMuted, fontSize = 12.sp)
    }
}
