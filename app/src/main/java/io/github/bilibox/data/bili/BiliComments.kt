package io.github.bilibox.data.bili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class VideoCommentOrder(val mode: String) {
    HOT("3"),
    NEWEST("2"),
}

internal data class VideoComment(
    val id: Long,
    val author: String,
    val avatarUrl: String,
    val message: String,
    val timestampSeconds: Long,
    val likes: Long,
    val replyCount: Int,
    val pinned: Boolean = false,
    val authorId: Long = 0L,
    val authorLevel: Int? = null,
    val previewReply: VideoComment? = null,
)

internal data class VideoCommentPage<Key : Any>(
    val comments: List<VideoComment>,
    val nextKey: Key?,
)

internal fun videoCommentParameters(aid: Long, offset: String, order: VideoCommentOrder): Map<String, String> = mapOf(
    "type" to "1",
    "oid" to aid.toString(),
    "mode" to order.mode,
    "pagination_str" to buildJsonObject { put("offset", offset) }.toString(),
)

@Serializable
internal data class VideoCommentListPayload(
    private val cursor: VideoCommentCursor? = null,
    @SerialName("top_replies") private val topReplies: List<VideoCommentPayload>? = null,
    private val replies: List<VideoCommentPayload>? = null,
    private val control: VideoCommentControl? = null,
) {
    fun toPage(firstPage: Boolean): VideoCommentPage<String> {
        val pageCursor = cursor ?: throw BiliException(-1, "评论接口未返回分页信息")
        val pinned = if (firstPage) topReplies.orEmpty().mapNotNull { it.toComment(pinned = true, includePreview = true) } else emptyList()
        val comments = (pinned + replies.orEmpty().mapNotNull { it.toComment(includePreview = true) }).distinctBy(VideoComment::id)
        if (comments.isEmpty() && !control?.message.isNullOrBlank()) {
            throw BiliException(-1, control?.message.orEmpty())
        }
        val next = pageCursor.pagination?.nextOffset?.takeIf { it.isNotBlank() && !pageCursor.isEnd }
        return VideoCommentPage(comments, next)
    }
}

@Serializable
internal data class VideoCommentCursor(
    @SerialName("is_end") val isEnd: Boolean = false,
    @SerialName("pagination_reply") val pagination: VideoCommentPagination? = null,
)

@Serializable
internal data class VideoCommentPagination(@SerialName("next_offset") val nextOffset: String = "")

@Serializable
internal data class VideoCommentControl(@SerialName("show_text") val message: String = "")

@Serializable
internal data class VideoCommentRepliesPayload(
    private val page: VideoCommentReplyPage? = null,
    private val replies: List<VideoCommentPayload>? = null,
) {
    fun toPage(requestedPage: Int): VideoCommentPage<Int> {
        val pagination = page ?: throw BiliException(-1, "回复接口未返回分页信息")
        val comments = replies.orEmpty().mapNotNull { it.toComment() }
        val hasMore = replies.orEmpty().isNotEmpty() && pagination.size > 0 &&
            requestedPage.toLong() * pagination.size < pagination.count
        return VideoCommentPage(comments, if (hasMore) requestedPage + 1 else null)
    }
}

@Serializable
internal data class VideoCommentReplyPage(val size: Int = 0, val count: Long = 0)

@Serializable
internal data class VideoCommentPayload(
    @SerialName("rpid_str") private val id: String? = null,
    private val rpid: Long = 0,
    @SerialName("ctime") private val timestamp: Long = 0,
    @SerialName("like") private val likes: Long = 0,
    @SerialName("rcount") private val replyCount: Int = 0,
    private val member: VideoCommentMember? = null,
    private val content: VideoCommentContent? = null,
    private val replies: List<VideoCommentPayload>? = null,
) {
    fun toComment(pinned: Boolean = false, includePreview: Boolean = false): VideoComment? {
        val commentId = (id?.toLongOrNull() ?: rpid).takeIf { it > 0 } ?: return null
        return VideoComment(
            id = commentId,
            author = member?.name.orEmpty().ifBlank { "用户" },
            avatarUrl = member?.avatar.orEmpty(),
            message = content?.message.orEmpty(),
            timestampSeconds = timestamp,
            likes = likes.coerceAtLeast(0L),
            replyCount = replyCount.coerceAtLeast(0),
            pinned = pinned,
            authorId = member?.mid ?: 0L,
            authorLevel = member?.levelInfo?.currentLevel,
            previewReply = if (includePreview) replies.orEmpty().firstNotNullOfOrNull { it.toComment() } else null,
        )
    }
}

@Serializable
internal data class VideoCommentMember(
    @SerialName("uname") val name: String = "",
    val avatar: String = "",
    val mid: Long = 0L,
    @SerialName("level_info") val levelInfo: VideoCommentLevelInfo? = null,
)

@Serializable
internal data class VideoCommentLevelInfo(@SerialName("current_level") val currentLevel: Int? = null)

@Serializable
internal data class VideoCommentContent(val message: String = "")
