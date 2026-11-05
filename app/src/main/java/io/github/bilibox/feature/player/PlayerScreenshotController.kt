package io.github.bilibox.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import io.github.bilibox.core.util.captureVideoFrame
import io.github.bilibox.core.util.saveVideoFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal class PlayerScreenshotController internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerView: () -> PlayerView?,
    private val canCapture: () -> Boolean,
    val flash: Animatable<Float, *>,
) {
    var saving by mutableStateOf(false)
        private set
    var previewFrame by mutableStateOf<Bitmap?>(null)
        private set
    var previewVisible by mutableStateOf(false)
        private set

    fun takeScreenshot() {
        if (saving || !canCapture()) return
        val frame = try {
            playerView()?.let(::captureVideoFrame)
        } catch (_: Exception) {
            null
        }
        if (frame == null) {
            Toast.makeText(context, "暂时无法截取画面，请等待视频显示后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val flashAnimation = scope.launch {
            flash.snapTo(.82f)
            flash.animateTo(0f, animationSpec = tween(360, easing = LinearEasing))
        }
        saving = true
        scope.launch {
            var saved = false
            try {
                withContext(Dispatchers.IO) { saveVideoFrame(context, frame) }
                saved = true
                flashAnimation.join()
                previewFrame = frame
                withFrameNanos { }
                previewVisible = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "截帧保存失败，请检查存储空间或权限后重试", Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
                if (!saved) frame.recycle()
            }
        }
    }

    fun dismissPreview(frame: Bitmap) {
        if (previewFrame === frame) {
            previewFrame = null
            frame.recycle()
        }
    }

    fun hidePreview() {
        previewVisible = false
    }
}

@Composable
internal fun rememberPlayerScreenshotController(
    playerView: PlayerView?,
    hasVideoFrame: Boolean,
    screenLocked: Boolean,
    playbackError: String?,
): PlayerScreenshotController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPlayerView = rememberUpdatedState(playerView)
    val currentCanCapture = rememberUpdatedState(hasVideoFrame && !screenLocked && playbackError == null)
    val flash = remember { Animatable(0f) }
    val controller = remember {
        PlayerScreenshotController(
            context = context,
            scope = scope,
            playerView = { currentPlayerView.value },
            canCapture = { currentCanCapture.value },
            flash = flash,
        )
    }

    LaunchedEffect(controller.previewFrame) {
        val frame = controller.previewFrame ?: return@LaunchedEffect
        delay(2_200)
        controller.hidePreview()
        delay(260)
        controller.dismissPreview(frame)
    }
    return controller
}
