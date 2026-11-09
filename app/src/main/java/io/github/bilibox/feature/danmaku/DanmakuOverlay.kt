package io.github.bilibox.feature.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import io.github.bilibox.data.danmaku.Danmaku
import io.github.bilibox.data.danmaku.DanmakuFilter
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.feature.danmaku.layout.DanmakuTrackBuilder
import kotlinx.coroutines.delay

/**
 * 盖在视频画面上的弹幕层。
 *
 * 位置每帧直接读取 [player] 的播放进度，因此拖动进度条、暂停、倍速和切 P 都会
 * 自然同步；没有弹幕或关闭显示时不会启动循环。绘制只用 Canvas，不注册 pointer
 * input，底下的手势层仍能正常响应。
 */
@Composable
internal fun DanmakuOverlay(
    player: Player,
    items: List<Danmaku>,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier,
) {
    // Keep the measured layouts and tracks alive while danmaku are hidden.
    // Otherwise every off→on toggle has to remeasure every comment on the main
    // thread before the switch icon can settle, which reads as input lag on
    // long videos.
    if (items.isEmpty()) return

    // 屏蔽在测量和排轨道之前生效：被过滤掉的弹幕既不会绘制，也不会占住轨道。
    val visible = remember(items, settings.blockedTypes) {
        DanmakuFilter.apply(items, settings)
    }
    if (visible.isEmpty()) return

    val localDensity = LocalDensity.current
    val density = localDensity.density
    val textMeasurer = rememberTextMeasurer()
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }

    // 按原始字号测量一次；用户字号在绘制时缩放，同时参与轨道宽高计算。
    // 滑杆每次变化都重新测量整个视频的弹幕，会长时间占用主线程。
    val measured = remember(visible, density, localDensity.fontScale) {
        visible.map { item ->
            val fontSize = item.fontSize.coerceIn(12, 36).sp
            item to textMeasurer.measure(
                text = AnnotatedString(item.text),
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = .82f),
                        offset = Offset(1.4f, 1.4f),
                        blurRadius = 2f,
                    ),
                ),
            )
        }
    }
    val layouts: Map<Danmaku, TextLayoutResult> = remember(measured) { measured.toMap() }
    val maxFontPx = remember(visible, density, localDensity.fontScale) {
        val largest = visible.maxOfOrNull { it.fontSize.coerceIn(12, 36) } ?: 25
        largest * localDensity.fontScale * density
    }

    BoxWithConstraints(
        modifier.fillMaxSize().clipToBounds()
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val laneHeightPx = maxFontPx * settings.fontScale * 1.35f
        val areaHeightPx = (heightPx * settings.areaFraction).coerceAtLeast(laneHeightPx)
        val tracks = remember(visible, layouts, widthPx, areaHeightPx, laneHeightPx, density, settings.speedScale) {
            DanmakuTrackBuilder.build(
                items = visible,
                viewportWidthPx = widthPx,
                areaHeightPx = areaHeightPx,
                laneHeightPx = laneHeightPx,
                density = density,
                speedScale = settings.speedScale,
                textWidthPx = { item -> (layouts[item]?.size?.width ?: 1) * settings.fontScale },
            )
        }

        LaunchedEffect(player, tracks, settings.enabled) {
            if (!settings.enabled || tracks.isEmpty()) return@LaunchedEffect
            while (true) {
                val now = player.currentPosition.coerceAtLeast(0L)
                positionMs = now
                if (player.isPlaying && tracks.any { it.isVisibleAt(now) }) {
                    withFrameNanos { }
                } else {
                    // No danmaku on screen: poll coarsely so a seek or the next
                    // comment still appears promptly without a 60fps redraw.
                    delay(120)
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            if (!settings.enabled) return@Canvas
            val now = positionMs
            tracks.forEach { track ->
                if (!track.isVisibleAt(now)) return@forEach
                val layout = layouts[track.item] ?: return@forEach
                val textWidth = layout.size.width * settings.fontScale
                val textHeight = layout.size.height * settings.fontScale
                val x = when (track.item.mode) {
                    Danmaku.Mode.SCROLL -> track.leftEdgeAt(now, widthPx)

                    Danmaku.Mode.TOP,
                    Danmaku.Mode.BOTTOM,
                    -> (widthPx - textWidth) / 2f
                }
                if (track.item.mode == Danmaku.Mode.SCROLL && (x > widthPx || x + textWidth < 0f)) {
                    return@forEach
                }
                val y = when (track.item.mode) {
                    Danmaku.Mode.SCROLL,
                    Danmaku.Mode.TOP,
                    -> track.lane * laneHeightPx + (laneHeightPx - textHeight) / 2f

                    Danmaku.Mode.BOTTOM ->
                        areaHeightPx - (track.lane + 1) * laneHeightPx + (laneHeightPx - textHeight) / 2f
                }
                val color = Color(track.item.color or 0xFF000000.toInt())
                    .copy(alpha = settings.opacity)
                val origin = Offset(x, y)
                scale(settings.fontScale, pivot = origin) {
                    drawText(
                        textLayoutResult = layout,
                        color = color,
                        topLeft = origin,
                    )
                }
            }
        }
    }
}
