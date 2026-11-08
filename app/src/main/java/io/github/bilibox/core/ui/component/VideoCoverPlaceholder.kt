package io.github.bilibox.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max

/** 插画设计稿尺寸，绘制时等比缩放铺满容器。 */
private const val ArtWidth = 240f
private const val ArtHeight = 135f

private val SkyTop = Color(0xFFB8C6D8)
private val SkyBottom = Color(0xFF9AAABF)
private val SkyDecor = Color(0xFFD1DCE8)
private val Hill = Color(0xFF6A6B83)
private val TvShell = Color(0xFFFFFFFF)
private val TvEyes = Color(0xFFFB7299)

/**
 * 视频封面缺失（加载中、加载失败或搜索结果没有封面地址）时的默认占位插画：
 * 蓝灰渐变天空、右上角圆形与左侧横线装饰、底部深色山丘，中央是 B 站小电视。
 *
 * 设计稿为 16:9，按 cover 方式居中裁切铺满容器，列表卡的窄封面与宫格卡的宽封面
 * 都不会被拉伸；小电视始终落在容器中心，裁掉的两侧只有天空与山丘。
 */
@Composable
internal fun VideoCoverPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = max(size.width / ArtWidth, size.height / ArtHeight)
        val offsetX = (size.width - ArtWidth * scale) / 2f
        val offsetY = (size.height - ArtHeight * scale) / 2f
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(SkyTop, SkyBottom),
                    start = Offset.Zero,
                    end = Offset(0f, ArtHeight),
                ),
                size = Size(ArtWidth, ArtHeight),
            )
            drawCircle(SkyDecor, radius = 24f, center = Offset(195f, 32f))
            drawRoundRect(
                SkyDecor,
                topLeft = Offset(20f, 24f),
                size = Size(58f, 7f),
                cornerRadius = CornerRadius(3.5f),
            )
            drawRoundRect(
                SkyDecor,
                topLeft = Offset(20f, 38f),
                size = Size(40f, 7f),
                cornerRadius = CornerRadius(3.5f),
            )

            // 底部山丘：两段二次贝塞尔连成一道起伏的深色轮廓，右侧留低，避免顶到时长胶囊。
            val hill = Path().apply {
                moveTo(0f, 105f)
                quadraticTo(60f, 75f, 125f, 98f)
                quadraticTo(190f, 121f, 240f, 85f)
                lineTo(ArtWidth, ArtHeight)
                lineTo(0f, ArtHeight)
                close()
            }
            drawPath(hill, Hill)

            // 中央小电视：天线、白色圆角机身与粉色的「><」双眼，整体平移到设计稿坐标。
            withTransform({ translate(88f, 44f) }) {
                drawLine(
                    TvShell,
                    Offset(16f, 4f),
                    Offset(26f, 12f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    TvShell,
                    Offset(48f, 4f),
                    Offset(38f, 12f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    TvShell,
                    topLeft = Offset(8f, 12f),
                    size = Size(48f, 36f),
                    cornerRadius = CornerRadius(9f),
                )
                val eyes = Path().apply {
                    moveTo(21f, 26f)
                    lineTo(27f, 31f)
                    lineTo(21f, 36f)
                    moveTo(43f, 26f)
                    lineTo(37f, 31f)
                    lineTo(43f, 36f)
                }
                drawPath(
                    eyes,
                    TvEyes,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
