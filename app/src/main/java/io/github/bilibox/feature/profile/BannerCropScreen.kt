package io.github.bilibox.feature.profile

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.profile.ProfileBannerStore
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CropAspect = 16f / 9f
private const val MinZoom = 1f
private const val MaxZoom = 4f
private val CropBackground = Color(0xFF08080C)

/**
 * 头图裁剪页：图片在 16:9 取景框内拖动 / 双指缩放，确认后才生成并保存头图。
 *
 * 源图由调用方持有；离开组合时在这里回收。保存失败会保留当前裁剪状态并提示重试。
 */
@Composable
internal fun BannerCropScreen(
    source: Bitmap,
    store: ProfileBannerStore,
    onCancel: () -> Unit,
    onSaved: (Bitmap) -> Unit,
) {
    val image = remember(source) { source.asImageBitmap() }
    val accent = LocalAccentColor.current
    val scope = rememberCoroutineScope()
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var viewport by remember(source) { mutableStateOf(IntSize.Zero) }
    var applying by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }

    // 源图只在裁剪页生命周期内使用；取消、保存完成或整页退出时统一回收。
    DisposableEffect(source) {
        onDispose { source.recycle() }
    }

    fun applyCrop() {
        if (applying || viewport.width <= 0 || viewport.height <= 0) return
        applying = true
        error = null
        val crop = bannerCropRect(
            sourceWidth = source.width,
            sourceHeight = source.height,
            viewportWidth = viewport.width.toFloat(),
            viewportHeight = viewport.height.toFloat(),
            zoom = zoom,
            offsetX = offset.x,
            offsetY = offset.y,
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) { store.saveCrop(source, crop) }
            applying = false
            if (result != null) onSaved(result) else error = "裁剪保存失败，请重试"
        }
    }

    // 处理中也保持消费返回键，避免后退直接退出整个应用。
    BackHandler {
        if (!applying) onCancel()
    }

    // 尺寸变化（如旋转）后重新限制位移，避免图片被拖出取景框。
    LaunchedEffect(viewport) {
        if (viewport.width <= 0 || viewport.height <= 0) return@LaunchedEffect
        val (x, y) = clampBannerOffset(
            offsetX = offset.x,
            offsetY = offset.y,
            zoom = zoom,
            sourceWidth = source.width,
            sourceHeight = source.height,
            viewportWidth = viewport.width.toFloat(),
            viewportHeight = viewport.height.toFloat(),
        )
        offset = Offset(x, y)
    }

    Column(Modifier.fillMaxSize().background(CropBackground)) {
        CropTopBar(
            enabled = !applying,
            accent = accent,
            onBack = onCancel,
        )

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            val frameWidth = minOf(maxWidth, maxHeight * CropAspect)
            Box(
                Modifier.align(Alignment.Center)
                    .width(frameWidth)
                    .aspectRatio(CropAspect)
                    .shadow(
                        elevation = 28.dp,
                        shape = RectangleShape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clipToBounds()
                    .background(Color.Black)
                    .onSizeChanged { viewport = it }
            ) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(source, viewport) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            if (applying) return@detectTransformGestures
                            if (size.width <= 0 || size.height <= 0) return@detectTransformGestures
                            val oldZoom = zoom
                            val nextZoom = (oldZoom * gestureZoom).coerceIn(MinZoom, MaxZoom)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val pivot = centroid - center
                            val raw = pivot + (offset - pivot) * (nextZoom / oldZoom) + pan
                            val (x, y) = clampBannerOffset(
                                offsetX = raw.x,
                                offsetY = raw.y,
                                zoom = nextZoom,
                                sourceWidth = source.width,
                                sourceHeight = source.height,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                            )
                            zoom = nextZoom
                            offset = Offset(x, y)
                        }
                    }
                ) {
                    val scale = bannerCoverScale(
                        sourceWidth = source.width,
                        sourceHeight = source.height,
                        viewportWidth = size.width,
                        viewportHeight = size.height,
                    ) * zoom
                    val displayWidth = source.width * scale
                    val displayHeight = source.height * scale
                    val left = (size.width - displayWidth) / 2f + offset.x
                    val top = (size.height - displayHeight) / 2f + offset.y
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )

                    // 三分线帮助判断构图，边框与预览页保持一致的低调白色。
                    val grid = Color.White.copy(alpha = .22f)
                    val stroke = 1.dp.toPx()
                    drawLine(grid, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
                    drawLine(grid, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
                    drawLine(grid, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), stroke)
                    drawLine(grid, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), stroke)
                    drawRect(
                        color = Color.White.copy(alpha = .32f),
                        style = Stroke(stroke),
                    )
                }
            }
        }

        CropBottomBar(
            accent = accent,
            applying = applying,
            error = error,
            onCancel = onCancel,
            onApply = ::applyCrop,
        )
    }
}

@Composable
private fun CropTopBar(
    enabled: Boolean,
    accent: Color,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "取消裁剪" },
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(
                icon = AppIcon.BACK,
                tint = Color.White.copy(alpha = if (enabled) .92f else .4f),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "裁剪头图",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "16:9",
            color = accent.copy(alpha = .9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun CropBottomBar(
    accent: Color,
    applying: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error != null) {
            Text(
                text = error,
                color = Color(0xFFFF9DB1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text(
                text = "拖动调整位置 · 双指缩放",
                color = Color.White.copy(alpha = .55f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CropActionButton(
                label = "取消",
                accent = accent,
                primary = false,
                enabled = !applying,
                onClick = onCancel,
            )
            CropActionButton(
                label = if (applying) "处理中" else "完成",
                accent = accent,
                primary = true,
                enabled = !applying,
                loading = applying,
                onClick = onApply,
            )
        }
    }
}

@Composable
private fun CropActionButton(
    label: String,
    accent: Color,
    primary: Boolean,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .94f else 1f,
        animationSpec = tween(120),
        label = "cropButtonScale",
    )
    val shape = CircleShape
    Row(
        Modifier.height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled || loading) 1f else .55f
            }
            .then(
                if (primary) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = accent.copy(alpha = .5f),
                        spotColor = accent.copy(alpha = .6f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(if (primary) accent else Color.White.copy(alpha = .1f))
            .border(
                width = 1.dp,
                color = if (primary) Color.Transparent else Color.White.copy(alpha = .15f),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (primary) 24.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else if (primary) {
            AppGlyphIcon(
                icon = AppIcon.CHECK,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
                strokeWidth = 1.9f,
            )
        }
        Text(
            text = label,
            color = if (primary) Color.White else Color.White.copy(alpha = .9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )
    }
}
