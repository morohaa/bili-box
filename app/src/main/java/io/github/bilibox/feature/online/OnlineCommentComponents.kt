package io.github.bilibox.feature.online

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.RemoteCover
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackStroke
import io.github.bilibox.core.ui.theme.playbackColor
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import java.text.NumberFormat

internal val CommentInset: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFF7F8FA), Color(0xFF2A2D33))

@Composable
internal fun CommentOrderBar(order: VideoCommentOrder, totalCount: Long?, onOrderChange: (VideoCommentOrder) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommentOrderPill("热门", order == VideoCommentOrder.HOT) { onOrderChange(VideoCommentOrder.HOT) }
            CommentOrderPill("最新", order == VideoCommentOrder.NEWEST) { onOrderChange(VideoCommentOrder.NEWEST) }
        }
        if (totalCount != null) {
            val count = remember(totalCount) { NumberFormat.getIntegerInstance().format(totalCount) }
            Text("共 $count 条评论", Modifier.weight(1f), color = PlaybackMuted, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 2)
        }
    }
    HorizontalDivider(color = PlaybackDivider)
}

@Composable
private fun CommentOrderPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val activeFill = playbackColor(Color(0xFFECE9FA), Color(0xFF3C345A))
    val activeInk = playbackColor(Color(0xFF5948D4), Color(0xFFD2C8FF))
    Box(
        Modifier.heightIn(min = 40.dp).clip(shape)
            .then(if (selected) Modifier.background(activeFill) else Modifier.border(1.dp, PlaybackStroke, shape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) activeInk else PlaybackMuted, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun CommentFloor(
    comment: VideoComment,
    ownerId: Long,
    compact: Boolean = false,
    inset: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val avatar = normalizeImageUrl(comment.avatarUrl).takeIf { it.startsWith("https://") }.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (inset) 0.dp else 16.dp, vertical = if (compact) 10.dp else 15.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
    ) {
        RemoteCover(avatar, Modifier.size(if (compact) 26.dp else 34.dp).border(1.dp, PlaybackDivider, CircleShape), CircleShape) {
            Box(Modifier.matchParentSize().background(CommentInset), contentAlignment = Alignment.Center) {
                Text(comment.author.take(1), color = PlaybackMuted, fontSize = 13.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CommentIdentity(comment, ownerId)
            Text(
                comment.message.ifBlank { "（无文字内容）" },
                color = PlaybackInk,
                fontSize = if (compact) 14.sp else 15.sp,
                lineHeight = if (compact) 20.sp else 23.sp,
            )
            CommentMetadata(comment)
            if (footer != null) Box(Modifier.padding(top = 4.dp)) { footer() }
        }
    }
}

@Composable
private fun CommentIdentity(comment: VideoComment, ownerId: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (comment.pinned) {
            CommentBadge("置顶", playbackColor(Color(0xFF9E4C00), Color(0xFFFFBC73)), playbackColor(Color(0xFFFFF3E5), Color(0xFF533823)))
        }
        Text(comment.author, Modifier.weight(1f, fill = false), color = PlaybackMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (ownerId > 0L && comment.authorId == ownerId) {
            CommentBadge("UP", playbackColor(Color(0xFFB52E61), Color(0xFFFFA3C1)), Pink.copy(alpha = .15f))
        }
    }
}

@Composable
private fun CommentBadge(label: String, ink: Color, fill: Color) {
    Text(
        label,
        Modifier.background(fill, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        color = ink,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentMetadata(comment: VideoComment) {
    val muted = PlaybackMuted
    val likes = formatCompactCount(comment.likes)
    FlowRow(
        Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (comment.timestampSeconds > 0) {
            Text(remember(comment.timestampSeconds) { formatPublishDateTime(comment.timestampSeconds) }, color = muted, fontSize = 11.sp)
        }
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = "$likes 个赞" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnlineActionIcon("like", muted, Modifier.size(13.dp))
            Text(likes, color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun CommentReplyPreview(comment: VideoComment, onClick: () -> Unit) {
    val authorInk = PlaybackMuted
    val accent = playbackColor(Color(0xFFB52E61), Color(0xFFFFA3C1))
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).background(CommentInset)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        comment.previewReply?.let { preview ->
            val teaser = buildAnnotatedString {
                withStyle(SpanStyle(color = authorInk, fontWeight = FontWeight.Medium)) { append("${preview.author}：") }
                append(preview.message.ifBlank { "（无文字内容）" })
            }
            Text(teaser, color = PlaybackInk, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("查看 ${formatCompactCount(comment.replyCount.toLong())} 条回复", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Canvas(Modifier.size(12.dp)) {
                val start = Offset(size.width * .38f, size.height * .22f)
                val point = Offset(size.width * .66f, size.height * .5f)
                val end = Offset(start.x, size.height - start.y)
                drawLine(accent, start, point, 1.3.dp.toPx(), StrokeCap.Round)
                drawLine(accent, point, end, 1.3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun CommentSheetClose(onClick: () -> Unit) {
    val ink = PlaybackMuted
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "关闭评论详情" }) {
        Box(Modifier.size(28.dp).background(CommentInset, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(12.dp)) {
                val start = Offset(size.width * .17f, size.height * .17f)
                val end = Offset(size.width - start.x, size.height - start.y)
                drawLine(ink, start, end, 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(ink, Offset(end.x, start.y), Offset(start.x, end.y), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
