package io.github.bilibox.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Keep the outgoing frame underneath the incoming page without retaining its player or handlers. */
@Stable
internal class AppPageTransitionState(private val scope: CoroutineScope) {
    val progress = Animatable(1f)
    var running by mutableStateOf(false)
        private set
    var previousFrame by mutableStateOf<ImageBitmap?>(null)
        private set
    var captureFrame: (suspend () -> ImageBitmap?)? = null
    private var direction by mutableStateOf(1)
    private var distance by mutableStateOf(32f)
    val offsetDp: Float get() = direction * distance * (1f - progress.value)

    fun forward(action: () -> Unit) = navigate(1, 32f, action)
    fun back(action: () -> Unit) = navigate(-1, 32f, action)
    fun tab(from: AppTab, to: AppTab, action: () -> Unit) {
        if (running) return
        if (from == to) {
            action()
            return
        }
        navigate(if (to.ordinal > from.ordinal) 1 else -1, 18f, action)
    }

    private fun navigate(nextDirection: Int, nextDistance: Float, action: () -> Unit) {
        if (running) return
        running = true
        direction = nextDirection
        distance = nextDistance
        scope.launch {
            try {
                val frame = if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                    null
                } else {
                    try {
                        captureFrame?.invoke()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // An unavailable frame must never prevent navigation.
                        null
                    }
                }
                if (frame == null) {
                    action()
                    return@launch
                }
                previousFrame = frame
                progress.snapTo(0f)
                action()
                withFrameNanos { }
                progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            } finally {
                previousFrame = null
                running = false
            }
        }
    }
}

@Composable
internal fun rememberAppPageTransitionState(): AppPageTransitionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppPageTransitionState(scope) }
}

@Composable
internal fun AppPageTransition(
    state: AppPageTransitionState,
    background: Color,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(state, layer) {
        state.captureFrame = {
            if (layer.size.width > 0 && layer.size.height > 0) layer.toImageBitmap() else null
        }
        onDispose { state.captureFrame = null }
    }
    Box(
        Modifier.fillMaxSize().background(background).clipToBounds()
            .drawWithContent {
                layer.record {
                    drawRect(background)
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
            }
            .testTag("app-page-transition")
            .semantics { stateDescription = if (state.running) "页面切换中" else "页面就绪" },
    ) {
        state.previousFrame?.let { frame ->
            // Keep this frame opaque, including the edges exposed by the incoming slide.
            Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val animating = state.previousFrame != null
                alpha = if (animating) state.progress.value else 1f
                translationX = if (animating) state.offsetDp * density else 0f
            },
        ) { content() }
        if (state.running) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }
    }
    BackHandler(enabled = state.running) { }
}
