package io.github.bilibox.core.ui.component

import android.os.Build
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.BrandSpringEasing
import io.github.bilibox.core.ui.theme.DarkInkSoft
import io.github.bilibox.core.ui.theme.DynamicColors
import io.github.bilibox.core.ui.theme.dynamicColors

private val HomeInk = Color(0xFF24313D)
private val HomePink = Color(0xFFEF7196)
private val HomeMuted = Color(0xFF82909D)

/**
 * 固定顶栏容器：宽满屏、内容高 64dp，并带一条可见的下边界。
 *
 * 三个 Tab 共用同一条栏的高度与样式，但各自可以通过 [content] 放入不同的
 * 内容；默认沿用品牌标题与夜间模式开关。
 */
@Composable
internal fun AppTopBar(
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    content: @Composable RowScope.() -> Unit = { BrandTopBarContent(darkMode, onToggleDarkMode) },
) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (darkMode) Color(0xFF17191D) else Color(0xFFF4F6FA))
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HorizontalDivider(
            color = if (darkMode) Color(0xFF30343A) else Color(0xFFE0E6ED),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun RowScope.BrandTopBarContent(darkMode: Boolean, onToggleDarkMode: () -> Unit) {
    Text("bili", color = HomePink, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-1).sp)
    Text("box", color = if (darkMode) Color(0xFFE9E9EC) else HomeInk, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-1).sp)
    Spacer(Modifier.weight(1f))
    DarkModeToggle(darkMode, onToggleDarkMode)
}

@Composable
internal fun DarkModeToggle(darkMode: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(if (darkMode) Color(0xFF292D33) else Color(0xFFE9EDF3))
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (darkMode) "切换日间模式" else "切换夜间模式" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(21.dp)) {
            val unit = size.minDimension / 24f
            val tint = if (darkMode) Color(0xFFFFC857) else Color(0xFF596779)
            val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round)
            if (darkMode) {
                drawCircle(tint, 7f * unit, Offset(12f * unit, 12f * unit), style = stroke)
                for (index in 0 until 8) {
                    val angle = Math.PI * index / 4.0
                    val inner = 9f * unit
                    val outer = 11f * unit
                    drawLine(
                        tint,
                        Offset(12f * unit + kotlin.math.cos(angle).toFloat() * inner, 12f * unit + kotlin.math.sin(angle).toFloat() * inner),
                        Offset(12f * unit + kotlin.math.cos(angle).toFloat() * outer, 12f * unit + kotlin.math.sin(angle).toFloat() * outer),
                        stroke.width,
                        StrokeCap.Round
                    )
                }
            } else {
                drawCircle(tint, 8f * unit, Offset(10f * unit, 10f * unit))
                drawCircle(if (darkMode) Color(0xFF292D33) else Color(0xFFE9EDF3), 7f * unit, Offset(14f * unit, 8f * unit))
            }
        }
    }
}

@Composable
internal fun EmptyCard(title: String, text: String, darkMode: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(if (darkMode) Color(0xFF24272D) else Color.White.copy(alpha = .7f), RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text(title, color = if (darkMode) DarkInkSoft else HomeInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = if (darkMode) Color(0xFF9BA3AE) else HomeMuted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

/** 底部悬浮玻璃导航：三段胶囊，选中项用粉色浅底与粉色图标文字。 */
@Composable
internal fun LiquidNavigation(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: GraphicsLayer,
    darkMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = dynamicColors(darkMode)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // 外层左右各留 5dp，让选中底色正好落在内层的一段宽度里。
        val itemWidth = (maxWidth - 10.dp) / 3
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selected.coerceIn(0, 2),
            animationSpec = tween(durationMillis = 420, easing = BrandSpringEasing),
            label = "glass selection position",
        )
        GlassSurface(backdrop, darkMode, Modifier.fillMaxWidth().height(66.dp)) {
            Box(Modifier.fillMaxSize().padding(5.dp)) {
                Box(
                    Modifier.offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .background(colors.pink.copy(alpha = .12f), CircleShape)
                )
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    listOf("动态", "视频", "我的").forEachIndexed { index, title ->
                        NavigationItem(
                            index = index,
                            title = title,
                            selected = selected == index,
                            colors = colors,
                            onClick = { onSelect(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSurface(
    backdrop: GraphicsLayer,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = dynamicColors(darkMode)
    var position by remember { mutableStateOf(Offset.Zero) }
    val blur = with(LocalDensity.current) { 20.dp.toPx() }
    val supportsBlur = android.os.Build.VERSION.SDK_INT >= 31
    val shape = RoundedCornerShape(33.dp)
    val tint = if (supportsBlur) {
        listOf(colors.navGlassTop, colors.navGlassBottom)
    } else if (darkMode) {
        listOf(Color(0xFF24272B).copy(alpha = .97f), Color(0xFF1C1E21).copy(alpha = .97f))
    } else {
        listOf(Color.White.copy(alpha = .94f), Color(0xFFF2F4F6).copy(alpha = .92f))
    }
    Box(
        modifier.onGloballyPositioned { position = it.positionInRoot() }
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                this.shape = shape
                ambientShadowColor = colors.navShadow
                spotShadowColor = colors.navShadow
            }
    ) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    if (supportsBlur) renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                }.drawWithContent { translate(-position.x, -position.y) { drawLayer(backdrop) } }
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(tint)))
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                val inset = stroke / 2
                drawRoundRect(
                    colors.navEdge,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    style = Stroke(stroke),
                )
                if (!darkMode) {
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = 0f))
                        ),
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        style = Stroke(stroke),
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun NavigationItem(
    index: Int,
    title: String,
    selected: Boolean,
    colors: DynamicColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) colors.pink else colors.navIdle,
        label = "navigation tint",
    )
    Column(
        modifier.fillMaxHeight().clip(CircleShape).selectable(selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIcon(index, color)
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun NavIcon(index: Int, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke.width, StrokeCap.Round)
        when (index) {
            0 -> {
                drawRoundRect(
                    tint,
                    Offset(3 * unit, 6 * unit),
                    androidx.compose.ui.geometry.Size(18 * unit, 14 * unit),
                    androidx.compose.ui.geometry.CornerRadius(4 * unit),
                    style = stroke,
                )
                line(8f, 3f, 10f, 6f)
                line(16f, 3f, 14f, 6f)
                line(9f, 12f, 9f, 14f)
                line(15f, 12f, 15f, 14f)
            }

            1 -> {
                drawRoundRect(
                    tint,
                    Offset(3 * unit, 6 * unit),
                    androidx.compose.ui.geometry.Size(18 * unit, 15 * unit),
                    androidx.compose.ui.geometry.CornerRadius(3 * unit),
                    style = stroke,
                )
                line(7f, 3f, 17f, 3f)
                val path = Path().apply {
                    moveTo(10.5f * unit, 10.5f * unit)
                    lineTo(10.5f * unit, 16.5f * unit)
                    lineTo(15.5f * unit, 13.5f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            else -> {
                drawCircle(tint, 4 * unit, Offset(12 * unit, 8 * unit), style = stroke)
                val path = Path().apply {
                    moveTo(4 * unit, 21 * unit)
                    cubicTo(5.5f * unit, 17 * unit, 8.5f * unit, 15 * unit, 12 * unit, 15 * unit)
                    cubicTo(15.5f * unit, 15 * unit, 18.5f * unit, 17 * unit, 20 * unit, 21 * unit)
                }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}
