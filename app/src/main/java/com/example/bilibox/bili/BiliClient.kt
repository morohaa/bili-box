package com.example.bilibox.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
        private const val PLAY_URL = "https://api.bilibili.com/x/player/wbi/playurl"
        private const val SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val KEY_TTL_MS = 10 * 60 * 1000L
        private const val CODE_RISK = -352
        private const val CODE_SIGN_EXPIRED = -403

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
