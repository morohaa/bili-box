package com.example.bilibox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.Size
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bilibox.ui.theme.BiliBoxTheme
import com.example.bilibox.bili.BiliClient
import com.example.bilibox.bili.Page
import com.example.bilibox.bili.BiliPlayback
import com.example.bilibox.bili.BiliQn
import com.example.bilibox.bili.VideoInfo
import com.example.bilibox.bili.VideoSearchItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    Text("bili", color =   HomePink, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-1).sp)
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
                    drawLine(tint,
                        Offset(12f * unit + kotlin.math.cos(angle).toFloat() * inner, 12f * unit + kotlin.math.sin(angle).toFloat() * inner),
                        Offset(12f * unit + kotlin.math.cos(angle).toFloat() * outer, 12f * unit + kotlin.math.sin(angle).toFloat() * outer),
                        stroke.width, StrokeCap.Round)
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
        Text(title, color = if (darkMode) Color(0xFFE7E9ED) else HomeInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = if (darkMode) Color(0xFF9BA3AE) else HomeMuted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

/** A single three-item capsule: (xxx). */
@Composable
internal fun LiquidNavigation(selected: Int, onSelect: (Int) -> Unit, backdrop: GraphicsLayer, darkMode: Boolean = false, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val itemWidth = (maxWidth - 12.dp) / 3
        val indicatorOffset by animateDpAsState(itemWidth * selected.coerceIn(0, 2), label = "glass selection position")
        GlassSurface(backdrop, darkMode, Modifier.fillMaxWidth().height(68.dp)) {
            Box(Modifier.fillMaxSize().padding(6.dp)) {
                val indicatorColors = if (darkMode) {
                    listOf(Color(0xFF464C55), Color(0xFF343A43))
                } else {
                    listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .38f))
                }
                Box(Modifier.offset(x = indicatorOffset).width(itemWidth).fillMaxHeight()
                    .background(Brush.verticalGradient(indicatorColors), CircleShape)
                    .then(Modifier.clip(CircleShape)))
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    listOf("动态", "视频", "我的").forEachIndexed { index, title ->
                        NavigationItem(index, title, selected == index, darkMode, { onSelect(index) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSurface(backdrop: GraphicsLayer, darkMode: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
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
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(if (darkMode) {
                listOf(Color(0xFF2B3038).copy(alpha = if (supportsBlur) .78f else .98f), Color(0xFF20242A).copy(alpha = if (supportsBlur) .82f else .98f))
            } else {
                listOf(Color.White.copy(alpha = if (supportsBlur) .62f else .93f), Color(0xFFF0F5FA).copy(alpha = if (supportsBlur) .38f else .92f))
            })))
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                val edge = if (darkMode) Color(0xFF58616D) else Color.White
                drawRoundRect(Brush.linearGradient(listOf(edge, edge.copy(alpha = .15f), edge.copy(alpha = .65f))),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(stroke))
            }
        }
        content()
    }
}

@Composable
private fun NavigationItem(index: Int, title: String, selected: Boolean, darkMode: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) HomePink else if (darkMode) Color(0xFFB4BDC8) else Color(0xFF667786), label = "navigation tint")
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
                drawRoundRect(tint, Offset(3 * unit, 6 * unit), androidx.compose.ui.geometry.Size(18 * unit, 15 * unit), androidx.compose.ui.geometry.CornerRadius(4 * unit), style = stroke)
                line(8f, 2f, 10f, 6f); line(16f, 2f, 14f, 6f)
                line(8f, 12f, 8f, 15f); line(16f, 12f, 16f, 15f)
            }
            1 -> {
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
