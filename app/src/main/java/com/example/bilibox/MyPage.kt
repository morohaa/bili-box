package com.example.bilibox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val MyInk = Color(0xFF24313D)
private val MyPink = Color(0xFFEF7196)
private val MyMuted = Color(0xFF82909D)

private data class BiliSession(val username: String, val avatarUrl: String, val cookie: String)

private sealed interface LoginUiState {
    data object SignedOut : LoginUiState
    data object CreatingQr : LoginUiState
    data class WaitingForScan(val key: String, val bitmap: ImageBitmap, val status: String) : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

private object BiliSessionStore {
    private const val alias = "bilibox_bili_session_key"
    private const val prefsName = "bili_session_secure"
    private const val dataKey = "encrypted_session"

    fun load(context: Context): BiliSession? = runCatching {
        val encoded = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString(dataKey, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        BiliSession(json.getString("username"), json.optString("avatar"), json.getString("cookie"))
    }.getOrNull()

    fun save(context: Context, session: BiliSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(JSONObject().put("username", session.username)
            .put("avatar", session.avatarUrl).put("cookie", session.cookie).toString().toByteArray(Charsets.UTF_8))
        val stored = cipher.iv + encrypted
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString(dataKey, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }
}

@Composable
internal fun MyPage(savedCount: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(BiliSessionStore.load(context)) }
    var state by remember { mutableStateOf<LoginUiState>(LoginUiState.SignedOut) }
    var creating by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(creating) {
        if (creating == 0) return@LaunchedEffect
        state = LoginUiState.CreatingQr
        state = try {
            val (key, qrUrl) = withContext(Dispatchers.IO) { createLoginQr() }
            LoginUiState.WaitingForScan(key, createQrImage(qrUrl), "请使用哔哩哔哩 App 扫码")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LoginUiState.Failed(error.message ?: "二维码生成失败")
        }
    }

    LaunchedEffect(state) {
        val waiting = state as? LoginUiState.WaitingForScan ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            try {
                when (val result = withContext(Dispatchers.IO) { pollLoginQr(waiting.key) }) {
                    QrPollResult.Pending -> Unit
                    QrPollResult.Scanned -> state = waiting.copy(status = "已扫码，请在哔哩哔哩 App 确认登录")
                    QrPollResult.Expired -> state = LoginUiState.Failed("二维码已过期，请重新获取")
                    is QrPollResult.Success -> {
                        BiliSessionStore.save(context, result.session)
                        session = result.session
                        state = LoginUiState.SignedOut
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = LoginUiState.Failed(error.message ?: "登录状态检查失败")
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text("我的", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MyInk)
        Spacer(Modifier.height(8.dp))
        Text("每一次好奇，都值得被记录", color = MyMuted, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        if (session == null) {
            SignedOutPanel(
                state = state,
                onLogin = { creating++ },
                onRetry = { creating++ }
            )
        } else {
            SignedInPanel(session!!, savedCount) {
                BiliSessionStore.clear(context)
                session = null
                state = LoginUiState.SignedOut
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("账号与安全", color = MyMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        InfoRow("登录方式", "哔哩哔哩扫码授权")
        InfoRow("会话保存", "仅加密保存在本机")
        InfoRow("我的收藏", "$savedCount 个视频")
    }
}

@Composable
private fun SignedOutPanel(state: LoginUiState, onLogin: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .86f)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(MyPink.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
            Text("B", color = MyPink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text("登录哔哩哔哩账号", color = MyInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("扫码登录后，可在后续功能中使用账号能力", color = MyMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        when (state) {
            LoginUiState.SignedOut -> Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) { Text("扫码登录") }
            LoginUiState.CreatingQr -> CircularProgressIndicator(color = MyPink, strokeWidth = 2.dp)
            is LoginUiState.WaitingForScan -> {
                Image(state.bitmap, contentDescription = "哔哩哔哩登录二维码", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(10.dp))
                Text(state.status, color = MyInk, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("刷新二维码", color = MyPink) }
            }
            is LoginUiState.Failed -> {
                Text(state.message, color = Color(0xFFB54F67), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) { Text("重新获取二维码") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("登录二维码由哔哩哔哩提供。授权后会话凭证使用 Android Keystore 加密存储。", color = MyMuted, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SignedInPanel(session: BiliSession, savedCount: Int, onLogout: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .86f)).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(62.dp).background(MyPink.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                Text(session.username.take(1), color = MyPink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(session.username, color = MyInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("哔哩哔哩账号已登录", color = MyMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("$savedCount", color = MyInk, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("我的收藏", color = MyMuted, fontSize = 11.sp) }
            TextButton(onClick = onLogout) { Text("退出登录", color = MyPink) }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MyInk, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = MyMuted, fontSize = 12.sp)
    }
    HorizontalDivider(color = Color(0xFFE7EBEF))
}

private data class QrTicket(val key: String, val url: String)

private sealed interface QrPollResult {
    data object Pending : QrPollResult
    data object Scanned : QrPollResult
    data object Expired : QrPollResult
    data class Success(val session: BiliSession) : QrPollResult
}

private fun createLoginQr(): QrTicket {
    val response = biliLoginRequest("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    if (response.json.optInt("code", -1) != 0) throw IllegalStateException(response.json.optString("message", "无法创建登录二维码"))
    val data = response.json.optJSONObject("data") ?: error("二维码响应无效")
    val key = data.optString("qrcode_key")
    val url = data.optString("url")
    if (key.isBlank() || url.isBlank()) error("B 站没有返回有效二维码")
    return QrTicket(key, url)
}

private fun pollLoginQr(key: String): QrPollResult {
    val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())
    val response = biliLoginRequest("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$encodedKey")
    val root = response.json
    val code = root.optInt("code", -1)
    return when (code) {
        86101 -> QrPollResult.Pending
        86090 -> QrPollResult.Scanned
        86038 -> QrPollResult.Expired
        0 -> {
            val cookie = response.cookies
            if (cookie.isBlank()) throw IllegalStateException("登录已确认，但 B 站未返回会话凭证")
            val nav = biliLoginRequest("https://api.bilibili.com/x/web-interface/nav", cookie = cookie).json
            val account = nav.optJSONObject("data")
            if (nav.optInt("code", -1) != 0 || account?.optBoolean("isLogin", false) != true) {
                throw IllegalStateException("登录成功，但无法读取账号信息")
            }
            val user = account
            QrPollResult.Success(BiliSession(user.optString("uname", "B 站用户"), secureImageUrl(user.optString("face")), cookie))
        }
        else -> throw IllegalStateException(root.optString("message").ifBlank { "二维码登录失败（$code）" })
    }
}

private data class LoginResponse(val json: JSONObject, val cookies: String)

private fun biliLoginRequest(endpoint: String, cookie: String? = null, includeCookies: Boolean = true): LoginResponse {
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
    connection.setRequestProperty("Referer", "https://www.bilibili.com/")
    if (cookie != null) connection.setRequestProperty("Cookie", cookie)
    return try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (status !in 200..299) throw IllegalStateException("B 站登录服务暂不可用（HTTP $status）")
        val cookies = if (includeCookies) connection.headerFields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
            .map { it.substringBefore(';').trim() }
            .filter { it.contains('=') }
            .distinctBy { it.substringBefore('=') }
            .joinToString("; ") else ""
        LoginResponse(JSONObject(body), cookies)
    } finally {
        connection.disconnect()
    }
}

private fun createQrImage(content: String): ImageBitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 720, 720)
    val bitmap = android.graphics.Bitmap.createBitmap(720, 720, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until 720) for (x in 0 until 720) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap.asImageBitmap()
}

private fun secureImageUrl(url: String): String = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
