package com.example.bilibox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import org.json.JSONObject

private const val CAPTCHA_BRIDGE = "BiliBoxCaptcha"
private const val CAPTCHA_BASE_URL = "https://passport.bilibili.com/login"
private const val CAPTCHA_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * 只负责极验（geetest）人机验证：加载官方 gt.js，验证通过后把 validate/seccode 交回调用方，
 * 后续的密码/短信登录请求仍由原生代码完成。
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun GeetestCaptchaDialog(
    captcha: LoginCaptcha,
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onSolved: (GeetestProof) -> Unit,
) {
    val context = LocalContext.current
    val background = if (darkMode) Color(0xFF141519) else Color(0xFFF4F5F7)
    val panelColor = if (darkMode) Color(0xFF22252B) else Color.White
    val ink = if (darkMode) Color(0xFFE7E9ED) else Ink
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted

    var error by remember { mutableStateOf<String?>(null) }
    var reloadTicket by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val dismissEvent = rememberUpdatedState(onDismiss)
    val solvedEvent = rememberUpdatedState(onSolved)

    val webView = remember {
        val view = WebView(context)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = CAPTCHA_UA
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
            webView.removeJavascriptInterface(CAPTCHA_BRIDGE)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    LaunchedEffect(reloadTicket) {
        error = null
        loading = true
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun postMessage(payload: String) {
                Handler(Looper.getMainLooper()).post {
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
                    when (json.optString("type")) {
                        "ready" -> loading = false
                        "loaded" -> loading = false
                        "success" -> {
                            val validate = json.optString("geetest_validate")
                            val seccode = json.optString("geetest_seccode")
                            if (validate.isNotBlank() && seccode.isNotBlank()) {
                                solvedEvent.value(GeetestProof(validate, seccode))
                            } else {
                                error = "验证结果无效，请重试"
                            }
                        }
                        "error" -> error = json.optString("message").ifBlank { "人机验证加载失败，请重试" }
                    }
                }
            }
        }, CAPTCHA_BRIDGE)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceError?) {
                if (request?.isForMainFrame != true) return
                loading = false
                error = "验证码加载失败，请检查网络后重试"
            }
        }
        webView.loadDataWithBaseURL(CAPTCHA_BASE_URL, geetestHtml(captcha), "text/html", "UTF-8", CAPTCHA_BASE_URL)
    }

    Dialog(
        onDismissRequest = { dismissEvent.value() },
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
                        Text("人机验证", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("完成极验验证后自动继续登录", color = muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { dismissEvent.value() }) { Text("关闭", color = Pink, fontSize = 14.sp) }
                }
                if (error != null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (darkMode) Color(0xFF33272B) else Color(0xFFFFF0F3))
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            error.orEmpty(),
                            color = if (darkMode) Color(0xFFF3B3C2) else Color(0xFFB54F67),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { error = null; reloadTicket++ }) { Text("重试", color = Pink, fontSize = 13.sp) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
                        }
                    }
                }
                Text(
                    "验证由极验（geetest）提供，仅用于本次登录。",
                    color = muted,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().background(panelColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun geetestHtml(captcha: LoginCaptcha): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html, body { margin: 0; padding: 0; background: transparent; }
  #fallback { display: none; margin: 24px auto 0; padding: 10px 18px; border: 0; border-radius: 10px;
              background: #ef7196; color: #fff; font-size: 15px; }
</style>
</head>
<body>
<div id="captcha"></div>
<button id="fallback">点击重新验证</button>
<script src="https://static.geetest.com/static/js/gt.0.4.9.js"></script>
<script>
(function () {
  var fallback = document.getElementById('fallback');
  function post(payload) {
    try { window.$CAPTCHA_BRIDGE.postMessage(JSON.stringify(payload)); } catch (e) {}
  }
  function fail(message) { post({ type: 'error', message: String(message || '') }); }

  function boot() {
    if (typeof initGeetest !== 'function') { fail('验证码组件加载失败'); return; }
    try {
      initGeetest({
        gt: '${captcha.gt}',
        challenge: '${captcha.challenge}',
        offline: false,
        new_captcha: true,
        product: 'popup',
        width: '100%',
        lang: 'zh-cn',
        https: true
      }, function (captchaObj) {
        window.__captcha = captchaObj;
        captchaObj.appendTo('#captcha');
        fallback.onclick = function () { captchaObj.verify(); };
        captchaObj.onReady(function () {
          post({ type: 'ready' });
          captchaObj.verify();
          // 兜底：验证浮层被关掉或没自动弹出时，给一个重新验证的入口。
          setTimeout(function () { fallback.style.display = 'block'; }, 8000);
        });
        captchaObj.onSuccess(function () {
          var result = captchaObj.getValidate() || {};
          post({
            type: 'success',
            geetest_validate: result.geetest_validate || '',
            geetest_seccode: result.geetest_seccode || ''
          });
        });
        captchaObj.onError(function (e) { fail('验证失败：' + e); });
        captchaObj.onClose(function () {
          fallback.style.display = 'block';
          post({ type: 'closed' });
        });
      });
    } catch (e) { fail(e); }
  }

  if (document.readyState === 'complete') boot(); else window.addEventListener('load', boot);
})();
</script>
</body>
</html>
""".trimIndent()
