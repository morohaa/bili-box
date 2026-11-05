package io.github.bilibox.feature.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.BiliSessionStore
import io.github.bilibox.data.auth.biliLoginRequest
import io.github.bilibox.data.auth.cookieFromCallbackUrl
import io.github.bilibox.data.auth.fetchBiliSession
import io.github.bilibox.data.auth.hasLoginCookies
import io.github.bilibox.data.auth.mergeCookies
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.feature.auth.BiliNativeLoginDialog
import io.github.bilibox.feature.auth.BiliWebLoginDialog
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- palette ---
// 参照设计稿：浅色底 #f4f6fb、深色底 #121212；图标蓝 #00AEEC、图标粉 #FF85A2。
internal val MyBgLight = Color(0xFFF4F6FA)
internal val MyBgDark = Color(0xFF17191D)
internal val MyInkLight = Color(0xFF0F172A)
internal val MyInkDark = Color(0xFFFFFFFF)
internal val MySubLight = Color(0xFF64748B)
internal val MySubDark = Color(0xFF9BA3AE)
internal val MyTrackLight = Color(0xFFDBE3EE)
internal val MyTrackDark = Color(0xFF333A42)
internal val MyLabelLight = Color(0xFF334155)
internal val MyLabelDark = Color(0xFFF1F5F9)
internal val MyCardLight = Color(0xFFFFFFFF)
internal val MyCardDark = Color(0xFF23262C)
internal val MyBlue = Color(0xFF00AEEC)
internal val MyRose = Color(0xFFFF85A2)
internal val MyPink = Color(0xFFEF7196)
internal val MyOrange = Color(0xFFFF6600)
internal val MyBadgeRed = Color(0xFFFA5A57)

// 视口低于这个高度（手机横屏、分屏）时，固定头部会把内容挤没，改用整页滚动。
private val CompactHeightThreshold = 500.dp

// 内容滚到头部下沿时的渐隐高度。
private val HeaderFadeHeight = 24.dp

// 内容底部留白。
private val ContentBottomGap = 12.dp

// 已登录时内容与头图之间的留白：未登录滚到底后宫格要对齐到同一位置。
private val ContentTopGap = 12.dp

// 悬浮导航栏高度 66dp + 16dp 间距：内容区预留出来，最后一项不会被挡住。
private val NavigationBarClearance = 82.dp

@Composable
internal fun ProfileScreen(
    session: BiliSession?,
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onOpenLibrary: (LocalVideoQuickFilter?) -> Unit = {},
    onSessionChange: (BiliSession?) -> Unit,
    scrollState: ScrollState,
    onBannerScrollLimitChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LoginUiState>(LoginUiState.SignedOut) }
    var creating by rememberSaveable { mutableIntStateOf(0) }
    var webLoginOpen by remember { mutableStateOf(false) }
    var nativeLoginOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<MySheet?>(null) }

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

    val background = if (darkMode) MyBgDark else MyBgLight
    val ink = if (darkMode) MyInkDark else MyInkLight
    val sub = if (darkMode) MySubDark else MySubLight
    val avatar = rememberRemoteBitmap(session?.avatarUrl)
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 头图固定在页面顶部，只让下方内容滚动：背景图始终完整露出，不随滚动被裁掉。
    // 视口太矮（手机横屏、分屏）时固定头部会把内容挤没，那时退化成整页滚动。
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val bannerHeight = profileBannerHeight(maxWidth, maxHeight)
        val compact = maxHeight < CompactHeightThreshold
        // 整页滚动时头图会滚走，滚过状态栏后要把图标换回深色；固定头图时永远不需要。
        val scrollLimit = if (compact) {
            val bannerPx = with(LocalDensity.current) { bannerHeight.roundToPx() }
            val statusBarPx = with(LocalDensity.current) {
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding().roundToPx()
            }
            (bannerPx - statusBarPx).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        LaunchedEffect(scrollLimit) { onBannerScrollLimitChange(scrollLimit) }

        // 未登录时补一段尾部空白：登录卡正好能完全滚出，滚到底时宫格落在登录后的位置。
        var viewportPx by remember { mutableIntStateOf(0) }
        var gridHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val trailingSpace = if (!compact && session == null && viewportPx > 0) {
            with(density) {
                (viewportPx - gridHeightPx - ContentBottomGap.roundToPx() - ContentTopGap.roundToPx())
                    .coerceAtLeast(0).toDp()
            }
        } else {
            0.dp
        }

        val content: @Composable () -> Unit = {
            if (session == null) {
                LoginCard(
                    state = state,
                    darkMode = darkMode,
                    onLogin = { creating++ },
                    onRetry = { creating++ },
                    onCredentialLogin = { nativeLoginOpen = true },
                )
            }

            Spacer(Modifier.height(if (session == null) 18.dp else ContentTopGap))

            Box(Modifier.onSizeChanged { gridHeightPx = it.height }) {
                ActionGrid(
                    darkMode = darkMode,
                    background = background,
                    onOfflineCache = { onOpenLibrary(LocalVideoQuickFilter.BILI_CACHE) },
                    onHistory = { onOpenLibrary(LocalVideoQuickFilter.RECENT) },
                    onLocalVideos = { onOpenLibrary(null) },
                    onLoginMethods = { sheet = MySheet.LoginMethods },
                    onSession = { sheet = MySheet.Session },
                    onTheme = onToggleDarkMode,
                    onAbout = { sheet = MySheet.About },
                    onClearCache = {
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { clearAppCache(context) }
                            toast(if (freed > 0) "已清理 ${Formatter.formatFileSize(context, freed)} 缓存" else "缓存已经是空的")
                        }
                    },
                    onHelp = { sheet = MySheet.Help },
                    onUpdate = { sheet = MySheet.Update },
                    onFavorites = { toast("「我的收藏」需要 B 站账号数据接口，当前版本暂未接入") },
                    onWatchLater = { toast("「稍后再看」需要 B 站账号数据接口，当前版本暂未接入") },
                )
            }

            if (session != null) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {
                        BiliSessionStore.clear(context)
                        onSessionChange(null)
                        state = LoginUiState.SignedOut
                    }) {
                        Text("退出登录", color = MyPink, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(ContentBottomGap))
            Spacer(Modifier.height(trailingSpace))
        }

        val header: @Composable () -> Unit = {
            ProfileHeader(session, avatar, darkMode, onToggleDarkMode, bannerHeight)
        }

        if (compact) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                header()
                content()
                // 悬浮导航栏占位，让最后一项能滚到它上面。
                Spacer(Modifier.height(NavigationBarClearance))
                Spacer(Modifier.navigationBarsPadding())
            }
        } else {
            val fadePx = with(density) { HeaderFadeHeight.toPx() }
            Column(Modifier.fillMaxSize()) {
                header()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = NavigationBarClearance)
                            .onSizeChanged { viewportPx = it.height }
                            .verticalScroll(scrollState)
                    ) { content() }
                    // 内容滚到头部下沿时渐隐到背景色：透明度跟着滚动距离走，静止时不压暗首项。
                    Box(
                        Modifier.fillMaxWidth().height(HeaderFadeHeight)
                            .graphicsLayer { alpha = (scrollState.value / fadePx).coerceIn(0f, 1f) }
                            .background(
                                Brush.verticalGradient(
                                    0f to background,
                                    0.6f to background,
                                    1f to background.copy(alpha = 0f),
                                )
                            )
                    )
                }
            }
        }
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

    sheet?.let { current ->
        MyInfoDialog(
            sheet = current,
            session = session,
            darkMode = darkMode,
            version = version,
            onDismiss = { sheet = null },
        )
    }
}

// ---------------------------------------------------------------- header ---

@Composable
private fun rememberRemoteBitmap(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
    return bitmap
}

private fun clearAppCache(context: Context): Long {
    val directories = listOf(context.cacheDir, context.externalCacheDir)
    var freed = 0L
    directories.filterNotNull().forEach { directory ->
        runCatching {
            directory.walkBottomUp().forEach { file ->
                if (file.isFile) {
                    val length = file.length()
                    if (file.delete()) freed += length
                }
            }
        }
    }
    return freed
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
    for (y in 0 until 720) {
        for (x in 0 until 720) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
