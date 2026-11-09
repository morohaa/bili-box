package io.github.bilibox.feature.danmaku

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.playback.PlayerSidePanel
import io.github.bilibox.data.danmaku.DanmakuSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val DanmakuSheetSurface = Color(0xFFF6F7F8)
private val DanmakuGrabber = Color(0xFFC8CBD0)
private const val DANMAKU_SHEET_MAX_FRACTION = .92f
private val DANMAKU_MIN_SHEET_HEIGHT = 260.dp

/**
 * 弹幕设置面板。
 *
 * [topInset] 是视频画面下沿：传入时卡片从这里铺到屏幕底部，画面始终露在上方；
 * 竖屏全屏时视频占满舞台，传 null 退回屏幕底部弹层并限制最大高度。
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

            DanmakuSettingsContent(settings, onSettingsChange)
        }
    }
}

@Composable
internal fun DanmakuSettingsDrawer(
    settings: DanmakuSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    CompositionLocalProvider(LocalDanmakuSettingsColors provides DarkDanmakuSettingsColors) {
        PlayerSidePanel(
            title = "弹幕设置",
            onDismiss = onDismiss,
            closeDescription = "收起弹幕设置",
        ) {
            Column(
                Modifier.fillMaxSize()
                    .testTag("danmaku-settings-drawer")
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                DanmakuSettingsContent(settings, onSettingsChange)
            }
        }
    }
}
