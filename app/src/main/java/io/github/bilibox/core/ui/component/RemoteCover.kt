package io.github.bilibox.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.RemoteImageCache

/**
 * 远程封面。图片未就绪（加载中或加载失败）时显示 [placeholder]，
 * 默认是粉色的「B」字标；搜索结果卡会传入完整的封面插画占位。
 */
@Composable
internal fun RemoteCover(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    imageCache: RemoteImageCache = RemoteImageCache.shared,
    placeholder: @Composable BoxScope.() -> Unit = { BrandLetterPlaceholder() },
) {
    var bitmap by remember(url, imageCache) { mutableStateOf(imageCache.cached(url)) }
    LaunchedEffect(url, imageCache) {
        if (bitmap == null) bitmap = imageCache.load(url)
    }
    Box(modifier.clip(shape).background(Color(0xFFE7EDF2)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (bitmap == null) placeholder()
    }
}

/** 品牌色字母占位，供头像等小尺寸封面使用。 */
@Composable
private fun BrandLetterPlaceholder() {
    Text("B", color = Pink.copy(alpha = .75f), fontSize = 24.sp, fontWeight = FontWeight.Black)
}
