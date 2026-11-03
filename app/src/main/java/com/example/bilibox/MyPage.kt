package com.example.bilibox

import android.content.Context
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val MyInk = Color(0xFF24313D)
private val MyPink = Color(0xFFEF7196)
private val MyMuted = Color(0xFF82909D)

internal data class BiliSession(
    val username: String,
    val avatarUrl: String,
    val cookie: String,
    val level: Int = 0,
    val coins: Double = 0.0,
    val currentExp: Int = 0,
    val nextExp: Int = 0,
)

private sealed interface LoginUiState {
    data object SignedOut : LoginUiState
    data object CreatingQr : LoginUiState
    data class WaitingForScan(val key: String, val bitmap: ImageBitmap, val status: String, val cookie: String) : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

internal object BiliSessionStore {
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
        BiliSession(
            username = json.getString("username"),
            avatarUrl = json.optString("avatar"),
            cookie = json.getString("cookie"),
            level = json.optInt("level"),
            coins = json.optDouble("coins"),
            currentExp = json.optInt("current_exp"),
            nextExp = json.optInt("next_exp"),
        )
    }.getOrNull()

    fun save(context: Context, session: BiliSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(JSONObject().put("username", session.username)
            .put("avatar", session.avatarUrl).put("cookie", session.cookie)
            .put("level", session.level).put("coins", session.coins)
            .put("current_exp", session.currentExp).put("next_exp", session.nextExp)
            .toString().toByteArray(Charsets.UTF_8))
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
internal fun MyPage(savedCount: Int, session: BiliSession?, darkMode: Boolean = false, onSessionChange: (BiliSession?) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LoginUiState>(LoginUiState.SignedOut) }
    var creating by rememberSaveable { mutableIntStateOf(0) }
    var webLoginOpen by remember { mutableStateOf(false) }
    var nativeLoginOpen by remember { mutableStateOf(false) }

    fun applySession(loggedIn: BiliSession) {
        BiliSessionStore.save(context, loggedIn)
        onSessionChange(loggedIn)
        state = LoginUiState.SignedOut
        webLoginOpen = false
        nativeLoginOpen = false
    }

    LaunchedEffect(creating) {
        if (creating == 0) return@LaunchedEffect
        state = LoginUiState.CreatingQr
        state = try {
            val ticket = withContext(Dispatchers.IO) { createLoginQr() }
            LoginUiState.WaitingForScan(ticket.key, createQrImage(ticket.url), "请使用哔哩哔哩 App 扫码", ticket.cookie)
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
                when (val result = withContext(Dispatchers.IO) { pollLoginQr(waiting.key, waiting.cookie) }) {
                    QrPollResult.Pending -> Unit
                    QrPollResult.Scanned -> state = waiting.copy(status = "已扫码，请在哔哩哔哩 App 确认登录")
                    QrPollResult.Expired -> state = LoginUiState.Failed("二维码已过期，请重新获取")
                    is QrPollResult.Success -> {
                        BiliSessionStore.save(context, result.session)
                        onSessionChange(result.session)
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

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        if (session == null) {
            SignedOutPanel(
                state = state,
                darkMode = darkMode,
                onLogin = { creating++ },
                onRetry = { creating++ },
                onCredentialLogin = { nativeLoginOpen = true },
            )
        } else {
            SignedInPanel(session, darkMode)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    BiliSessionStore.clear(context)
                    onSessionChange(null)
                    state = LoginUiState.SignedOut
                }) { Text("退出登录", color = MyPink) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("账号与安全", color = if (darkMode) Color(0xFF9BA3AE) else MyMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        InfoRow("登录方式", "扫码 / 密码 / 短信", darkMode)
        InfoRow("会话保存", "仅加密保存在本机", darkMode)
        InfoRow("我的收藏", "$savedCount 个视频", darkMode)
    }

    if (nativeLoginOpen) {
        BiliNativeLoginDialog(
            darkMode = darkMode,
            onDismiss = { nativeLoginOpen = false },
            onSuccess = { applySession(it) },
            onUseWebLogin = {
                nativeLoginOpen = false
                webLoginOpen = true
            },
        )
    }

    if (webLoginOpen) {
        BiliWebLoginDialog(
            darkMode = darkMode,
            onDismiss = { webLoginOpen = false },
            onSuccess = { applySession(it) },
        )
    }
}

@Composable
private fun SignedOutPanel(
    state: LoginUiState,
    darkMode: Boolean,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onCredentialLogin: () -> Unit,
) {
    val panel = if (darkMode) Color(0xFF24272D) else Color.White.copy(alpha = .86f)
    val ink = if (darkMode) Color(0xFFE7E9ED) else MyInk
    val muted = if (darkMode) Color(0xFFA0A7B0) else MyMuted
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(panel).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(MyPink.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
            Text("B", color = MyPink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text("登录哔哩哔哩账号", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("登录后可用账号权限获取更高画质视频", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        when (state) {
            LoginUiState.SignedOut -> Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) { Text("扫码登录") }
            LoginUiState.CreatingQr -> CircularProgressIndicator(color = MyPink, strokeWidth = 2.dp)
            is LoginUiState.WaitingForScan -> {
                Image(state.bitmap, contentDescription = "哔哩哔哩登录二维码", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(10.dp))
                Text(state.status, color = ink, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("刷新二维码", color = MyPink) }
            }
            is LoginUiState.Failed -> {
                Text(state.message, color = Color(0xFFB54F67), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) { Text("重新获取二维码") }
            }
        }
        if (state is LoginUiState.SignedOut || state is LoginUiState.Failed) {
            TextButton(onClick = onCredentialLogin) { Text("密码 / 短信验证码登录", color = MyPink, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Text("二维码由哔哩哔哩提供；密码与短信登录在应用内完成，仅人机验证使用官方组件。会话凭证使用 Android Keystore 加密存储。", color = muted, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SignedInPanel(session: BiliSession, darkMode: Boolean) {
    val maxExp = session.nextExp.coerceAtLeast(0)
    val progress = if (maxExp > 0) (session.currentExp.toFloat() / maxExp).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (darkMode) Color(0xFF24272D) else Color.White.copy(alpha = .82f))
            .border(1.dp, if (darkMode) Color(0xFF343941) else Color.White.copy(alpha = .9f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(session)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.username, color = if (darkMode) Color(0xFFE7E9ED) else Color(0xFF242124), fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text("LV${session.level}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF47742)).padding(horizontal = 7.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("硬币 ${formatCoins(session.coins)}", color = if (darkMode) Color(0xFFB1B7C0) else Color(0xFF68636A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(14.dp))
                    Text("经验 ${session.currentExp}/${session.nextExp}", color = if (darkMode) Color(0xFFB1B7C0) else Color(0xFF68636A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(if (darkMode) Color(0xFF4A4E56) else Color(0xFFDEDADD))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MyPink, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(session: BiliSession) {
    var avatar by remember(session.avatarUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(session.avatarUrl) {
        avatar = if (session.avatarUrl.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(session.avatarUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(Modifier.size(72.dp).clip(CircleShape).background(MyPink.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        if (avatar != null) {
            Image(avatar!!, contentDescription = "头像", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(session.username.take(1), color = MyPink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatCoins(coins: Double): String = if (coins % 1.0 == 0.0) coins.toLong().toString() else String.format(Locale.US, "%.1f", coins)

@Composable
private fun InfoRow(title: String, value: String, darkMode: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (darkMode) Color(0xFFE0E3E8) else MyInk, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = if (darkMode) Color(0xFF9BA3AE) else MyMuted, fontSize = 12.sp)
    }
    HorizontalDivider(color = if (darkMode) Color(0xFF30343A) else Color(0xFFE7EBEF))
}

private data class QrTicket(val key: String, val url: String, val cookie: String)

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
    return QrTicket(key, url, response.cookies)
}

private fun pollLoginQr(key: String, initialCookie: String): QrPollResult {
    val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())
    val response = biliLoginRequest(
        "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$encodedKey",
        cookie = initialCookie.ifBlank { null }
    )
    val root = response.json
    val code = root.optInt("code", -1)
    return when (code) {
        86101 -> QrPollResult.Pending
        86090 -> QrPollResult.Scanned
        86038 -> QrPollResult.Expired
        0 -> {
            val data = root.optJSONObject("data")
            val cookie = mergeCookies(initialCookie, response.cookies, cookieFromCallbackUrl(data?.optString("url").orEmpty()))
            // Some B 站 edge responses use code=0 before returning the auth cookies.
            // Keep polling in that case instead of treating the transient response as a failed login.
            if (!hasLoginCookies(cookie)) return QrPollResult.Pending
            QrPollResult.Success(fetchBiliSession(cookie))
        }
        else -> throw IllegalStateException(root.optString("message").ifBlank { "二维码登录失败（$code）" })
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
