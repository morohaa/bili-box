package io.github.bilibox.data.auth

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import org.json.JSONObject

internal data class LoginResponse(val json: JSONObject, val cookies: String)

/** 极验（geetest）验证所需参数，由 B 站 captcha 接口下发。 */
internal data class LoginCaptcha(val token: String, val gt: String, val challenge: String)

/** 极验验证结果，对应登录接口的 validate / seccode 参数。 */
internal data class GeetestProof(val validate: String, val seccode: String)

private const val loginUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"

/**
 * 访问哔哩哔哩通行证（passport）接口，统一带上 Referer 并收集 Set-Cookie。
 * 扫码登录与账号密码/短信登录共用这一份请求逻辑。
 */
internal fun biliLoginRequest(endpoint: String, cookie: String? = null, includeCookies: Boolean = true): LoginResponse = executeLoginRequest(endpoint, cookie, body = null, includeCookies = includeCookies)

internal fun biliLoginPost(endpoint: String, params: Map<String, String>, cookie: String? = null): LoginResponse = executeLoginRequest(endpoint, cookie, body = encodeForm(params), includeCookies = true)

private fun encodeForm(params: Map<String, String>): String = params.entries.joinToString("&") { (key, value) ->
    "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
}

private fun executeLoginRequest(endpoint: String, cookie: String?, body: String?, includeCookies: Boolean): LoginResponse {
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 15000
    connection.setRequestProperty("User-Agent", loginUserAgent)
    connection.setRequestProperty("Referer", "https://www.bilibili.com/")
    if (cookie != null) connection.setRequestProperty("Cookie", cookie)
    if (body != null) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    return try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (status !in 200..299) throw IllegalStateException("B 站登录服务暂不可用（HTTP $status）")
        val cookies = if (includeCookies) {
            connection.headerFields.entries
                .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value.orEmpty() }
                .map { it.substringBefore(';').trim() }
                .filter { it.contains('=') }
                .distinctBy { it.substringBefore('=') }
                .joinToString("; ")
        } else {
            ""
        }
        LoginResponse(JSONObject(text), cookies)
    } finally {
        connection.disconnect()
    }
}

private val loginCookieNames = setOf("DedeUserID", "DedeUserID__ckMd5", "SESSDATA", "bili_jct", "sid")

internal fun cookieFromCallbackUrl(callbackUrl: String): String {
    if (callbackUrl.isBlank()) return ""
    val query = runCatching { URL(callbackUrl).query.orEmpty() }.getOrDefault("")
    return query.split('&').asSequence()
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = URLDecoder.decode(pair.substring(0, separator), Charsets.UTF_8.name())
            if (name !in loginCookieNames) return@mapNotNull null
            val value = URLDecoder.decode(pair.substring(separator + 1), Charsets.UTF_8.name())
            "$name=$value"
        }
        .joinToString("; ")
}

internal fun mergeCookies(vararg cookieSources: String): String {
    val values = linkedMapOf<String, String>()
    cookieSources.flatMap { it.split(';') }
        .map { it.trim() }
        .filter { it.contains('=') }
        .forEach { value -> values[value.substringBefore('=').trim()] = value.substringAfter('=').trim() }
    return values.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

internal fun hasLoginCookies(cookie: String): Boolean {
    val names = cookie.split(';').mapNotNull { it.trim().substringBefore('=').takeIf(String::isNotBlank) }.toSet()
    return "SESSDATA" in names && "bili_jct" in names
}

/** 用登录 Cookie 拉取导航栏接口，确认会话有效并组装本地账号资料。 */
internal fun fetchBiliSession(cookie: String): BiliSession {
    require(hasLoginCookies(cookie)) { "登录 Cookie 不完整" }
    val nav = biliLoginRequest("https://api.bilibili.com/x/web-interface/nav", cookie = cookie).json
    val account = nav.optJSONObject("data")
    if (nav.optInt("code", -1) != 0 || account?.optBoolean("isLogin", false) != true) {
        throw IllegalStateException("登录成功，但无法读取账号信息")
    }
    val levelInfo = account.optJSONObject("level_info")
    return BiliSession(
        username = account.optString("uname", "B 站用户"),
        avatarUrl = secureImageUrl(account.optString("face")),
        cookie = cookie,
        level = levelInfo?.optInt("current_level") ?: 0,
        coins = account.optDouble("money", 0.0),
        currentExp = levelInfo?.optInt("current_exp") ?: 0,
        nextExp = levelInfo?.optInt("next_exp") ?: 0,
    )
}

internal fun secureImageUrl(url: String): String = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url

// ---------------------------------------------------------------------------
// 账号密码 / 短信验证码登录
//
// 两步：先申请 captcha（token + 极验 gt/challenge），在 WebView 里完成极验验证，
// 再用 validate/seccode 调登录接口。密码需先用接口公钥做 RSA 加密。
// ---------------------------------------------------------------------------

internal fun requestLoginCaptcha(): LoginCaptcha {
    val root = biliLoginRequest("https://passport.bilibili.com/x/passport-login/captcha?source=main_web").json
    if (root.optInt("code", -1) != 0) {
        throw IllegalStateException(root.optString("message").ifBlank { "获取人机验证失败" })
    }
    val data = root.optJSONObject("data") ?: error("人机验证响应无效")
    val geetest = data.optJSONObject("geetest") ?: error("人机验证响应无效")
    val captcha = LoginCaptcha(
        token = data.optString("token"),
        gt = geetest.optString("gt"),
        challenge = geetest.optString("challenge"),
    )
    if (captcha.token.isBlank() || captcha.gt.isBlank() || captcha.challenge.isBlank()) {
        error("人机验证响应无效")
    }
    return captcha
}

internal fun loginWithPassword(
    username: String,
    password: String,
    captcha: LoginCaptcha,
    proof: GeetestProof,
): String {
    val keyData = biliLoginRequest("https://passport.bilibili.com/x/passport-login/web/key").json.optJSONObject("data")
        ?: throw IllegalStateException("无法获取登录公钥，请稍后重试")
    val encrypted = try {
        rsaEncryptPassword(keyData.optString("hash") + password, keyData.optString("key"))
    } catch (error: Exception) {
        throw IllegalStateException("密码加密失败，请重试")
    }
    val response = biliLoginPost(
        "https://passport.bilibili.com/x/passport-login/web/login",
        mapOf(
            "username" to username,
            "password" to encrypted,
            "keep" to "0",
            "token" to captcha.token,
            "challenge" to captcha.challenge,
            "validate" to proof.validate,
            "seccode" to proof.seccode,
            "source" to "main_web",
            "go_url" to "https://www.bilibili.com",
        ),
    )
    return loginCookieOf(response)
}

internal fun sendLoginSmsCode(tel: String, captcha: LoginCaptcha, proof: GeetestProof): String {
    val response = biliLoginPost(
        "https://passport.bilibili.com/x/passport-login/web/sms/send",
        mapOf(
            "cid" to "86",
            "tel" to tel,
            "source" to "main_web",
            "token" to captcha.token,
            "challenge" to captcha.challenge,
            "validate" to proof.validate,
            "seccode" to proof.seccode,
        ),
    )
    val root = response.json
    val code = root.optInt("code", -1)
    if (code != 0) throw loginFailure(code, root.optString("message"))
    val captchaKey = root.optJSONObject("data")?.optString("captcha_key").orEmpty()
    if (captchaKey.isBlank()) throw IllegalStateException("短信发送失败，请稍后重试")
    return captchaKey
}

internal fun loginWithSmsCode(tel: String, code: String, captchaKey: String): String {
    val response = biliLoginPost(
        "https://passport.bilibili.com/x/passport-login/web/login/sms",
        mapOf(
            "cid" to "86",
            "tel" to tel,
            "code" to code,
            "source" to "main_web",
            "captcha_key" to captchaKey,
            "keep" to "true",
            "go_url" to "https://www.bilibili.com",
        ),
    )
    return loginCookieOf(response)
}

private fun loginCookieOf(response: LoginResponse): String {
    val root = response.json
    val code = root.optInt("code", -1)
    if (code != 0) throw loginFailure(code, root.optString("message"))
    val data = root.optJSONObject("data")
    val cookie = mergeCookies(response.cookies, cookieFromCallbackUrl(data?.optString("url").orEmpty()))
    if (!hasLoginCookies(cookie)) {
        // 风控命中时 code 仍为 0，但 status=2 且不下发会话 Cookie。
        val riskHit = data?.optInt("status", 0) == 2 || data?.optString("message").orEmpty().contains("风险")
        if (riskHit) throw IllegalStateException("本次登录环境存在风险，请改用扫码或网页登录")
        throw IllegalStateException("登录成功，但未获取到会话凭证，请重试或改用扫码登录")
    }
    return cookie
}

private fun loginFailure(code: Int, message: String): IllegalStateException {
    val text = when (code) {
        -105 -> "人机验证未通过，请重新验证"
        -629 -> "账号或密码错误"
        -662 -> "提交超时，请重新登录"
        -2100 -> "本次登录环境存在风险，请改用扫码或网页登录"
        2400 -> "登录密钥已失效，请重试"
        2406 -> "人机验证服务异常，请稍后重试"
        86000 -> "密码校验失败，请重试"
        1002 -> "手机号格式不正确"
        1003 -> "验证码已发送，请稍后重试"
        1006 -> "短信验证码不正确"
        1007 -> "短信验证码已过期，请重新获取"
        1025 -> "该手机号无法使用短信登录"
        86203 -> "该手机号今日发送次数已达上限"
        else -> message.ifBlank { "登录失败（$code）" }
    }
    return IllegalStateException(text)
}

/** B 站要求先用接口公钥对「盐 + 明文密码」做 RSA(PKCS#1) 加密，再做 Base64。 */
private fun rsaEncryptPassword(plain: String, pem: String): String {
    val key = pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\\s".toRegex(), "")
    val spec = X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT))
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(spec)
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
}
