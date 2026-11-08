package io.github.bilibox.feature.appearance

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.bilibox.core.ui.component.WallpaperEditorChrome
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax
import io.github.bilibox.core.ui.theme.homeBackgroundScrim
import io.github.bilibox.core.util.saveImageToGallery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自定义首页背景的壁纸预览：整屏铺图，顶栏可换图 / 关闭，底部面板可调暗化与模糊。
 *
 * 只有点「应用」才会把当前图片与调节值交给调用方落盘；直接关闭等于放弃这次选择。
 * 「下载壁纸」抓取的是画面层（不含浮层），与首页实际铺法一致。
 */
@Composable
internal fun WallpaperPreviewOverlay(
    image: ImageBitmap,
    accent: Color,
    darkMode: Boolean,
    initialDarkness: Int,
    initialBlur: Int,
    toastText: String,
    toastVisible: Boolean,
    onShowToast: (String) -> Unit,
    onClose: () -> Unit,
    onReplace: () -> Unit,
    onApply: (ImageBitmap, Int, Int) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val captureLayer = rememberGraphicsLayer()

    var darkness by remember(image) {
        mutableIntStateOf(initialDarkness.coerceIn(0, WallpaperDarknessMax))
    }
    var blur by remember(image) {
        mutableIntStateOf(initialBlur.coerceIn(0, WallpaperBlurMax))
    }
    var saving by remember { mutableStateOf(false) }
    var saveRequest by remember { mutableIntStateOf(0) }

    // 预览是深色整屏，状态栏与导航栏都换成浅色图标；退出后按外观页当前的明暗恢复。
    DisposableEffect(view, darkMode) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkMode
            controller?.isAppearanceLightNavigationBars = !darkMode
        }
    }

    val savePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveRequest++ else onShowToast("需要存储权限才能保存壁纸")
    }

    val saveAction: () -> Unit = {
        if (!saving) {
            if (Build.VERSION.SDK_INT == 28 && context.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                saveRequest++
            }
        }
    }

    LaunchedEffect(saveRequest) {
        if (saveRequest == 0) return@LaunchedEffect
        saving = true
        try {
            val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
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
            onShowToast(if (saved) "壁纸已保存" else "保存失败，请重试")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onShowToast("保存失败，请重试")
        } finally {
            saving = false
        }
    }

    val blurPx = blur.toFloat()
    val canBlur = Build.VERSION.SDK_INT >= 31

    WallpaperEditorChrome(
        title = "壁纸预览",
        accent = accent,
        darkness = darkness,
        blur = blur,
        onDarknessChange = { darkness = it },
        onBlurChange = { blur = it },
        applyLabel = "应用",
        downloadLabel = "下载壁纸",
        saving = saving,
        onClose = onClose,
        onChangeImage = onReplace,
        onDownload = saveAction,
        onReset = onReset,
        onApply = { onApply(image, darkness, blur) },
        onTip = onShowToast,
        toastText = toastText,
        toastVisible = toastVisible,
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                captureLayer.record { this@drawWithContent.drawContent() }
                drawLayer(captureLayer)
            }
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (canBlur && blurPx > 0f) {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                        val scale = 1f + blur / 200f
                        scaleX = scale
                        scaleY = scale
                    }
                },
            )
            // 与首页实际渲染保持一致：固定可读性蒙层 + 用户选择的暗化。
            Box(Modifier.fillMaxSize().background(homeBackgroundScrim(darkMode)))
            if (darkness > 0) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = darkness / 100f))
                )
            }
        }
    }
}
