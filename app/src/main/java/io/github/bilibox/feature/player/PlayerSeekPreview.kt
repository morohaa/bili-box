package io.github.bilibox.feature.player

import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.bilibox.core.ui.component.playback.SeekThumbWidth
import io.github.bilibox.core.util.formatPlaybackTime

/** 以滑块为锚点，预览保持在进度条上方并夹在可见窗口内。 */
internal class SeekPreviewPositionProvider(private val fraction: Float, private val gap: Int, private val thumbRadius: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val progress = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
        val center = anchorBounds.left + thumbRadius + (anchorBounds.width - thumbRadius * 2).coerceAtLeast(0) * progress
        return IntOffset(
            (center - popupContentSize.width / 2).toInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height - gap).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

@Composable
internal fun PlayerSeekPreviewPopup(preview: PlayerSeekPreviewController?, positionMs: Long, durationMs: Long) {
    val provider = with(LocalDensity.current) {
        SeekPreviewPositionProvider(
            (positionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
            8.dp.roundToPx(),
            (SeekThumbWidth / 2).roundToPx(),
        )
    }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        PlayerSeekPreview(preview, positionMs)
    }
}

/** 弹窗只展示已有预览会话的缩略图，不在手势开始时重新创建解码器。 */
@Composable
internal fun PlayerSeekPreview(preview: PlayerSeekPreviewController?, positionMs: Long, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val aspectRatio = preview?.networkAspectRatio ?: preview?.frame?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val frameWidth = minOf(136.dp, 76.5.dp * aspectRatio)
    val frameHeight = minOf(76.5.dp, 136.dp / aspectRatio)
    Column(
        modifier.testTag("seek-preview"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(frameWidth, frameHeight).clip(shape).background(Color(0xFF151515))
                .border(1.dp, Color.White.copy(alpha = .7f), shape)
                .testTag("seek-preview-frame"),
            contentAlignment = Alignment.Center,
        ) {
            if (preview == null) {
                Text("暂无预览", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
            } else {
                SeekPreviewContent(preview)
            }
        }
        Text(
            formatPlaybackTime(positionMs),
            modifier = Modifier.testTag("seek-preview-time"),
            color = Color.White,
            fontSize = 11.sp,
            style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f)),
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun SeekPreviewContent(preview: PlayerSeekPreviewController) {
    val player = preview.networkPlayer
    if (player != null) {
        AndroidView(
            factory = {
                createTexturePlayerView(it, player).apply {
                    useController = false
                    keepScreenOn = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { it.player = player },
            onRelease = { view ->
                val surface = view.videoSurfaceView as? TextureView
                preview.retainNetworkFrame(surface?.takeIf { it.isAvailable }?.bitmap)
                view.player = null
            },
            modifier = Modifier.fillMaxSize().testTag("seek-preview-video"),
        )
    }
    val frame = preview.frame
    if (frame != null && (preview.local || !preview.networkRendered)) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = "进度预览画面",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag("seek-preview-image")
                .semantics { stateDescription = "预览位置：${preview.framePositionMs}" },
        )
    }
    if (frame == null && !preview.networkRendered) {
        Text(
            if (preview.failed) "预览暂不可用" else "加载中…",
            color = Color.White.copy(alpha = .75f),
            fontSize = 11.sp,
        )
    }
}
