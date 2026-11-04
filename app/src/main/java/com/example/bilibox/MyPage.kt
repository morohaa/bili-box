package com.example.bilibox

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.format.Formatter
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ---------------------------------------------------------------- palette ---
// 参照设计稿：浅色底 #f4f6fb、深色底 #121212；图标蓝 #00AEEC、图标粉 #FF85A2。
private val MyBgLight = Color(0xFFF4F6FA)
private val MyBgDark = Color(0xFF17191D)
private val MyInkLight = Color(0xFF0F172A)
private val MyInkDark = Color(0xFFFFFFFF)
private val MySubLight = Color(0xFF64748B)
private val MySubDark = Color(0xFF9BA3AE)
private val MyTrackLight = Color(0xFFDBE3EE)
private val MyTrackDark = Color(0xFF333A42)
private val MyLabelLight = Color(0xFF334155)
private val MyLabelDark = Color(0xFFF1F5F9)
private val MyCardLight = Color(0xFFFFFFFF)
private val MyCardDark = Color(0xFF23262C)
private val MyBlue = Color(0xFF00AEEC)
private val MyRose = Color(0xFFFF85A2)
private val MyPink = Color(0xFFEF7196)
private val MyOrange = Color(0xFFFF6600)
private val MyBadgeRed = Color(0xFFFA5A57)

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

/** 我的页里由宫格打开的说明弹层。 */
private enum class MySheet { LoginMethods, Session, About, Help, Update }

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
internal fun MyPage(
    session: BiliSession?,
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onOpenLibrary: (LocalVideoQuickFilter?) -> Unit = {},
    onSessionChange: (BiliSession?) -> Unit,
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

    Box(Modifier.fillMaxWidth().background(background)) {
        Column(Modifier.fillMaxWidth()) {
            ProfileHeader(session, avatar, darkMode, onToggleDarkMode)

            if (session == null) {
                LoginCard(
                    state = state,
                    darkMode = darkMode,
                    onLogin = { creating++ },
                    onRetry = { creating++ },
                    onCredentialLogin = { nativeLoginOpen = true },
                )
            }

            Spacer(Modifier.height(if (session == null) 18.dp else 12.dp))

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
            Spacer(Modifier.height(18.dp))
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
private fun ProfileHeader(
    session: BiliSession?,
    avatar: ImageBitmap?,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
) {
    val background = if (darkMode) MyBgDark else MyBgLight
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bannerHeight = 200.dp + statusBar

    Box(Modifier.fillMaxWidth().height(bannerHeight + 56.dp)) {
        Banner(avatar, darkMode, Modifier.fillMaxWidth().height(bannerHeight))
        ProfileAvatar(
            session = session,
            avatar = avatar,
            ring = background,
            darkMode = darkMode,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 20.dp, y = bannerHeight - 45.dp),
        )
        ThemeToggle(
            darkMode = darkMode,
            onToggle = onToggleDarkMode,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = bannerHeight + 10.dp),
        )
    }

    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
        Text(
            text = session?.username ?: "未登录",
            color = if (darkMode) MyInkDark else MyInkLight,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (session != null) 18.dp else 10.dp))
        if (session != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(session.level)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (session.nextExp > 0) "${session.currentExp}/${session.nextExp}" else "MAX",
                    color = if (darkMode) MySubDark else MySubLight,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            ExpProgress(
                fraction = if (session.nextExp > 0) session.currentExp.toFloat() / session.nextExp else 1f,
                darkMode = darkMode,
            )
        } else {
            Text(
                text = "登录后可同步收藏、历史与稍后再看",
                color = if (darkMode) MySubDark else MySubLight,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Banner(avatar: ImageBitmap?, darkMode: Boolean, modifier: Modifier = Modifier) {
    val blurPx = with(LocalDensity.current) { 26.dp.toPx() }
    val canBlur = Build.VERSION.SDK_INT >= 31
    // BlurEffect 会把图层向外扩散，必须裁掉，否则头图会糊到信息区里。
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(listOf(Color(0xFFF87CA1), Color(0xFFB0335D)))
            )
        )
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    alpha = 0.94f
                    if (canBlur) renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                },
            )
            // 统一成品牌粉调，避免头像放大后喧宾夺主。
            Box(Modifier.matchParentSize().background(Color(0x33F1739B)))
        }
        // 状态栏图标是白色，顶部压一层深色保证可读。
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = .34f), 0.42f to Color.Transparent)
            )
        )
    }
}

@Composable
private fun ProfileAvatar(
    session: BiliSession?,
    avatar: ImageBitmap?,
    ring: Color,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(86.dp).clip(CircleShape)
            .background(if (darkMode) Color(0xFF2A2D33) else Color(0xFFE7ECF3)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = session?.username?.take(1) ?: "B",
                color = MyPink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.matchParentSize().border(3.dp, ring, CircleShape))
    }
}

@Composable
private fun ThemeToggle(darkMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val sunAlpha by animateFloatAsState(if (darkMode) 0f else 1f, label = "sun")
    val moonAlpha by animateFloatAsState(if (darkMode) 1f else 0f, label = "moon")
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp).graphicsLayer { alpha = sunAlpha }) {
            val unit = size.minDimension / 24f
            val tint = Color(0xFFF59E0B)
            val stroke = Stroke(2f * unit, cap = StrokeCap.Round)
            drawCircle(tint, 4f * unit, Offset(12f * unit, 12f * unit), style = stroke)
            for (index in 0 until 8) {
                val angle = Math.PI * index / 4.0
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                drawLine(
                    tint,
                    Offset(12f * unit + cos * 7.5f * unit, 12f * unit + sin * 7.5f * unit),
                    Offset(12f * unit + cos * 9.5f * unit, 12f * unit + sin * 9.5f * unit),
                    stroke.width,
                    StrokeCap.Round,
                )
            }
        }
        Canvas(Modifier.size(24.dp).graphicsLayer { alpha = moonAlpha }) {
            val unit = size.minDimension / 24f
            val path = PathParser().parsePathString("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z").toPath()
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(
                    path,
                    color = Color(0xFFE2E8F0),
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private val LevelBadgeShape = GenericShape { size, _ ->
    val u = size.width / 36f
    moveTo(4 * u, 0f)
    lineTo(30 * u, 0f)
    lineTo(36 * u, 6 * u)
    lineTo(36 * u, 18 * u)
    lineTo(4 * u, 18 * u)
    arcTo(Rect(Offset(0f, 10 * u), Size(8 * u, 8 * u)), 90f, 90f, false)
    lineTo(0f, 4 * u)
    arcTo(Rect(Offset(0f, 0f), Size(8 * u, 8 * u)), 180f, 90f, false)
    close()
}

@Composable
private fun LevelBadge(level: Int) {
    Box(
        Modifier.width(32.dp).height(16.dp).background(MyOrange, LevelBadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LV${level.coerceAtLeast(0)}",
            color = Color.White,
            style = TextStyle(
                fontSize = 9.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.3.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

@Composable
private fun ExpProgress(fraction: Float, darkMode: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (darkMode) MyTrackDark else MyTrackLight)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFFF6600), Color(0xFFFF3300))),
                    RoundedCornerShape(2.dp),
                )
        )
    }
}

// ------------------------------------------------------------------ grid ---
private class MyAction(
    val title: String,
    val tint: Color,
    val glyph: String,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

private const val ICON_OFFLINE = "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3"
private const val ICON_HISTORY = "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8 M3 3v5h5 M12 7v5 M12 12l3 3"
private const val ICON_STAR = "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26Z"
private const val ICON_WATCH_LATER = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0Z M10 8L16 12L10 16Z"
private const val ICON_LOGIN_WAYS = "M4 7.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0Z M10 10l10 10 M16 16l2-2 M18 18l2-2"
private const val ICON_SESSION = "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z M8 11V7a4 4 0 0 1 8 0v4 M11 16a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z"
private const val ICON_THEME = "M12 21a9 9 0 1 1 9-9c0 2-1.5 3.5-3.5 3.5h-2a2 2 0 0 0-1.5 3.3c.4.5.6 1 .6 1.7 0 1.5-1.2 2.5-2.6 2.5z M6.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z M11 7.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z M15.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z"
private const val ICON_ABOUT = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0Z M12 16v-4 M12 8h.01"
private const val ICON_LOCAL_VIDEO = "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z M10 11l6 3-6 3Z"
private const val ICON_CLEAR_CACHE = "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M10 11v6 M14 11v6"
private const val ICON_HELP = "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3 M12 17h.01"
private const val ICON_UPDATE = "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5 M12 7v5l3 3"

@Composable
private fun ActionGrid(
    darkMode: Boolean,
    background: Color,
    onOfflineCache: () -> Unit,
    onHistory: () -> Unit,
    onLocalVideos: () -> Unit,
    onLoginMethods: () -> Unit,
    onSession: () -> Unit,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    onClearCache: () -> Unit,
    onHelp: () -> Unit,
    onUpdate: () -> Unit,
    onFavorites: () -> Unit,
    onWatchLater: () -> Unit,
) {
    val rows = listOf(
        listOf(
            MyAction("离线缓存", MyBlue, ICON_OFFLINE, onClick = onOfflineCache),
            MyAction("历史记录", MyBlue, ICON_HISTORY, onClick = onHistory),
            MyAction("我的收藏", MyBlue, ICON_STAR, onClick = onFavorites),
            MyAction("稍后再看", MyBlue, ICON_WATCH_LATER, onClick = onWatchLater),
        ),
        listOf(
            MyAction("登录方式", MyRose, ICON_LOGIN_WAYS, onClick = onLoginMethods),
            MyAction("会话保存", MyRose, ICON_SESSION, onClick = onSession),
            MyAction("主题外观", MyRose, ICON_THEME, onClick = onTheme),
            MyAction("关于 bilibox", MyRose, ICON_ABOUT, onClick = onAbout),
        ),
        listOf(
            MyAction("本地视频", MyBlue, ICON_LOCAL_VIDEO, onClick = onLocalVideos),
            MyAction("清除缓存", MyBlue, ICON_CLEAR_CACHE, onClick = onClearCache),
            MyAction("帮助与反馈", MyBlue, ICON_HELP, onClick = onHelp),
            MyAction("检查更新", MyBlue, ICON_UPDATE, onClick = onUpdate),
        ),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    ActionItem(action, darkMode, background, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    action: MyAction,
    darkMode: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            // 这些是纯触摸入口：不参与焦点，避免弹层开关时被 bring-into-view 带着滚一屏。
            .focusProperties { canFocus = false }
            .clickable(onClick = action.onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            ActionGlyph(action.glyph, action.tint, Modifier.size(26.dp))
            action.badge?.let { count ->
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 11.dp, y = (-7).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MyBadgeRed)
                        .border(1.5.dp, background, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .defaultMinSize(minWidth = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = action.title,
            color = if (darkMode) MyLabelDark else MyLabelLight,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionGlyph(pathData: String, tint: Color, modifier: Modifier = Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            drawPath(
                path,
                color = tint,
                style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ----------------------------------------------------------------- login ---
@Composable
private fun LoginCard(
    state: LoginUiState,
    darkMode: Boolean,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onCredentialLogin: () -> Unit,
) {
    val card = if (darkMode) MyCardDark else MyCardLight
    val ink = if (darkMode) MyInkDark else MyInkLight
    val muted = if (darkMode) MySubDark else MySubLight
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(20.dp)).background(card).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("登录哔哩哔哩", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("扫码登录后可获取更高画质与账号权限", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        when (state) {
            LoginUiState.SignedOut -> {
                Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) {
                    Text("扫码登录")
                }
            }
            LoginUiState.CreatingQr -> CircularProgressIndicator(color = MyPink, strokeWidth = 2.dp)
            is LoginUiState.WaitingForScan -> {
                Image(
                    state.bitmap,
                    contentDescription = "哔哩哔哩登录二维码",
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(10.dp))
                Text(state.status, color = ink, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRetry) { Text("刷新二维码", color = MyPink) }
            }
            is LoginUiState.Failed -> {
                Text(state.message, color = MyBadgeRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MyPink)) {
                    Text("重新获取二维码")
                }
            }
        }
        if (state is LoginUiState.SignedOut || state is LoginUiState.Failed) {
            TextButton(onClick = onCredentialLogin) {
                Text("密码 / 短信验证码登录", color = MyPink, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "二维码由哔哩哔哩提供；密码与短信登录在应用内完成，仅人机验证使用官方组件。会话凭证使用 Android Keystore 加密存储。",
            color = muted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// --------------------------------------------------------------- dialogs ---
@Composable
private fun MyInfoDialog(
    sheet: MySheet,
    session: BiliSession?,
    darkMode: Boolean,
    version: String,
    onDismiss: () -> Unit,
) {
    val ink = if (darkMode) MyInkDark else MyInkLight
    val muted = if (darkMode) MySubDark else MySubLight
    val title = when (sheet) {
        MySheet.LoginMethods -> "登录方式"
        MySheet.Session -> "会话保存"
        MySheet.About -> "关于 bilibox"
        MySheet.Help -> "帮助与反馈"
        MySheet.Update -> "检查更新"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (darkMode) MyCardDark else MyCardLight,
        title = { Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                when (sheet) {
                    MySheet.LoginMethods -> {
                        Text(
                            "当前账号：${session?.username ?: "未登录"}\n\n" +
                                "支持扫码登录、账号密码登录和短信验证码登录。密码与短信登录在应用内完成，仅人机验证使用哔哩哔哩官方组件。",
                            color = muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    MySheet.Session -> {
                        Text(
                            "会话凭证使用 AES-GCM 加密后保存在本机 Android Keystore，不会上传到任何服务器；卸载应用或清除应用数据会一并清除。",
                            color = muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    MySheet.About -> {
                        Text("BiliBox $version", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "一个用 Jetpack Compose 写的哔哩哔哩客户端，支持在线视频、本地视频库与听视频模式。与哔哩哔哩官方无关，图标与账号数据归原权利方所有。",
                            color = muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    MySheet.Help -> {
                        Text(
                            "· 视频无法播放：先确认账号已登录，部分视频需要大会员或登录后才能取流。\n" +
                                "· 扫码后无反应：在哔哩哔哩 App 中确认登录，或改用密码 / 短信登录。\n" +
                                "· 本地视频缺失：在系统设置中授予媒体权限，部分目录含 .nomedia 会被跳过。\n" +
                                "· 缓存位置：视频缓存写入 Movies/BiliBox，可在「视频」页查看。",
                            color = muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    MySheet.Update -> {
                        Text(
                            "当前版本 $version\n\n更新检查尚未接入服务端，新版本请通过安装包手动更新。",
                            color = muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = MyPink) }
        },
    )
}

// ----------------------------------------------------------------- utils ---
@Composable
private fun rememberRemoteBitmap(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
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
    for (y in 0 until 720) for (x in 0 until 720) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap.asImageBitmap()
}
