package io.github.bilibox.feature.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
        SeekPreviewPositionProvider((positionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f), 8.dp.roundToPx(), 9.dp.roundToPx())
    }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        PlayerSeekPreview(preview, positionMs, Modifier.size(136.dp, 98.5.dp))
    }
}

/** 弹窗只展示已有预览会话的缩略图，不在手势开始时重新创建解码器。 */
@Composable
internal fun PlayerSeekPreview(preview: PlayerSeekPreviewController?, positionMs: Long, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.clip(shape).background(Color(0xFF151515))
            .border(1.dp, Color.White.copy(alpha = .7f), shape)
            .testTag("seek-preview"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (preview == null) {
                Text("暂无预览", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
            } else {
                SeekPreviewContent(preview)
            }
        }
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Text(formatPlaybackTime(positionMs), color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SeekPreviewContent(preview: PlayerSeekPreviewController) {
    val player = preview.networkPlayer
    if (player != null) {
        AndroidView(
            factory = {
                createTexturePlayerView(it, player).apply {
                    useController = false
                    keepScreenOn = false
                }
            },
            update = { it.player = player },
            onRelease = { view ->
                val surface = view.videoSurfaceView as? android.view.TextureView
                preview.retainNetworkFrame(surface?.takeIf { it.isAvailable }?.getBitmap(320, 180))
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
            contentScale = ContentScale.Fit,
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
