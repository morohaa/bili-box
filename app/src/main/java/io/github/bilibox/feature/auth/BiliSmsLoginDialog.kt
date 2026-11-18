package io.github.bilibox.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.GeetestProof
import io.github.bilibox.data.auth.LoginCaptcha
import io.github.bilibox.data.auth.fetchBiliSession
import io.github.bilibox.data.auth.loginWithSmsCode
import io.github.bilibox.data.auth.requestLoginCaptcha
import io.github.bilibox.data.auth.sendLoginSmsCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BiliSmsLoginDialog(
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onSuccess: (BiliSession) -> Unit,
    onUseWebLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val panelColor = if (darkMode) Color(0xFF22252B) else Color.White
    val ink = if (darkMode) Color(0xFFE7E9ED) else Ink
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted
    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var captchaKey by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var captcha by remember { mutableStateOf<LoginCaptcha?>(null) }

    val phoneNumber = phone.trim()
    val phoneValid = phoneNumber.matches(Regex("1\\d{10}"))
    val codeValid = smsCode.trim().length >= 4

    LaunchedEffect(countdown) {
        if (countdown <= 0) return@LaunchedEffect
        delay(1000)
        countdown -= 1
    }

    fun requestSmsCode() {
        if (busy || countdown > 0 || captcha != null) return
        if (!phoneValid) {
            error = "请输入正确的手机号"
            return
        }
        error = null
        hint = null
        busy = true
        scope.launch {
            try {
                captcha = withContext(Dispatchers.IO) { requestLoginCaptcha() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "获取人机验证失败"
            } finally {
                busy = false
            }
        }
    }

    fun handleProof(proof: GeetestProof) {
        val ticket = captcha ?: return
        captcha = null
        busy = true
        error = null
        scope.launch {
            try {
                captchaKey = withContext(Dispatchers.IO) { sendLoginSmsCode(phoneNumber, ticket, proof) }
                countdown = 60
                hint = "验证码已发送至 +86 $phoneNumber"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    fun loginWithSms() {
        if (busy) return
        val code = smsCode.trim()
        val key = captchaKey
        error = null
        hint = null
        busy = true
        scope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    fetchBiliSession(loginWithSmsCode(phoneNumber, code, key))
                }
                onSuccess(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)).imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(panelColor)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("短信验证码登录", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { if (!busy) onDismiss() }) {
                        Text("关闭", color = Pink, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                CredentialField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        error = null
                        captchaKey = ""
                        countdown = 0
                    },
                    label = "手机号（+86）",
                    darkMode = darkMode,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CredentialField(
                        value = smsCode,
                        onValueChange = {
                            smsCode = it
                            error = null
                        },
                        label = "短信验证码",
                        darkMode = darkMode,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { requestSmsCode() },
                        enabled = !busy && countdown == 0,
                    ) {
                        Text(
                            if (countdown > 0) "${countdown}s 后重发" else "获取验证码",
                            color = if (!busy && countdown == 0) Pink else muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                PrimaryAction(
                    label = "登录",
                    busy = busy,
                    enabled = phoneValid && codeValid && captchaKey.isNotBlank(),
                ) { loginWithSms() }

                error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = if (darkMode) Color(0xFFF3B3C2) else Color(0xFFB54F67),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                hint?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = if (darkMode) Color(0xFF8FD6A8) else Color(0xFF2E8B57),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "仅用于本机获取账号权限，会话凭证使用 Android Keystore 加密保存。",
                    color = muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                TextButton(
                    onClick = { if (!busy) onUseWebLogin() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("遇到风控提示？使用网页登录", color = muted, fontSize = 11.sp)
                }
            }
        }
    }

    captcha?.let { ticket ->
        GeetestCaptchaDialog(
            captcha = ticket,
            darkMode = darkMode,
            onDismiss = { captcha = null },
            onSolved = { proof -> handleProof(proof) },
        )
    }
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    darkMode: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(
            fontSize = 15.sp,
            color = if (darkMode) Color(0xFFE7E9ED) else Ink,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pink,
            unfocusedBorderColor = if (darkMode) Color(0xFF3A3F47) else Color(0xFFE1E5EA),
            focusedLabelColor = Pink,
            unfocusedLabelColor = if (darkMode) Color(0xFF9BA3AE) else Muted,
            cursorColor = Pink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PrimaryAction(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Pink,
            contentColor = Color.White,
            disabledContainerColor = Pink.copy(alpha = .38f),
            disabledContentColor = Color.White.copy(alpha = .9f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
