package io.github.bilibox.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CancellationException

/** 保留切换前的画面，挖开不断扩大的圆，让下方的新主题从按钮中心显露。 */
@Composable
internal fun ThemeRevealTransition(
    onToggle: () -> Unit,
    content: @Composable (onToggle: (Offset) -> Unit) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    var previousFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val currentToggle by rememberUpdatedState(onToggle)
    val revealPath = remember { Path() }

    LaunchedEffect(origin) {
        if (origin == null) return@LaunchedEffect
        try {
            progress.snapTo(0f)
            previousFrame = try {
                layer.toImageBitmap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 图层暂不可捕获时仍正常切换主题。
                null
            }
            currentToggle()
            if (previousFrame != null) {
                // 先让新主题完成重组；旧画面在半径为零时完整遮住它。
                withFrameNanos { }
                progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            }
        } finally {
            previousFrame = null
            origin = null
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInRoot() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val image = previousFrame
                val center = origin
                if (image != null && center != null) {
                    val radius = hypot(
                        max(center.x, size.width - center.x),
                        max(center.y, size.height - center.y),
                    ) * progress.value
                    revealPath.reset()
                    revealPath.addOval(Rect(center, radius))
                    clipPath(revealPath, ClipOp.Difference) { drawImage(image) }
                }
            }
    ) {
        content { center ->
            if (origin == null) origin = center - rootPosition
        }
        if (origin != null) {
            // 扩散期间拦住触摸，避免切页或重复切换造成画面与快照错位。
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            )
        }
    }
}
