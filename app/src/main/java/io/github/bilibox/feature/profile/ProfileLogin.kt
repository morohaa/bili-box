package io.github.bilibox.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.data.auth.BiliSession

internal sealed interface LoginUiState {
    data object SignedOut : LoginUiState
    data object CreatingQr : LoginUiState
    data class WaitingForScan(val key: String, val bitmap: ImageBitmap, val status: String, val cookie: String) : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

/** 我的页里由宫格打开的说明弹层。 */
internal enum class MySheet { LoginMethods, Session, About, Help, Update }

@Composable
internal fun ProfileLoginDialog(
    state: LoginUiState,
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onCredentialLogin: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LoginCard(
            state = state,
            darkMode = darkMode,
            onLogin = onLogin,
            onRetry = onRetry,
            onCredentialLogin = onCredentialLogin,
        )
    }
}

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
        Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(20.dp)).background(card)
            .verticalScroll(rememberScrollState()).padding(20.dp),
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
internal fun MyInfoDialog(
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
                                "· 缓存位置：视频缓存写入 Movies/BiliBox，可在「我的 → 离线缓存」查看。",
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
