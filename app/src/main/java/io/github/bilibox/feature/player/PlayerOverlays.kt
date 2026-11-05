package io.github.bilibox.feature.player

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun ScreenshotPreviewCard(
    frame: Bitmap,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            scaleIn(
                initialScale = .6f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ),
        exit = fadeOut(tween(160)) +
            scaleOut(
                targetScale = .92f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(180, easing = FastOutSlowInEasing),
            ),
        modifier = modifier,
    ) {
        Column(
            Modifier.width(96.dp)
                .shadow(10.dp, RoundedCornerShape(7.dp))
                .background(Color.White, RoundedCornerShape(7.dp))
                .padding(2.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Box {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "已保存截图预览",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("已保存到相册", color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun PlayerDetailsOverlay(visible: Boolean, overlay: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(140)),
    ) {
        overlay()
    }
}

@Composable
internal fun BiliLoadingIndicator(message: String) {
    val transition = rememberInfiniteTransition(label = "播放器缓冲")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = CubicBezierEasing(.45f, .05f, .55f, .95f))),
        label = "粉色缓冲环旋转"
    )
    val televisionOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "小电视浮动"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp).rotate(rotation)) {
                val u = size.width / 50f
                val diameter = androidx.compose.ui.geometry.Size(40f * u, 40f * u)
                drawCircle(
                    Color.White.copy(alpha = .12f),
                    radius = 20f * u,
                    style = Stroke(3.2f * u)
                )
                drawArc(
                    OnlinePink,
                    startAngle = -90f,
                    sweepAngle = 244f,
                    useCenter = false,
                    topLeft = Offset(5f * u, 5f * u),
                    size = diameter,
                    style = Stroke(3.2f * u, cap = StrokeCap.Round)
                )
            }
            Canvas(Modifier.offset(y = televisionOffset.dp).size(20.dp)) {
                val u = size.width / 24f
                val tint = Color.White.copy(alpha = .9f)
                val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(
                    Path().apply {
                        moveTo(8f * u, 3f * u)
                        lineTo(11f * u, 6f * u)
                        lineTo(14f * u, 3f * u)
                    },
                    tint,
                    style = stroke
                )
                drawRoundRect(
                    tint,
                    Offset(4f * u, 6f * u),
                    androidx.compose.ui.geometry.Size(16f * u, 13f * u),
                    androidx.compose.ui.geometry.CornerRadius(3.5f * u),
                    style = stroke
                )
                listOf(9f, 15f).forEach { x ->
                    drawLine(tint, Offset(x * u, 11f * u), Offset(x * u, 13f * u), 1.8f * u, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = Color.White.copy(alpha = .9f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = .3.sp
        )
    }
}
