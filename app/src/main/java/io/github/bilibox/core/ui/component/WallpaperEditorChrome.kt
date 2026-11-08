package io.github.bilibox.core.ui.component

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax
import kotlin.math.roundToInt

private val PanelEasing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
private val OverlayShadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 1.5f), 3f)
private val OverlayText = TextStyle(
    color = Color.White,
    shadow = OverlayShadow,
)
private val DangerTint = Color(0xFFFFB3C7)

/**
 * 「壁纸 / 头图」预览页共用的浮层：顶栏、可展开的调节面板与底部操作条。
 *
 * 内容层由调用方提供并自行截图导出；这里只负责沉浸式切换、面板开合、Toast 与
 * 系统栏显隐，因此首页壁纸和 16:9 头图可以复用同一套交互与视觉。
 */
@Composable
internal fun WallpaperEditorChrome(
    title: String,
    accent: Color,
    darkness: Int,
    blur: Int,
    onDarknessChange: (Int) -> Unit,
    onBlurChange: (Int) -> Unit,
    applyLabel: String,
    downloadLabel: String,
    saving: Boolean,
    onClose: () -> Unit,
    onChangeImage: () -> Unit,
    onDownload: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onTip: (String) -> Unit,
    toastText: String,
    toastVisible: Boolean,
    onPanelOpenChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scrimInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(panelOpen) { onPanelOpenChange(panelOpen) }

    fun enterImmersive() {
        panelOpen = false
        immersive = true
        onTip("点击屏幕退出全屏预览")
    }

    BackHandler {
        when {
            immersive -> immersive = false
            panelOpen -> panelOpen = false
            else -> onClose()
        }
    }

    // 全屏预览时隐藏系统栏；退出或离开页面时恢复，避免系统栏一直不回来。
    DisposableEffect(immersive) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (immersive) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        // 空白处点击：全屏时退出，面板打开时收起；顶部栏与面板自身会消费各自区域的点击。
        if (immersive) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClickLabel = "退出全屏预览",
                ) { immersive = false }
            )
        } else if (panelOpen) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClickLabel = "收起设置面板",
                ) { panelOpen = false }
            )
        }

        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(220)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = .55f),
                                1f to Color.Transparent,
                            )
                        )
                )

                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(
                        icon = AppIcon.CLOSE,
                        contentDescription = "关闭预览",
                        onClick = {
                            if (panelOpen) panelOpen = false else onClose()
                        },
                    )
                    Text(
                        text = title,
                        style = OverlayText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    CircleIconButton(
                        icon = AppIcon.IMAGE,
                        contentDescription = "更换图片",
                        onClick = onChangeImage,
                    )
                }

                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(360.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .34f to Color.Black.copy(alpha = .28f),
                                1f to Color.Black.copy(alpha = .85f),
                            )
                        )
                )

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    AnimatedVisibility(
                        visible = panelOpen,
                        enter = expandVertically(tween(300, easing = PanelEasing)) +
                            fadeIn(tween(200)),
                        exit = shrinkVertically(tween(260, easing = PanelEasing)) +
                            fadeOut(tween(160)),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            EditorPanel(
                                darkness = darkness,
                                blur = blur,
                                onDarknessChange = onDarknessChange,
                                onBlurChange = onBlurChange,
                                downloadLabel = downloadLabel,
                                saving = saving,
                                onDownload = onDownload,
                                onFullscreen = { enterImmersive() },
                                onReset = {
                                    panelOpen = false
                                    onReset()
                                },
                            )
                            Spacer(Modifier.height(15.dp))
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ApplyPill(
                            label = applyLabel,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                panelOpen = false
                                onApply()
                            },
                        )
                        MoreButton(
                            active = panelOpen,
                            onClick = { panelOpen = !panelOpen },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp),
            enter = fadeIn(tween(220)) +
                slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier.clip(CircleShape)
                    .background(Color.Black.copy(alpha = .8f))
                    .border(1.dp, Color.White.copy(alpha = .1f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .2f))
            .border(1.dp, Color.White.copy(alpha = .15f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(icon, Color.White, Modifier.size(18.dp), strokeWidth = 2f)
    }
}

@Composable
private fun EditorPanel(
    darkness: Int,
    blur: Int,
    onDarknessChange: (Int) -> Unit,
    onBlurChange: (Int) -> Unit,
    downloadLabel: String,
    saving: Boolean,
    onDownload: () -> Unit,
    onFullscreen: () -> Unit,
    onReset: () -> Unit,
) {
    val panelInteraction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = .15f))
            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(20.dp))
            // 面板内的空白点击不应把面板收起，吞掉即可。
            .clickable(interactionSource = panelInteraction, indication = null) {}
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SliderRow(
            label = "暗化遮罩",
            value = darkness,
            max = WallpaperDarknessMax,
            suffix = "%",
            onValueChange = onDarknessChange,
        )
        SliderRow(
            label = "背景模糊",
            value = blur,
            max = WallpaperBlurMax,
            suffix = "px",
            onValueChange = onBlurChange,
        )
        HorizontalDivider(color = Color.White.copy(alpha = .12f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelAction(
                label = downloadLabel,
                icon = AppIcon.DOWNLOAD,
                loading = saving,
                enabled = !saving,
                onClick = onDownload,
            )
            PanelAction(
                label = "全屏预览",
                icon = AppIcon.FULLSCREEN,
                onClick = onFullscreen,
            )
            PanelAction(
                label = "恢复默认",
                icon = AppIcon.ROTATE_LEFT,
                danger = true,
                onClick = onReset,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    max: Int,
    suffix: String,
    onValueChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = OverlayText,
            fontSize = 13.sp,
            modifier = Modifier.width(60.dp),
        )
        Slider(
            value = value.coerceIn(0, max).toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, max)) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).height(32.dp),
            thumb = {
                Box(
                    Modifier.size(20.dp)
                        .shadow(3.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            track = {
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .3f))
                )
            },
        )
        Text(
            text = "$value$suffix",
            style = OverlayText,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun PanelAction(
    label: String,
    icon: AppIcon,
    danger: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (danger) DangerTint else Color.White
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = .1f))
            .border(
                width = 1.dp,
                color = if (danger) tint.copy(alpha = .35f) else Color.White.copy(alpha = .18f),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            AppGlyphIcon(icon, tint, Modifier.size(18.dp), strokeWidth = 2f)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ApplyPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .97f else 1f,
        animationSpec = tween(130),
        label = "wallpaperApplyScale",
    )
    Box(
        modifier.height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                14.dp,
                CircleShape,
                ambientColor = accent.copy(alpha = .4f),
                spotColor = accent.copy(alpha = .55f),
            )
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, .24f))))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MoreButton(active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) .38f else .2f))
            .border(1.dp, Color.White.copy(alpha = .3f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "更多设置"
                stateDescription = if (active) "已展开" else "已收起"
            },
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(AppIcon.MORE_HORIZONTAL, Color.White, Modifier.size(22.dp))
    }
}
