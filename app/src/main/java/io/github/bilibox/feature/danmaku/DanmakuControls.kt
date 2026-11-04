package io.github.bilibox.feature.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.data.danmaku.DanmakuArea
import io.github.bilibox.data.danmaku.DanmakuSettings

private val DanmakuPink = Color(0xFFFB7299)
private val DanmakuSheet = Color(0xFF1B1E23)
private val DanmakuSub = Color(0xFFB8BEC8)

/**
 * 全屏播放器底栏中间的弹幕控制组：开关、设置和发送入口。
 * 造型沿用原来的播放器胶囊，保证和倍速/画质按钮同一视觉层级。
 */
@Composable
internal fun DanmakuControlBar(
    settings: DanmakuSettings,
    loading: Boolean,
    error: String?,
    canSend: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .background(if (settings.enabled) DanmakuPink.copy(alpha = .18f) else Color.White.copy(alpha = .12f))
                .border(
                    width = 1.dp,
                    color = if (settings.enabled) DanmakuPink.copy(alpha = .55f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = if (settings.enabled) "关闭弹幕" else "开启弹幕" }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("弹", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            if (settings.enabled) {
                Box(
                    Modifier.size(13.dp).background(DanmakuPink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 8.sp,
                    )
                }
            } else {
                Box(
                    Modifier.size(13.dp).border(1.dp, Color.White.copy(alpha = .5f), CircleShape),
                )
            }
        }

        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .12f))
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = "弹幕设置" }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("弹", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Canvas(Modifier.size(13.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                drawCircle(
                    DanmakuPink,
                    radius * .42f,
                    center,
                    style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        DanmakuPink,
                        Offset(center.x + cos * radius * .56f, center.y + sin * radius * .56f),
                        Offset(center.x + cos * radius * .9f, center.y + sin * radius * .9f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(DanmakuPink, radius * .12f, center)
            }
        }

        Row(
            Modifier.weight(1f).height(30.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .14f))
                .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                .clickable(role = Role.Button, onClick = onSend)
                .semantics { contentDescription = if (canSend) "发送弹幕" else "登录后发送弹幕" }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    loading -> "弹幕加载中…"
                    error != null -> "弹幕加载失败"
                    else -> "发个友善的弹幕见证当下…"
                },
                color = Color.White.copy(alpha = .68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "发送",
                color = if (canSend) DanmakuPink else Color.White.copy(alpha = .4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** 发送弹幕的输入弹层。字符上限与 B 站接口一致，失败信息保留在弹层里便于重试。 */
@Composable
internal fun DanmakuComposerDialog(
    sending: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canSubmit = text.isNotBlank() && !sending
    fun submit() {
        if (canSubmit) {
            focusManager.clearFocus()
            onSend(text.trim())
        }
    }

    Dialog(
        onDismissRequest = { if (!sending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DanmakuSheet)
                .imePadding()
                .padding(20.dp)
        ) {
            Text("发送弹幕", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.take(100) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("发个友善的弹幕见证当下…", color = Color.White.copy(alpha = .4f)) },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                minLines = 3,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DanmakuPink,
                    unfocusedBorderColor = Color.White.copy(alpha = .18f),
                    cursorColor = DanmakuPink,
                ),
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            error.orEmpty(),
                            color = Color(0xFFFF8A8A),
                            fontSize = 12.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${text.length}/100", color = DanmakuSub, fontSize = 12.sp)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !sending) {
                    Text("取消", color = DanmakuSub)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { submit() },
                    enabled = canSubmit,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = DanmakuPink.copy(alpha = if (canSubmit) 1f else .3f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (sending) "发送中…" else "发送", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** 弹幕显示设置：开关、不透明度、字号、显示区域和滚动速度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DanmakuSettingsSheet(
    settings: DanmakuSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DanmakuSheet,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .24f))
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("弹幕设置", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "恢复默认",
                    color = DanmakuPink,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onSettingsChange(DanmakuSettings(enabled = settings.enabled))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = "显示弹幕",
                description = "关闭后画面不再绘制弹幕",
                checked = settings.enabled,
                onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
            )

            SettingSliderRow(
                title = "不透明度",
                valueLabel = "${(settings.opacity * 100).toInt()}%",
                value = settings.opacity,
                range = .2f..1f,
                onValueChange = { onSettingsChange(settings.copy(opacity = it)) },
            )
            SettingSliderRow(
                title = "字号",
                valueLabel = "${(settings.fontScale * 100).toInt()}%",
                value = settings.fontScale,
                range = .7f..1.5f,
                onValueChange = { onSettingsChange(settings.copy(fontScale = it)) },
            )
            SettingSliderRow(
                title = "滚动速度",
                valueLabel = "${(settings.speedScale * 100).toInt()}%",
                value = settings.speedScale,
                range = .5f..1.8f,
                onValueChange = { onSettingsChange(settings.copy(speedScale = it)) },
            )

            Spacer(Modifier.height(10.dp))
            Text("显示区域", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DanmakuArea.entries.forEach { area ->
                    val selected = area == settings.area
                    Box(
                        Modifier.weight(1f).height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) DanmakuPink.copy(alpha = .2f) else Color.White.copy(alpha = .08f))
                            .border(
                                1.dp,
                                if (selected) DanmakuPink.copy(alpha = .7f) else Color.White.copy(alpha = .12f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(role = Role.Button) {
                                onSettingsChange(settings.copy(area = area))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            area.label,
                            color = if (selected) DanmakuPink else Color.White.copy(alpha = .78f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = DanmakuSub, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DanmakuPink,
                uncheckedThumbColor = Color.White.copy(alpha = .8f),
                uncheckedTrackColor = Color.White.copy(alpha = .18f),
            ),
        )
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = DanmakuSub, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = DanmakuPink,
                activeTrackColor = DanmakuPink,
                inactiveTrackColor = Color.White.copy(alpha = .18f),
            ),
        )
    }
}
