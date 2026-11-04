package io.github.bilibox.feature.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.fetchBiliSession
import io.github.bilibox.data.auth.hasLoginCookies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val LOGIN_PAGE = "https://passport.bilibili.com/login?gourl=https%3A%2F%2Fwww.bilibili.com%2F"

// 默认的 WebView UA 带 "wv" 标记，容易被风控拦截，这里伪装成普通移动版 Chrome。
private const val WEB_LOGIN_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * 账号密码 / 短信验证码登录。
 *
 * 哔哩哔哩的密码与短信登录接口都强制要求极验（geetest）验证码，纯原生表单无法通过，
 * 因此这里内嵌官方登录页完成验证，登录成功后再从 WebView 的 Cookie 中取出会话凭证。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BiliWebLoginDialog(
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onSuccess: (BiliSession) -> Unit,
) {
    val context = LocalContext.current
    val background = if (darkMode) Color(0xFF141519) else Color(0xFFF4F5F7)
    val panelColor = if (darkMode) Color(0xFF22252B) else Color.White
    val ink = if (darkMode) Color(0xFFE7E9ED) else Ink
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted

    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTicket by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }

    val dismissEvent = rememberUpdatedState(onDismiss)
    val successEvent = rememberUpdatedState(onSuccess)

    val webView = remember {
        val view = WebView(context)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = WEB_LOGIN_UA
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // 打开登录页；点击"重试"会重新加载一次。
    LaunchedEffect(reloadTicket) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
                if (newProgress >= 100) loading = false
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                error = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                progress = 100
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceError?) {
                if (request?.isForMainFrame != true) return
                loading = false
                error = "登录页加载失败，请检查网络后重试"
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "http" || url.scheme == "https") return false
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
                return true
            }
        }
        webView.loadUrl(LOGIN_PAGE)
    }

    // 登录成功后会写入 SESSDATA / bili_jct，轮询 Cookie 并用 nav 接口校验会话。
    LaunchedEffect(webView) {
        var failures = 0
        var lastFingerprint = ""
        while (isActive && !completed) {
            delay(900)
            val cookie = readWebLoginCookie() ?: continue
            // 只比较登录凭证，避免 buvid 等 Cookie 变化导致重复校验。
            val fingerprint = loginCookieFingerprint(cookie)
            if (fingerprint != lastFingerprint) {
                lastFingerprint = fingerprint
                failures = 0
            }
            if (failures >= 2) continue
            verifying = true
            val session = try {
                withContext(Dispatchers.IO) { fetchBiliSession(cookie) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                null
            } finally {
                verifying = false
            }
            if (session != null) {
                completed = true
                error = null
                successEvent.value(session)
                return@LaunchedEffect
            }
            failures++
            if (failures >= 2) error = "已检测到登录，但账号信息校验失败，可重试或改用扫码登录"
        }
    }

    Dialog(
        onDismissRequest = {
            if (webView.canGoBack()) webView.goBack() else dismissEvent.value()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().background(panelColor)
                        .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("密码 / 短信登录", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("在哔哩哔哩官方页面完成验证，成功后自动返回", color = muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { dismissEvent.value() }) { Text("关闭", color = Pink, fontSize = 14.sp) }
                }
                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Pink,
                        trackColor = Color.Transparent,
                    )
                }
                error?.let { message ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (darkMode) Color(0xFF33272B) else Color(0xFFFFF0F3))
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            message,
                            color = if (darkMode) Color(0xFFF3B3C2) else Color(0xFFB54F67),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { error = null; reloadTicket++ }) { Text("重试", color = Pink, fontSize = 13.sp) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    if (verifying) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                Modifier.background(if (darkMode) Color(0xFF2A2D33) else Color.White, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 26.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
                                Spacer(Modifier.height(12.dp))
                                Text("正在确认登录状态…", color = ink, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text(
                    "登录信息仅用于本机获取哔哩哔哩账号权限，会话凭证加密保存在设备中。",
                    color = muted,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().background(panelColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 从 WebView 的 Cookie 存储中读取哔哩哔哩会话；未登录或会话不全时返回 null。 */
private fun readWebLoginCookie(): String? {
    val cookie = runCatching { CookieManager.getInstance().getCookie("https://www.bilibili.com/") }
        .getOrNull() ?: return null
    return cookie.takeIf { it.isNotBlank() && hasLoginCookies(it) }
}

/** 仅保留登录相关的 Cookie 值，用于判断登录态是否发生变化。 */
private fun loginCookieFingerprint(cookie: String): String = listOf("SESSDATA", "bili_jct", "DedeUserID")
    .joinToString("|") { name -> cookie.substringAfter("$name=", "").substringBefore(';').trim() }
