package com.example.bilibox.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BiliException(val code: Int, message: String) : Exception("[$code] $message")

/** B 站公开视频详情和播放地址客户端。 */
class BiliClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** 可传入登录模块保存的完整 Cookie 头，格式如 SESSDATA=...; bili_jct=...。 */
    @Volatile
    var cookieHeader: String? = null

    /** 兼容只提供 SESSDATA 值的调用方。 */
    @Volatile
    var sessData: String? = null

    @Volatile private var buvid3: String? = null
    @Volatile private var mixinKey: String? = null
    @Volatile private var mixinKeyAt = 0L
    private val keyLock = Mutex()
    private val buvidLock = Mutex()

    suspend fun warmUp() { runCatching { buvid() } }

    suspend fun videoInfo(bvid: String): VideoInfo {
        val response = getJson<VideoInfo>("$VIEW_URL?bvid=${encodeQuery(bvid)}")
        ensureOk(response)
        return response.data ?: throw BiliException(response.code, "view 返回空 data")
    }

    suspend fun userCard(mid: Long): UserCardData {
        val response = getJson<UserCardData>("$USER_CARD_URL?mid=$mid")
        ensureOk(response)
        return response.data ?: throw BiliException(response.code, "card 返回空 data")
    }

    /** 使用 Web 端 WBI 签名搜索视频，支持排序和分页。 */
    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        order: String = SEARCH_ORDER_TOTALRANK,
        pageSize: Int = 20,
    ): VideoSearchPage {
        require(keyword.isNotBlank()) { "搜索关键词不能为空" }
        val currentPage = page.coerceAtLeast(1)
        val size = pageSize.coerceIn(1, 50)
        val params = mapOf(
            "search_type" to "video",
            "keyword" to keyword.trim(),
            "page" to currentPage.toString(),
            "pagesize" to size.toString(),
            "order" to order,
            "web_location" to "1430654",
        )
        buvid()
        var response = getJson<JsonObject>(signedSearchUrl(params, false))
        if (response.code == CODE_RISK || response.code == CODE_SIGN_EXPIRED) {
            buvid3 = null
            runCatching { buvid() }
            response = getJson(signedSearchUrl(params, true))
        }
        ensureOk(response)
        val data = response.data ?: return VideoSearchPage(page = currentPage)
        val result = data["result"]?.jsonArray.orEmpty()
        val items = result.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val bvid = item.stringValue("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            VideoSearchItem(
                title = item.stringValue("title"),
                author = item.stringValue("author").ifBlank { "未知 UP 主" },
                play = item.stringValue("play").ifBlank { "--" },
                commentCount = item.stringValue("video_review")
                    .ifBlank { item.stringValue("review") }
                    .toLongOrNull() ?: 0L,
                publishTime = item.stringValue("pubdate").toLongOrNull() ?: 0L,
                duration = item.stringValue("duration").ifBlank { "--:--" },
                bvid = bvid,
                pic = item.stringValue("pic"),
            )
        }
        val totalPages = data.stringValue("numPages").toIntOrNull() ?: 0
        val totalResults = data.stringValue("numResults").toIntOrNull() ?: 0
        val hasMore = totalPages > currentPage ||
            (totalPages == 0 && (totalResults > currentPage * size || items.size >= size))
        return VideoSearchPage(items, currentPage, hasMore)
    }

    suspend fun playUrl(
        bvid: String,
        cid: Long,
        qn: Int = QN_MAX,
        fnval: Int = FNVAL_DASH_ALL,
    ): PlayData {
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to fnval.toString(),
            "fnver" to "0",
            "fourk" to "1",
            // The Web API otherwise caps anonymous playback below 720P. This
            // flag lets an anonymous request try the 64/80 streams documented
            // by Bilibili, while logged-in requests remain compatible.
            "try_look" to "1",
        )
        var response = getJson<PlayData>(signedPlayUrl(params, false))
        if (response.code == CODE_RISK || response.code == CODE_SIGN_EXPIRED) {
            buvid3 = null
            runCatching { buvid() }
            response = getJson(signedPlayUrl(params, true))
        }
        ensureOk(response)
        return response.data ?: throw BiliException(response.code, "playurl 返回空 data")
    }

    private suspend fun signedPlayUrl(params: Map<String, String>, forceRefreshKey: Boolean): String =
        "$PLAY_URL?${WbiSigner.sign(params, obtainMixinKey(forceRefreshKey))}"

    private suspend fun signedSearchUrl(params: Map<String, String>, forceRefreshKey: Boolean): String =
        "$SEARCH_URL?${WbiSigner.sign(params, obtainMixinKey(forceRefreshKey))}"

    private suspend fun obtainMixinKey(force: Boolean): String {
        val cached = mixinKey
        if (!force && cached != null && System.currentTimeMillis() - mixinKeyAt < KEY_TTL_MS) return cached
        return keyLock.withLock {
            val current = mixinKey
            if (!force && current != null && System.currentTimeMillis() - mixinKeyAt < KEY_TTL_MS) current
            else {
                val nav = getJson<NavData>(NAV_URL, withCookie = false)
                val image = nav.data?.wbiImg?.imgUrl.orEmpty()
                val sub = nav.data?.wbiImg?.subUrl.orEmpty()
                if (image.isEmpty() || sub.isEmpty()) throw BiliException(nav.code, "nav 未返回 wbi_img: ${nav.message}")
                WbiSigner.mixinKey(WbiSigner.extractKey(image), WbiSigner.extractKey(sub)).also {
                    mixinKey = it
                    mixinKeyAt = System.currentTimeMillis()
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> getJson(url: String, withCookie: Boolean = true): ApiResponse<T> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Accept", "application/json, text/plain, */*")
            if (withCookie) buildCookieHeader()?.let { builder.header("Cookie", it) }
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isEmpty()) throw BiliException(response.code, "HTTP ${response.code} $url")
                json.decodeFromString<ApiResponse<T>>(body)
            }
        }

    private fun buildCookieHeader(): String? {
        val existing = cookieHeader?.takeIf { it.isNotBlank() }
            ?: sessData?.takeIf { it.isNotBlank() }?.let { "SESSDATA=$it" }
        val fingerprint = buvid3?.takeIf { !existing.orEmpty().contains("buvid3=") }
            ?.let { "buvid3=$it" }
        return listOfNotNull(existing, fingerprint).joinToString("; ").takeIf { it.isNotBlank() }
    }

    private suspend fun buvid(): String? {
        buvid3?.let { return it }
        return buvidLock.withLock {
            buvid3 ?: runCatching { getJson<BuvidData>(SPI_URL, false).data?.b3 }
                .getOrNull()?.takeIf { it.isNotBlank() }?.also { buvid3 = it }
        }
    }

    private fun ensureOk(response: ApiResponse<*>) {
        if (response.code != 0) throw BiliException(response.code, response.message.ifBlank { "未知错误" })
    }

    companion object {
        const val QN_MAX = 127
        const val FNVAL_DASH_ALL = 4048
        const val FNVAL_DURL = 1
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val USER_CARD_URL = "https://api.bilibili.com/x/web-interface/card"
        private const val PLAY_URL = "https://api.bilibili.com/x/player/wbi/playurl"
        private const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        private const val SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val KEY_TTL_MS = 10 * 60 * 1000L
        private const val CODE_RISK = -352
        private const val CODE_SIGN_EXPIRED = -403

        const val SEARCH_ORDER_TOTALRANK = "totalrank"
        const val SEARCH_ORDER_CLICK = "click"
        const val SEARCH_ORDER_PUBDATE = "pubdate"
        const val SEARCH_ORDER_DM = "dm"
        const val SEARCH_ORDER_STOW = "stow"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
