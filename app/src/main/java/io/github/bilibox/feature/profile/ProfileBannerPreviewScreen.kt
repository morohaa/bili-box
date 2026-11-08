package io.github.bilibox.feature.profile

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.core.util.saveImageToGallery
import io.github.bilibox.data.profile.ProfileBannerStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PreviewAspect = 16f / 9f
private val PreviewEnterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val PreviewBackground = Color(0xFF08080C)

/** 16:9 预览图在可用区域内取最大宽度；横屏时改为受高度限制。 */
internal fun profileBannerPreviewWidth(availableWidth: Dp, availableHeight: Dp): Dp = minOf(availableWidth, availableHeight * PreviewAspect)

/**
 * 头图全屏预览：沉浸黑底、环境光、16:9 画面与「默认 / 保存 / 更换」操作栏。
 *
 * 点按画面或空白处退出；更换后的图片由 [ProfileBannerStore] 持久化，并回传给个人页。
 */
@Composable
internal fun ProfileBannerPreviewScreen(
    avatar: ImageBitmap?,
    customBanner: ImageBitmap?,
    store: ProfileBannerStore,
    onBannerChange: (ImageBitmap?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val view = LocalView.current

    var toastText by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var toastTick by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveRequest by remember { mutableIntStateOf(0) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var loadingSource by remember { mutableStateOf(false) }
    val captureLayer = rememberGraphicsLayer()

    fun showToast(text: String) {
        toastText = text
        toastVisible = true
        toastTick++
    }

    LaunchedEffect(toastTick) {
        if (toastTick == 0) return@LaunchedEffect
        delay(1800)
        toastVisible = false
    }

    fun saveCurrentBanner() {
        if (saving) return
        saveRequest++
    }

    val savePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveCurrentBanner() else showToast("需要存储权限才能保存头图")
    }

    val saveAction: () -> Unit = {
        if (Build.VERSION.SDK_INT == 28 && context.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveCurrentBanner()
        }
    }

    val pickBanner = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingSource = true
        scope.launch {
            try {
                val source = withContext(Dispatchers.IO) { store.loadSource(uri) }
                if (source == null) {
                    showToast("图片读取失败，请换一张试试")
                } else {
                    cropSource = source
                }
            } finally {
                loadingSource = false
            }
        }
    }

    LaunchedEffect(saveRequest) {
        if (saveRequest == 0) return@LaunchedEffect
        saving = true
        try {
            val image = captureLayer.toImageBitmap()
            val bitmap = image.asAndroidBitmap()
            val saved = try {
                withContext(Dispatchers.IO) { saveImageToGallery(context, bitmap) }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            } finally {
                bitmap.recycle()
            }
            showToast(if (saved) "已保存到相册" else "保存失败，请稍后重试")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showToast("保存失败，请稍后重试")
        } finally {
            saving = false
        }
    }

    // 沉浸式预览：进入时隐藏系统栏，退出或离开组合时恢复。
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 选图后先进入裁剪页；确认后再回到预览并展示新头图。
    val source = cropSource
    if (source != null) {
        BannerCropScreen(
            source = source,
            store = store,
            onCancel = { cropSource = null },
            onSaved = { bitmap ->
                cropSource = null
                onBannerChange(bitmap.asImageBitmap())
                showToast("已更换头图")
            },
        )
        return
    }

    BackHandler(onBack = onClose)

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(360, easing = PreviewEnterEasing))
    }

    val closeInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(PreviewBackground)
            .drawBehind { drawAmbientGlow(accent) }
            .clickable(
                interactionSource = closeInteraction,
                indication = null,
                onClickLabel = "关闭头图预览",
                onClick = onClose,
            )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 竖屏让画面在整个屏幕里居中；横屏时为底部工具栏预留高度，避免遮挡。
            val toolbarReserve = if (maxWidth > maxHeight) 132.dp else 0.dp
            val imageWidth = profileBannerPreviewWidth(
                availableWidth = maxWidth,
                availableHeight = (maxHeight - toolbarReserve).coerceAtLeast(1.dp),
            )
            // 外层单独承担入场缩放，抓图图层只记录未缩放的画面本身。
            Box(
                Modifier.align(Alignment.Center)
                    .offset(y = -(toolbarReserve / 2))
                    .graphicsLayer {
                        alpha = enter.value
                        val scale = 0.95f + 0.05f * enter.value
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Box(
                    Modifier.width(imageWidth)
                        .aspectRatio(PreviewAspect)
                        .shadow(
                            elevation = 28.dp,
                            shape = RectangleShape,
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .drawWithContent {
                            captureLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(captureLayer)
                        }
                ) {
                    BannerArtwork(avatar, customBanner, Modifier.fillMaxSize())
                }
            }
        }
        PreviewToolbar(
            modifier = Modifier.align(Alignment.BottomCenter),
            saving = saving,
            loadingSource = loadingSource,
            accent = accent,
            alpha = enter.value,
            onRestore = {
                if (customBanner == null) {
                    showToast("当前已是默认头图")
                } else {
                    store.clear()
                    onBannerChange(null)
                    showToast("已恢复默认头图")
                }
            },
            onSave = saveAction,
            onReplace = {
                pickBanner.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )

        Box(
            Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .1f))
                .border(1.dp, Color.White.copy(alpha = .15f), CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "关闭头图预览" },
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.CLOSE, Color.White.copy(alpha = .92f), Modifier.size(18.dp))
        }

        AnimatedVisibility(
            visible = toastVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 108.dp),
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier.clip(CircleShape)
                    .background(Color(0xE61A1B1F))
                    .border(1.dp, Color.White.copy(alpha = .12f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PreviewToolbar(
    modifier: Modifier = Modifier,
    saving: Boolean,
    loadingSource: Boolean,
    accent: Color,
    alpha: Float,
    onRestore: () -> Unit,
    onSave: () -> Unit,
    onReplace: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 64.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewToolButton(
            label = "默认",
            icon = AppIcon.ROTATE_LEFT,
            accent = accent,
            primary = false,
            onClick = onRestore,
        )
        PreviewToolButton(
            label = if (saving) "保存中" else "保存",
            icon = AppIcon.DOWNLOAD,
            accent = accent,
            primary = true,
            enabled = !saving,
            onClick = onSave,
        )
        PreviewToolButton(
            label = if (loadingSource) "读取中" else "更换",
            icon = AppIcon.IMAGE,
            accent = accent,
            primary = false,
            enabled = !loadingSource,
            onClick = onReplace,
        )
    }
}

@Composable
private fun PreviewToolButton(
    label: String,
    icon: AppIcon,
    accent: Color,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .94f else 1f,
        animationSpec = tween(120),
        label = "bannerToolScale",
    )
    val shape = CircleShape
    Row(
        Modifier.height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else .62f
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
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(
            icon = icon,
            tint = if (primary) Color.White else Color.White.copy(alpha = .9f),
            modifier = Modifier.size(15.dp),
            strokeWidth = 1.8f,
        )
        Text(
            text = label,
            color = if (primary) Color.White else Color.White.copy(alpha = .9f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

/** 环境光取自主题强调色，用两团大半径径向渐变替代模糊层，低端机也不掉帧。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAmbientGlow(accent: Color) {
    val cool = lerp(accent, Color.White, .45f)
    val firstCenter = Offset(size.width * .28f, size.height * .32f)
    val secondCenter = Offset(size.width * .74f, size.height * .72f)
    val firstRadius = size.maxDimension * .62f
    val secondRadius = size.maxDimension * .56f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = .18f), Color.Transparent),
            center = firstCenter,
            radius = firstRadius,
        ),
        radius = firstRadius,
        center = firstCenter,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(cool.copy(alpha = .1f), Color.Transparent),
            center = secondCenter,
            radius = secondRadius,
        ),
        radius = secondRadius,
        center = secondCenter,
    )
}
