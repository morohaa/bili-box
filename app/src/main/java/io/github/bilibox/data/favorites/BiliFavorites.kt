package io.github.bilibox.data.favorites

import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.BiliException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 只读当前登录账号的收藏，包含私密收藏夹；不保存或输出 Cookie。 */
internal class BiliFavorites(private val client: BiliClient) : FavoritesSource {
    override suspend fun accountId(): Long {
        val data = client.favoriteData("/x/web-interface/nav")
        val mid = data.number("mid")
        if (data.text("isLogin") != "true" || mid <= 0) throw BiliException(-101, "登录已失效，请重新登录")
        return mid
    }

    override suspend fun folders(mid: Long, page: Int): FavoriteFolderPage {
        require(mid > 0 && page > 0)
        val data = client.favoriteData(
            "/x/v3/fav/folder/created/list",
            mapOf("up_mid" to mid.toString(), "pn" to page.toString(), "ps" to "50"),
        )
        val items = data.objects("list").mapNotNull { item ->
            val id = item.number("id")
            if (id <= 0) return@mapNotNull null
            FavoriteFolder(
                id = id,
                title = item.text("title").ifBlank { "未命名收藏夹" },
                coverUrl = secureCover(item.text("cover")),
                count = item.number("media_count").toInt().coerceAtLeast(0),
                isPublic = item.number("attr") and 1L != 0L,
            )
        }
        return FavoriteFolderPage(items, data.text("has_more") == "true" || data.text("has_more") == "1")
    }

    override suspend fun videos(folderId: Long, page: Int, keyword: String, order: FavoriteOrder): FavoriteVideoPage {
        require(folderId > 0 && page > 0)
        val data = client.favoriteData(
            "/x/v3/fav/resource/list",
            mapOf(
                "media_id" to folderId.toString(),
                "pn" to page.toString(),
                "ps" to "20",
                "keyword" to keyword.trim(),
                "order" to order.parameter,
                "type" to "0",
                "tid" to "0",
                "platform" to "web",
            ),
        )
        val items = data.objects("medias").mapNotNull(::favoriteVideo)
        val total = (data["info"] as? JsonObject)?.number("media_count")?.toInt()?.coerceAtLeast(0) ?: 0
        return FavoriteVideoPage(items, data.text("has_more") == "true" || data.text("has_more") == "1", total)
    }
}

private fun favoriteVideo(item: JsonObject): FavoriteVideo? {
    val id = item.number("id")
    if (id <= 0) return null
    val title = item.text("title").ifBlank { "失效视频" }
    val bvid = item.text("bvid").ifBlank { item.text("bv_id") }
    val cover = secureCover(item.text("cover"))
    val author = (item["upper"] as? JsonObject)?.text("name").orEmpty().ifBlank { "未知 UP 主" }
    val seconds = item.number("duration").coerceAtLeast(0)
    val duration = if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
    val reason = when {
        item.number("attr") and 1L != 0L -> "视频已失效或无权访问"
        item.number("type") != 2L -> "暂不支持播放此类收藏内容"
        bvid.isBlank() -> "视频已失效或无权访问"
        else -> null
    }
    val counts = item["cnt_info"] as? JsonObject
    return FavoriteVideo(
        id = id,
        title = title,
        coverUrl = cover,
        author = author,
        duration = duration,
        savedAt = item.number("fav_time"),
        video = if (reason == null) {
            VideoSummary(
                title = title,
                author = author,
                playCount = counts?.number("play")?.toString() ?: "--",
                commentCount = counts?.number("danmaku") ?: 0L,
                publishTime = item.number("pubtime"),
                duration = duration,
                bvid = bvid,
                coverUrl = cover,
            )
        } else {
            null
        },
        unavailableReason = reason,
    )
}

private fun secureCover(value: String): String = when {
    value.startsWith("//") -> "https:$value"
    value.startsWith("http://") -> value.replaceFirst("http://", "https://")
    else -> value
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.number(key: String): Long = text(key).toLongOrNull() ?: 0L
private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
