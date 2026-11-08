package io.github.bilibox.feature.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.WallpaperEditorChrome
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax
import io.github.bilibox.core.util.saveImageToGallery
import io.github.bilibox.data.profile.BannerAdjustments
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
 * 头图全屏预览：沉浸黑底、环境光里居中一张 16:9 画面。
 *
 * 顶栏、可展开面板与「应用 / 更多」按钮复用壁纸编辑器；暗化与模糊会叠加到
 * 头图画面并回传持久化，保存到相册只抓取 16:9 画面本身。
 */
@Composable
internal fun ProfileBannerPreviewScreen(
    avatar: ImageBitmap?,
    customBanner: ImageBitmap?,
    adjustments: BannerAdjustments,
    store: ProfileBannerStore,
    onBannerChange: (ImageBitmap?) -> Unit,
    onAdjustmentsChange: (BannerAdjustments) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val captureLayer = rememberGraphicsLayer()

    var toastText by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var toastTick by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveRequest by remember { mutableIntStateOf(0) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var loadingSource by remember { mutableStateOf(false) }
    var darkness by remember(adjustments) {
        mutableIntStateOf(adjustments.darkness.coerceIn(0, WallpaperDarknessMax))
    }
    var blur by remember(adjustments) {
        mutableIntStateOf(adjustments.blur.coerceIn(0, WallpaperBlurMax))
    }
    var panelOpen by remember { mutableStateOf(false) }
    val panelReserve by animateDpAsState(
        targetValue = if (panelOpen) 380.dp else 0.dp,
        animationSpec = tween(300, easing = PreviewEnterEasing),
        label = "bannerPanelReserve",
    )

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

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(360, easing = PreviewEnterEasing))
    }

    Box(
        Modifier.fillMaxSize()
            .background(PreviewBackground)
            .drawBehind { drawAmbientGlow(accent) }
            .clipToBounds()
    ) {
        WallpaperEditorChrome(
            title = "头图预览",
            accent = accent,
            darkness = darkness,
            blur = blur,
            onDarknessChange = { darkness = it },
            onBlurChange = { blur = it },
            applyLabel = "应用",
            downloadLabel = "保存头图",
            saving = saving,
            onClose = onClose,
            onChangeImage = {
                pickBanner.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDownload = saveAction,
            onReset = {
                if (customBanner == null && darkness == 0 && blur == 0) {
                    showToast("当前已是默认头图")
                } else {
                    store.clear()
                    onBannerChange(null)
                    onAdjustmentsChange(BannerAdjustments())
                    showToast("已恢复默认头图")
                }
            },
            onApply = {
                onAdjustmentsChange(BannerAdjustments(darkness, blur).clamped())
                showToast("已应用头图设置")
            },
            onTip = { showToast(it) },
            toastText = toastText,
            toastVisible = toastVisible,
            onPanelOpenChange = { panelOpen = it },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 面板展开时给底部操作区让位，16:9 画面顺手向上收，不会被半透明面板压住。
                val toolbarReserve = if (maxWidth > maxHeight) 132.dp else panelReserve
                val imageWidth = profileBannerPreviewWidth(
                    availableWidth = maxWidth,
                    availableHeight = (maxHeight - toolbarReserve).coerceAtLeast(1.dp),
                )
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
                        BannerArtwork(
                            avatar = avatar,
                            customBanner = customBanner,
                            modifier = Modifier.fillMaxSize(),
                            adjustments = BannerAdjustments(darkness, blur).clamped(),
                        )
                    }
                }
            }
        }

        if (loadingSource) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.clip(CircleShape).background(Color(0xE61A1B1F))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text("正在读取图片…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
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
