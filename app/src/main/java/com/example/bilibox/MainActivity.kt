package com.example.bilibox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibox.ui.theme.BiliBoxTheme

private val Ink = Color(0xFF24313D)
private val Pink = Color(0xFFEF7196)
private val Muted = Color(0xFF82909D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BiliBoxTheme { BiliBoxApp() } }
    }
}

@Composable
private fun BiliBoxApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F6FA))) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }) {
            // The scrollable page is captured once and shared by both glass surfaces.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 24.dp)) {
                Header()
                when (selected) {
                    0 -> HomeContent(saved) { saved = !saved }
                    1 -> CollectionPage("动态", "关注的人，分享的日常", "今天的灵感", "沿着海岸线，收集一整个夏天")
                    2 -> {
                        SectionTitle("媒体库", "把喜欢的留在这里")
                        Spacer(Modifier.height(24.dp))
                        if (saved) VideoCard("日落之前，去海边走走", "你的收藏 · 1 个视频", 0)
                        else EmptyCard("还没有收藏", "在首页点击收藏，把喜欢的视频放进媒体库。")
                    }
                    else -> {
                        SectionTitle("我的", "每一次好奇，都值得被记录")
                        Spacer(Modifier.height(28.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).background(Pink.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("B", color = Pink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column { Text("BiliBox 观众", fontWeight = FontWeight.Bold, color = Ink, fontSize = 20.sp); Text("今天也有新的发现", color = Muted, fontSize = 13.sp) }
                        }
                        Spacer(Modifier.height(24.dp))
                        EmptyCard("${if (saved) 1 else 0} 个收藏", "喜欢的内容，会在媒体库里等你。")
                    }
                }
                Spacer(Modifier.height(150.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
        LiquidNavigation(selected, { selected = it }, backdrop,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("bili", color = Pink, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-1).sp)
        Text("box", color = Ink, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-1).sp)
        Spacer(Modifier.weight(1f))
        Text("让喜欢，自在发生", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-1).sp)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Muted, fontSize = 13.sp)
}

@Composable
private fun HomeContent(saved: Boolean, onSave: () -> Unit) {
    var liked by rememberSaveable { mutableStateOf(false) }
    SectionTitle("发现你的下一份喜欢", "放慢一点，好内容值得停留。")
    Spacer(Modifier.height(26.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("为你推荐", color = Pink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("生活", color = Muted, fontSize = 13.sp)
        Text("音乐", color = Muted, fontSize = 13.sp)
        Text("旅行", color = Muted, fontSize = 13.sp)
    }
    Spacer(Modifier.height(20.dp))
    Landscape(0, Modifier.fillMaxWidth().aspectRatio(1.25f))
    Spacer(Modifier.height(18.dp))
    Text("日落之前，去海边走走", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text("把城市的喧嚣，交给海浪。", color = Muted, fontSize = 13.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(Color(0xFFDDEBEA), CircleShape), contentAlignment = Alignment.Center) { Text("海", color = Color(0xFF6A9997), fontSize = 12.sp) }
        Spacer(Modifier.width(8.dp))
        Text("海边的阿树", color = Ink, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("12.8 万次观看", color = Muted, fontSize = 11.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = liked, onClick = { liked = !liked }, label = { Text(if (liked) "♥ 已点赞" else "♡ 点赞") })
        FilterChip(selected = saved, onClick = onSave, label = { Text(if (saved) "★ 已收藏" else "☆ 收藏") })
    }
    Spacer(Modifier.height(28.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("继续发现", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Text("为好奇心留一点时间", color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(16.dp))
    VideoCard("山野之间，有风经过", "山间来信 · 8.6 万次观看", 1)
    Spacer(Modifier.height(20.dp))
    VideoCard("今天，也要好好生活", "日常放映室 · 3.2 万次观看", 2)
}

@Composable
private fun CollectionPage(title: String, subtitle: String, cardTitle: String, description: String) {
    SectionTitle(title, subtitle)
    Spacer(Modifier.height(26.dp))
    Text("海边的阿树 · 今天 09:41", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    Text(description, color = Ink, fontSize = 17.sp)
    Spacer(Modifier.height(16.dp))
    VideoCard(cardTitle, "生活的片段，也可以很闪亮", 0)
}

@Composable
private fun EmptyCard(title: String, text: String) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .7f), RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun VideoCard(title: String, subtitle: String, variant: Int) {
    Landscape(variant, Modifier.fillMaxWidth().aspectRatio(1.9f))
    Spacer(Modifier.height(12.dp))
    Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(subtitle, color = Muted, fontSize = 11.sp)
}

/** Local artwork keeps the navigation demo usable offline; no video stream is attached. */
@Composable
private fun Landscape(variant: Int, modifier: Modifier = Modifier) {
    val sky = when (variant) {
        1 -> listOf(Color(0xFFAFCCCA), Color(0xFFE1E6D5), Color(0xFF789B8A))
        2 -> listOf(Color(0xFFD6B8C0), Color(0xFFF3DECC), Color(0xFFA99DAE))
        else -> listOf(Color(0xFF8DAEBE), Color(0xFFE9C8B4), Color(0xFF6B99A1))
    }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(sky))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawCircle(Color(0xFFFFE6B6), w * .095f, Offset(w * .72f, h * .36f))
            drawRect(Color(0xFF6B96A0).copy(alpha = .55f), Offset(0f, h * .60f), androidx.compose.ui.geometry.Size(w, h * .4f))
            val hill = Path().apply { moveTo(0f, h * .78f); cubicTo(w * .25f, h * .44f, w * .38f, h * .88f, w * .66f, h * .84f); cubicTo(w * .80f, h * .82f, w * .9f, h * .72f, w, h * .77f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(hill, Color(0xFF344F58).copy(alpha = .80f))
            for (i in 0..5) drawLine(Color.White.copy(alpha = .18f), Offset(w * .52f, h * (.66f + i * .025f)), Offset(w * .91f, h * (.66f + i * .025f)), 1.4f)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text(if (variant == 1) "INTO THE WILD" else "A LITTLE ESCAPE", color = Color.White.copy(alpha = .8f), fontSize = 9.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(7.dp))
            Text(if (variant == 1) "去有风的地方" else "和日落，见一面。", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
        Text("画面预览", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color.Black.copy(alpha = .15f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
        if (variant == 0) {
            Box(Modifier.align(Alignment.Center).size(58.dp).background(Color.White.copy(alpha = .88f), CircleShape), contentAlignment = Alignment.Center) {
                Text("▶", color = Color(0xFF4D6673), fontSize = 22.sp, modifier = Modifier.padding(start = 3.dp))
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.Black.copy(alpha = .2f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(42.dp).height(2.dp).background(Color.White.copy(alpha = .55f), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("01:24 / 04:52", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

/** A three-item capsule plus a detached profile button: (xxx) (x). */
@Composable
private fun LiquidNavigation(selected: Int, onSelect: (Int) -> Unit, backdrop: GraphicsLayer, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val gap = 12.dp
        val circle = 68.dp
        val capsuleWidth = maxWidth - circle - gap
        val itemWidth = (capsuleWidth - 12.dp) / 3
        val indicatorOffset by animateDpAsState(itemWidth * selected.coerceAtMost(2), label = "glass selection position")
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            GlassSurface(backdrop, Modifier.width(capsuleWidth).height(68.dp)) {
                Box(Modifier.fillMaxSize().padding(6.dp)) {
                    if (selected < 3) Box(Modifier.offset(x = indicatorOffset).width(itemWidth).fillMaxHeight()
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .38f))), CircleShape)
                        .then(Modifier.clip(CircleShape)))
                    Row(Modifier.fillMaxSize().selectableGroup()) {
                        listOf("首页", "动态", "媒体库").forEachIndexed { index, title ->
                            NavigationItem(index, title, selected == index, { onSelect(index) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            GlassSurface(backdrop, Modifier.size(circle)) {
                Box(Modifier.fillMaxSize().padding(6.dp).background(if (selected == 3) Color.White.copy(alpha = .8f) else Color.Transparent, CircleShape)) {
                    NavigationItem(3, "我的", selected == 3, { onSelect(3) }, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun GlassSurface(backdrop: GraphicsLayer, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val blur = with(LocalDensity.current) { 18.dp.toPx() }
    val supportsBlur = android.os.Build.VERSION.SDK_INT >= 31
    val shape = RoundedCornerShape(36.dp)
    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer { shadowElevation = 18.dp.toPx(); this.shape = shape; ambientShadowColor = Color(0xFF5D7288).copy(alpha = .22f); spotShadowColor = Color(0xFF5D7288).copy(alpha = .22f) }) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Box(Modifier.matchParentSize().graphicsLayer {
                if (supportsBlur) renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            }.drawWithContent { translate(-position.x, -position.y) { drawLayer(backdrop) } })
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (supportsBlur) .62f else .93f),
                Color(0xFFF0F5FA).copy(alpha = if (supportsBlur) .38f else .92f)
            ))))
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                drawRoundRect(Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .15f), Color.White.copy(alpha = .65f))),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(stroke))
            }
        }
        content()
    }
}

@Composable
private fun NavigationItem(index: Int, title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) Pink else Color(0xFF667786), label = "navigation tint")
    Column(modifier.fillMaxHeight().clip(CircleShape).selectable(selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NavIcon(index, color)
        Spacer(Modifier.height(3.dp))
        Text(title, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun NavIcon(index: Int, tint: Color) {
    Canvas(Modifier.size(23.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke.width, StrokeCap.Round)
        when (index) {
            0 -> {
                val path = Path().apply { moveTo(3 * unit, 10 * unit); lineTo(12 * unit, 3 * unit); lineTo(21 * unit, 10 * unit); lineTo(21 * unit, 20 * unit); lineTo(15 * unit, 20 * unit); lineTo(15 * unit, 14 * unit); lineTo(9 * unit, 14 * unit); lineTo(9 * unit, 20 * unit); lineTo(3 * unit, 20 * unit); close() }
                drawPath(path, tint, style = stroke)
            }
            1 -> {
                drawRoundRect(tint, Offset(3 * unit, 6 * unit), androidx.compose.ui.geometry.Size(18 * unit, 15 * unit), androidx.compose.ui.geometry.CornerRadius(4 * unit), style = stroke)
                line(8f, 2f, 10f, 6f); line(16f, 2f, 14f, 6f)
                line(8f, 12f, 8f, 15f); line(16f, 12f, 16f, 15f)
            }
            2 -> {
                drawRoundRect(tint, Offset(3 * unit, 7 * unit), androidx.compose.ui.geometry.Size(18 * unit, 14 * unit), androidx.compose.ui.geometry.CornerRadius(3 * unit), style = stroke)
                line(7f, 3f, 17f, 3f)
                val path = Path().apply { moveTo(10 * unit, 11 * unit); lineTo(15 * unit, 14 * unit); lineTo(10 * unit, 17 * unit); close() }
                drawPath(path, tint, style = stroke)
            }
            else -> {
                drawCircle(tint, 4 * unit, Offset(12 * unit, 7 * unit), style = stroke)
                val path = Path().apply { moveTo(4 * unit, 21 * unit); cubicTo(4 * unit, 12 * unit, 20 * unit, 12 * unit, 20 * unit, 21 * unit) }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BiliBoxPreview() { BiliBoxTheme { BiliBoxApp() } }
