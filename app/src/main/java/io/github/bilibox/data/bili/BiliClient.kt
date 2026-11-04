package io.github.bilibox.data.bili

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

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

    /**
     * 拉取一个分 P 的弹幕 XML。接口用 `Content-Encoding: deflate` 返回原始压缩
     * 数据，[decodeDanmakuPayload] 会在交给解析器前统一解压。
     */
    suspend fun danmakuXml(cid: Long): String {
        require(cid > 0L) { "cid 必须大于 0" }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$DANMAKU_URL?oid=$cid")
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Accept", "text/xml, application/xml, */*")
            buildCookieHeader()?.let { request.header("Cookie", it) }
            http.newCall(request.build()).execute().use { response ->
                val body = response.body?.bytes() ?: throw BiliException(
                    response.code,
                    "弹幕接口返回空内容",
                )
                if (!response.isSuccessful) {
                    throw BiliException(response.code, "弹幕加载失败（HTTP ${response.code}）")
                }
                decodeDanmakuPayload(body)
            }
        }
    }

    /**
     * 发送一条滚动弹幕。需要登录 Cookie 中的 `bili_jct` 作为 CSRF 令牌；
     * 发送成功后由调用方把新弹幕立即插入本地时间轴，无需重新拉取整段 XML。
     */
    suspend fun sendDanmaku(
        bvid: String,
        cid: Long,
        message: String,
        progressMs: Long,
        color: Int = 0xFFFFFF,
        fontSize: Int = 25,
    ) {
        val text = message.trim()
        require(text.isNotEmpty()) { "弹幕内容不能为空" }
        require(text.length <= 100) { "弹幕最多 100 个字符" }
        require(cid > 0L) { "cid 必须大于 0" }
        val csrf = csrfToken() ?: throw BiliException(-101, "请先登录后发送弹幕")
        val form = FormBody.Builder()
            .add("type", "1")
            .add("oid", cid.toString())
            .add("msg", text)
            .add("bvid", bvid)
            .add("progress", progressMs.coerceAtLeast(0L).toString())
            .add("color", (color and 0xFFFFFF).toString())
            .add("fontsize", fontSize.coerceIn(12, 64).toString())
            .add("pool", "0")
            .add("mode", "1")
            .add("plat", "2")
            .add("csrf", csrf)
            .build()
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(DANMAKU_POST_URL)
                .post(form)
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/video/$bvid")
                .header("Origin", "https://www.bilibili.com")
                .header("Accept", "application/json, text/plain, */*")
            buildCookieHeader()?.let { request.header("Cookie", it) }
            http.newCall(request.build()).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isEmpty()) {
                    throw BiliException(response.code, "发送弹幕失败（HTTP ${response.code}）")
                }
                val parsed = json.decodeFromString<ApiResponse<JsonObject>>(payload)
                if (parsed.code != 0) {
                    throw BiliException(parsed.code, danmakuPostMessage(parsed.code, parsed.message))
                }
            }
        }
    }

    private fun csrfToken(): String? = cookieHeader
        .orEmpty()
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("bili_jct=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    private fun danmakuPostMessage(code: Int, message: String): String = when (code) {
        -101 -> "请先登录后发送弹幕"
        -111 -> "登录凭证已失效，请重新登录"
        -400 -> "弹幕内容不符合规范"
        -403 -> "发送过于频繁或权限不足，请稍后再试"
        -412 -> "发送请求被风控拦截，请稍后再试"
        else -> message.ifBlank { "发送弹幕失败（$code）" }
    }

    /** 使用 Web 端 WBI 签名搜索视频，支持排序、时长 / 发布时间筛选和分页。 */
    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        order: String = SEARCH_ORDER_TOTALRANK,
        duration: String? = null,
        pubtimeBegin: Long? = null,
        pubtimeEnd: Long? = null,
        pageSize: Int = 20,
    ): VideoSearchPage {
        require(keyword.isNotBlank()) { "搜索关键词不能为空" }
        val currentPage = page.coerceAtLeast(1)
        val size = pageSize.coerceIn(1, 50)
        val params = buildMap {
            put("search_type", "video")
            put("keyword", keyword.trim())
            put("page", currentPage.toString())
            put("pagesize", size.toString())
            put("order", order)
            put("web_location", "1430654")
            if (!duration.isNullOrBlank()) put("duration", duration)
            if (pubtimeBegin != null && pubtimeEnd != null) {
                put("pubtime_begin_s", pubtimeBegin.toString())
                put("pubtime_end_s", pubtimeEnd.toString())
            }
        }
        // 未登录时只带 buvid3 的请求会被 B 站下发 v_voucher 风控并返回空结果，
        // 所以匿名搜索不带指纹 Cookie；已登录请求保持原有指纹逻辑。
        val anonymous = cookieHeader.isNullOrBlank() && sessData.isNullOrBlank()
        if (!anonymous) buvid()
        var response = getJson<JsonObject>(signedSearchUrl(params, false), includeFingerprint = !anonymous)
        if (response.code == CODE_RISK || response.code == CODE_SIGN_EXPIRED) {
            if (!anonymous) {
                buvid3 = null
                runCatching { buvid() }
            }
            response = getJson(signedSearchUrl(params, true), includeFingerprint = !anonymous)
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
        return VideoSearchPage(items, currentPage, hasMore, totalResults)
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
            // PiliPlus sends the same risk-control payload; without it the
            // playurl response can be degraded to low-quality/PCDN-only URLs.
            "dm_img_list" to "[]",
            "dm_img_str" to randomBase64(16, 64),
            "dm_cover_img_str" to randomBase64(32, 128),
            "dm_img_inter" to """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}""",
            "gaia_source" to "pre-load",
            "isGaiaAvoided" to "true",
            "web_location" to "1315873",
        )
        return requestPlayData(params)
    }

    /** Requests the single-file MP4 stream whose HTML5 platform avoids Referer anti-hotlink checks. */
    suspend fun playUrlHtml5(bvid: String, cid: Long, qn: Int = BiliQn.P1080): PlayData {
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to FNVAL_DURL.toString(),
            "fnver" to "0",
            "fourk" to "0",
            "platform" to "html5",
            "high_quality" to "1",
            "try_look" to "1",
        )
        return requestPlayData(params)
    }

    private suspend fun requestPlayData(params: Map<String, String>): PlayData {
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

    private suspend inline fun <reified T : Any> getJson(
        url: String,
        withCookie: Boolean = true,
        includeFingerprint: Boolean = true,
    ): ApiResponse<T> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Accept", "application/json, text/plain, */*")
            if (withCookie) buildCookieHeader(includeFingerprint)?.let { builder.header("Cookie", it) }
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isEmpty()) throw BiliException(response.code, "HTTP ${response.code} $url")
                json.decodeFromString<ApiResponse<T>>(body)
            }
        }

    private fun buildCookieHeader(includeFingerprint: Boolean = true): String? {
        val existing = cookieHeader?.takeIf { it.isNotBlank() }
            ?: sessData?.takeIf { it.isNotBlank() }?.let { "SESSDATA=$it" }
        val fingerprint = if (includeFingerprint) {
            buvid3?.takeIf { !existing.orEmpty().contains("buvid3=") }?.let { "buvid3=$it" }
        } else {
            null
        }
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

    private fun randomBase64(minLength: Int, maxLength: Int): String {
        val random = java.util.Random()
        val length = minLength + random.nextInt(maxLength - minLength + 1)
        val bytes = ByteArray(length) { (0x26 + random.nextInt(0x59)).toByte() }
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        return encoded.dropLast(2)
    }

    companion object {
        const val QN_MAX = 127
        const val FNVAL_DASH_ALL = 4048
        const val FNVAL_DURL = 1
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val USER_CARD_URL = "https://api.bilibili.com/x/web-interface/card"
        private const val DANMAKU_URL = "https://api.bilibili.com/x/v1/dm/list.so"
        private const val DANMAKU_POST_URL = "https://api.bilibili.com/x/v2/dm/post"
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
            // Bound metadata/playurl requests too: a stalled API call used to keep
            // the player on "正在获取视频流" for the whole read timeout.
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
