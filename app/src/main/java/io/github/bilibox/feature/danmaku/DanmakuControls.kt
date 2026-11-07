package io.github.bilibox.feature.danmaku

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.data.danmaku.DanmakuBlockType
import io.github.bilibox.data.danmaku.DanmakuSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DanmakuPink = Color(0xFFFB7299)

// 设置面板按设计稿用更亮的粉色，和底栏的品牌粉区分开。
private val DanmakuAccent = Color(0xFFFF759C)
private val DanmakuPinkSoft = Color(0xFFFFE8EF)
private val DanmakuPinkTrack = Color(0xFFFFB3C6)
private val DanmakuDanger = Color(0xFFFF4D4F)
private val DanmakuSheet = Color(0xFF1B1E23)
private val DanmakuSub = Color(0xFFB8BEC8)

// 设置面板改成浅色卡片后使用的一组中性色，取值与播放器设置面板保持一致。
private val DanmakuSheetSurface = Color(0xFFF6F7F8)
private val DanmakuCard = Color.White
private val DanmakuInk = Color(0xFF17191B)
private val DanmakuMuted = Color(0xFF9499A0)
private val DanmakuIdleTrack = Color(0xFFE1E4E6)
private val DanmakuGrabber = Color(0xFFC8CBD0)
private val DanmakuIconIdle = Color(0xFFF4F5F7)
private val DanmakuGlyphTint = Color(0xFF111315)

private const val DANMAKU_VALUE_HIGHLIGHT_MS = 900L
private const val DANMAKU_RESET_CONFIRM_MS = 3_000L
private const val DANMAKU_RESET_CONFIRM_DONE_MS = 1_600L
private const val DANMAKU_SHEET_MAX_FRACTION = .92f
private const val DANMAKU_AREA_GUIDE_MS = 900L
private val DANMAKU_MIN_SHEET_HEIGHT = 260.dp

/** 滑杆上的三个等分白点：两端被圆头轨道盖住，和设计稿一样只画中间三个。 */
private val DANMAKU_SLIDER_TICKS = listOf(.25f, .5f, .75f)

/** 速度的五个档位；数据层继续保存连续倍率，面板上取最接近的一档展示。 */
private val DANMAKU_SPEEDS = listOf(.6f, .8f, 1f, 1.25f, 1.6f)
private val DANMAKU_SPEED_LABELS = listOf("极慢", "较慢", "适中", "较快", "极快")

private val DanmakuIconSize = 24.dp
private val DanmakuIconPadding = 7.dp
private val DanmakuControlGap = 6.dp

/** 开关 + 设置两个按钮加间距的总宽，用于把弹幕输入框对准屏幕中心。 */
internal val DanmakuControlLeadingWidth =
    (DanmakuIconSize + DanmakuIconPadding * 2) * 2 + DanmakuControlGap * 2

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
        horizontalArrangement = Arrangement.spacedBy(DanmakuControlGap),
    ) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = if (settings.enabled) "关闭弹幕" else "开启弹幕" }
                .padding(horizontal = DanmakuIconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DanmakuStateIcon(
                enabled = settings.enabled,
                tint = Color.White,
                checkColor = DanmakuPink,
                modifier = Modifier.size(DanmakuIconSize),
            )
        }

        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = "弹幕设置" }
                .padding(horizontal = DanmakuIconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DanmakuSettingsIcon(
                tint = Color.White,
                gearColor = DanmakuPink,
                modifier = Modifier.size(DanmakuIconSize),
            )
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

/**
 * 弹幕设置面板。
 *
 * [topInset] 是视频画面下沿：传入时卡片从这里铺到屏幕底部，画面始终露在上方；
 * 横屏 / 竖屏全屏时视频占满舞台，传 null 退回屏幕底部弹层并限制最大高度。
 */
@Composable
internal fun DanmakuSettingsSheet(
    visible: Boolean,
    settings: DanmakuSettings,
    topInset: Dp?,
    onDismiss: () -> Unit,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    val scrim = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .42f))
                .clickable(
                    interactionSource = scrim,
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = "关闭弹幕设置" }
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(
            animationSpec = tween(320, easing = CubicBezierEasing(.22f, .8f, .25f, 1f)),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 视频下沿之上至少要留出一张卡片的高度，否则说明视频已铺满舞台。
            val anchored = topInset != null && topInset <= maxHeight - DANMAKU_MIN_SHEET_HEIGHT
            DanmakuSettingsPanel(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = onDismiss,
                modifier = if (anchored) {
                    Modifier.fillMaxSize().padding(top = topInset ?: 0.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .heightIn(max = maxHeight * DANMAKU_SHEET_MAX_FRACTION)
                },
            )
        }
    }
}

/** 面板外壳：顶部拖拽条可点按 / 下拉关闭，内容超长时整体滚动。 */
@Composable
private fun DanmakuSettingsPanel(
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThreshold = with(density) { 72.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Column(
        modifier
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .shadow(16.dp, shape)
            .clip(shape)
            .background(DanmakuSheetSurface)
            .testTag("danmaku-settings-sheet"),
    ) {
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragOffset > dismissThreshold) {
                                onDismiss()
                            } else {
                                scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                            }
                        },
                        onDragCancel = {
                            scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(DanmakuGrabber))
        }

        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
        ) {
            Text("弹幕设置", color = DanmakuInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            DanmakuPreview(settings = settings)
            Spacer(Modifier.height(16.dp))
            DanmakuSliderCard(settings = settings, onSettingsChange = onSettingsChange)

            Spacer(Modifier.height(20.dp))
            DanmakuSectionTitle("画面防挡")
            Spacer(Modifier.height(10.dp))
            DanmakuBlockCard(settings = settings, onSettingsChange = onSettingsChange)

            Spacer(Modifier.height(20.dp))
            DanmakuSectionTitle("更多工具")
            Spacer(Modifier.height(10.dp))
            DanmakuWatchFilterCard(settings = settings, onSettingsChange = onSettingsChange)

            Spacer(Modifier.height(4.dp))
            DanmakuResetAction(settings = settings, onSettingsChange = onSettingsChange)
        }
    }
}

/** 面板正文的分组标题，和卡片之间留出呼吸感。 */
@Composable
private fun DanmakuSectionTitle(title: String) {
    Text(
        title,
        color = DanmakuInk,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp),
    )
}

/** 白色圆角分组卡片。 */
@Composable
private fun DanmakuCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DanmakuCard)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        content = content,
    )
}

/** 显示区域 / 不透明度 / 字号 / 速度四个滑杆。 */
@Composable
private fun DanmakuSliderCard(
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    DanmakuCardContainer {
        DanmakuSliderRow(
            title = "显示区域",
            valueLabel = "${(settings.areaFraction * 100).roundToInt()}%",
            value = settings.areaFraction,
            range = DanmakuSettings.MIN_AREA_FRACTION..1f,
            ticks = DANMAKU_SLIDER_TICKS,
            onValueChange = { onSettingsChange(settings.copy(areaFraction = it)) },
        )
        DanmakuSliderRow(
            title = "不透明度",
            valueLabel = "${(settings.opacity * 100).roundToInt()}%",
            value = settings.opacity,
            range = DanmakuSettings.MIN_OPACITY..1f,
            onValueChange = { onSettingsChange(settings.copy(opacity = it)) },
        )
        DanmakuSliderRow(
            title = "字体大小",
            valueLabel = "${(settings.fontScale * 100).roundToInt()}%",
            value = settings.fontScale,
            range = DanmakuSettings.MIN_FONT_SCALE..DanmakuSettings.MAX_FONT_SCALE,
            onValueChange = { onSettingsChange(settings.copy(fontScale = it)) },
        )
        DanmakuSliderRow(
            title = "弹幕速度",
            valueLabel = danmakuSpeedLabel(settings.speedScale),
            value = danmakuSpeedLevel(settings.speedScale).toFloat(),
            range = 0f..DANMAKU_SPEEDS.lastIndex.toFloat(),
            ticks = DANMAKU_SLIDER_TICKS,
            onValueChange = { level ->
                onSettingsChange(settings.copy(speedScale = DANMAKU_SPEEDS[level.roundToInt()]))
            },
            last = true,
        )
    }
}

/** 画面防挡：一排图标切换屏蔽的弹幕类型。 */
@Composable
private fun DanmakuBlockCard(
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    DanmakuCardContainer {
        Text("按弹幕类型屏蔽", color = DanmakuInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("点击下方图标，即可屏蔽对应类型的弹幕", color = DanmakuMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DanmakuBlockType.entries.forEach { type ->
                DanmakuBlockTypeItem(
                    type = type,
                    blocked = settings.isBlocked(type),
                    modifier = Modifier.weight(1f),
                    onToggle = {
                        onSettingsChange(settings.withBlocked(type, !settings.isBlocked(type)))
                    },
                )
            }
        }
    }
}

/** 更多工具：只看自己、不看被屏蔽词命中的弹幕。 */
@Composable
private fun DanmakuWatchFilterCard(
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DanmakuCard)
    ) {
        DanmakuSwitchRow(
            title = "弹幕观看屏蔽词",
            description = "仅对我不展示特定弹幕",
            checked = !settings.isBlocked(DanmakuBlockType.REPEAT),
            // 数据层目前只有「重复」这一项能表达屏蔽词，开关接在重复屏蔽上。
            onCheckedChange = { onSettingsChange(settings.withBlocked(DanmakuBlockType.REPEAT, !it)) },
        )
    }
}

/** 重置：第一次点击转为确认态，3 秒内再点一次才恢复默认，避免误触。 */
@Composable
private fun DanmakuResetAction(
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (!confirming) return@LaunchedEffect
        delay(DANMAKU_RESET_CONFIRM_MS)
        confirming = false
    }
    LaunchedEffect(done) {
        if (!done) return@LaunchedEffect
        delay(DANMAKU_RESET_CONFIRM_DONE_MS)
        done = false
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) {
                if (confirming) {
                    confirming = false
                    done = true
                    // 保留弹幕总开关，重置只影响显示参数和屏蔽项。
                    onSettingsChange(DanmakuSettings(enabled = settings.enabled))
                } else {
                    confirming = true
                }
            }
            .semantics { contentDescription = "重置弹幕设置" }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                done -> "已恢复默认"
                confirming -> "确认重置？"
                else -> "重置弹幕设置"
            },
            color = if (confirming) DanmakuDanger else DanmakuMuted,
            fontSize = 14.sp,
        )
    }
}

/** 预览条：直接复用当前不透明度和字号，拖动滑杆时能立刻看到变化。 */
@Composable
private fun DanmakuPreview(settings: DanmakuSettings) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp))
            .background(DanmakuCard),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.clip(CircleShape)
                .background(DanmakuInk.copy(alpha = .72f))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                "这是弹幕预览效果~",
                color = Color.White.copy(alpha = settings.opacity),
                fontSize = (14 * settings.fontScale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一行滑杆：标题、右侧数值，拖动时数值用粉色强调，松手后恢复，和设计稿一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanmakuSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    ticks: List<Float> = emptyList(),
    last: Boolean = false,
) {
    var adjusting by remember { mutableStateOf(false) }
    // 只在拖动过程中高亮：拖动结束后停留一小会儿再变色，松手瞬间不会突然跳回去。
    LaunchedEffect(adjusting, value) {
        if (!adjusting) return@LaunchedEffect
        delay(DANMAKU_VALUE_HIGHLIGHT_MS)
        adjusting = false
    }
    Column(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = DanmakuInk, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(
                valueLabel,
                color = if (adjusting) DanmakuAccent else DanmakuMuted,
                fontSize = if (adjusting) 15.sp else 14.sp,
                fontWeight = if (adjusting) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Slider(
            value = value,
            onValueChange = {
                adjusting = true
                onValueChange(it)
            },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(24.dp)
                .semantics { contentDescription = "$title 滑杆" },
            // 默认滑块是 4×20 的竖条，这里换成设计稿里的圆形按钮 + 细轨道。
            thumb = {
                // 阴影要先于背景绘制，否则会盖在圆点上变成一圈暗边。
                Box(
                    Modifier.size(18.dp)
                        .shadow(4.dp, CircleShape, ambientColor = DanmakuAccent, spotColor = DanmakuAccent)
                        .background(DanmakuAccent, CircleShape)
                )
            },
            track = { state ->
                // value 不一定从 0 起（例如不透明度 20%~100%），按区间端点归一化。
                val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
                val fraction = ((state.value - range.start) / span).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(DanmakuIdleTrack)
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DanmakuPinkTrack)
                    )
                    if (ticks.isNotEmpty()) {
                        DanmakuSliderTicks(ticks)
                    }
                }
            },
        )
    }
}

/** 轨道上的白色等分点，位置按轨道两端各留半个滑块计算，和圆头轨道对齐。 */
@Composable
private fun DanmakuSliderTicks(ticks: List<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val inset = 9.dp.toPx()
        val trackWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val centerY = size.height / 2f
        ticks.forEach { fraction ->
            drawCircle(
                color = Color.White,
                radius = 1.6.dp.toPx(),
                center = Offset(inset + trackWidth * fraction, centerY),
            )
        }
    }
}

@Composable
private fun DanmakuSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DanmakuInk, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = DanmakuMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DanmakuAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = DanmakuIdleTrack,
            ),
        )
    }
}

/** 单个屏蔽类型：方块图标 + 名称，选中后整块换成粉色。 */
@Composable
private fun DanmakuBlockTypeItem(
    type: DanmakuBlockType,
    blocked: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val tint = if (blocked) DanmakuAccent else DanmakuGlyphTint
    val surface = if (blocked) DanmakuPinkSoft else DanmakuIconIdle
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics {
                contentDescription = if (blocked) "取消屏蔽${type.label}弹幕" else "屏蔽${type.label}弹幕"
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .background(surface),
            contentAlignment = Alignment.Center,
        ) {
            DanmakuBlockTypeGlyph(type = type, tint = tint, surface = surface, blocked = blocked)
            if (type == DanmakuBlockType.REPEAT) {
                // 重复用一枚 xN 字标，设计稿里它直接叠在弹幕框中间。
                Text(
                    "xN",
                    color = tint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-.5).sp,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            type.label,
            color = if (blocked) DanmakuAccent else DanmakuMuted,
            fontSize = 12.sp,
            fontWeight = if (blocked) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 屏蔽类型的示意图标：统一的弹幕条圆角框 + 类型专属笔画。
 *
 * 几何按设计稿的 1024 视图换算到 24 视图：框 18.5 × 13.1、顶部/底部是六连点、
 * 滚动是两行「点 + 文字条」、彩色是调色盘、重复只留框（字标在调用处叠加）。
 * 右下角的禁止徽标对应设计稿的选中态图标。图形不随时间变化，直接记住描边。
 */
@Composable
private fun DanmakuBlockTypeGlyph(
    type: DanmakuBlockType,
    tint: Color,
    surface: Color,
    blocked: Boolean,
) {
    val outline = remember { Stroke(width = 1.8f, join = StrokeJoin.Round) }
    Canvas(Modifier.size(26.dp)) {
        val factor = size.minDimension / 24f
        scale(factor, pivot = Offset.Zero) {
            val left = 2.75f
            val right = 21.25f
            val top = 5.45f
            val bottom = 18.55f
            val box = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        cornerRadius = CornerRadius(2.6f, 2.6f),
                    )
                )
            }
            drawPath(box, tint, style = outline)

            when (type) {
                DanmakuBlockType.TOP, DanmakuBlockType.BOTTOM -> {
                    // 六连点：顶部贴框口，底部贴框底，和设计稿一致。
                    val dotY = if (type == DanmakuBlockType.TOP) top + 3.29f else bottom - 4.37f
                    repeat(6) { index ->
                        drawRect(
                            color = tint,
                            topLeft = Offset(left + 3.26f + index * 2.18f, dotY),
                            size = Size(.95f, .95f),
                        )
                    }
                }

                DanmakuBlockType.SCROLL -> {
                    drawRect(tint, Offset(left + 3.8f, top + 3.29f), Size(.95f, .95f))
                    drawRect(tint, Offset(left + 5.97f, top + 3.29f), Size(7.06f, .95f))
                    drawRect(tint, Offset(left + 4.9f, top + 6f), Size(.95f, .95f))
                    drawRect(tint, Offset(left + 7.08f, top + 6f), Size(7.06f, .95f))
                }

                DanmakuBlockType.COLORFUL -> {
                    // 调色盘：横椭圆 + 右下拇指孔 + 一圈颜料点，和设计稿的轮廓一致。
                    val paletteLeft = 6.9f
                    val paletteTop = 7.4f
                    val paletteWidth = 11.8f
                    val paletteHeight = 7.9f
                    drawOval(
                        color = tint,
                        topLeft = Offset(paletteLeft, paletteTop),
                        size = Size(paletteWidth, paletteHeight),
                    )
                    drawCircle(
                        surface,
                        radius = 1.15f,
                        center = Offset(paletteLeft + paletteWidth - 1.1f, paletteTop + paletteHeight),
                    )
                    listOf(
                        Offset(paletteLeft + 2.4f, paletteTop + 2.4f),
                        Offset(paletteLeft + 4.7f, paletteTop + 1.9f),
                        Offset(paletteLeft + 7.0f, paletteTop + 2.2f),
                        Offset(paletteLeft + 2.9f, paletteTop + 4.8f),
                    ).forEach { dot -> drawCircle(surface, radius = .52f, center = dot) }
                }

                DanmakuBlockType.REPEAT -> Unit
            }

            if (blocked) {
                // 屏蔽态在右下角盖一枚禁止徽标，先挖掉底下的框线再画描边。
                val badge = Offset(18.6f, 15.2f)
                val badgeRadius = 4.05f
                drawCircle(surface, radius = badgeRadius, center = badge)
                drawCircle(tint, radius = badgeRadius, center = badge, style = Stroke(1.35f))
                drawLine(
                    color = tint,
                    start = Offset(badge.x - 2f, badge.y + 2f),
                    end = Offset(badge.x + 2f, badge.y - 2f),
                    strokeWidth = 1.35f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 调整「显示区域」时在视频上闪一下弹幕区域边界。
 *
 * 弹幕本身是顶对齐排布的，视频弹幕不密集时缩小区域只会少几行、不会挪位置，
 * 所以拖动滑杆时用一层遮罩把区域下沿画出来，让设置"看得见"。
 */
@Composable
internal fun DanmakuAreaGuide(
    areaFraction: Float,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableFloatStateOf(areaFraction) }
    LaunchedEffect(areaFraction) {
        if (areaFraction == previous) return@LaunchedEffect
        previous = areaFraction
        target = 1f
        delay(DANMAKU_AREA_GUIDE_MS)
        target = 0f
    }
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(180),
        label = "danmaku-area-guide",
    )
    if (alpha <= .01f) return

    Canvas(modifier.fillMaxSize()) {
        val boundary = size.height * areaFraction
        drawRect(
            color = Color.Black.copy(alpha = .4f * alpha),
            topLeft = Offset(0f, boundary),
            size = Size(size.width, (size.height - boundary).coerceAtLeast(0f)),
        )
        drawLine(
            color = Color.White.copy(alpha = .5f * alpha),
            start = Offset(0f, boundary),
            end = Offset(size.width, boundary),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** 数据层保存连续倍率，面板上取最接近的档位展示。 */
private fun danmakuSpeedLevel(speedScale: Float): Int {
    val nearest = DANMAKU_SPEEDS.indices.minByOrNull { abs(DANMAKU_SPEEDS[it] - speedScale) }
    return nearest ?: DANMAKU_SPEEDS.lastIndex / 2
}

/** 速度用档位文案而不是百分比，和面板里其它滑杆区分开。 */
private fun danmakuSpeedLabel(speedScale: Float): String = DANMAKU_SPEED_LABELS[danmakuSpeedLevel(speedScale)]
