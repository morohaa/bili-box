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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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


@Composable
internal fun SearchLoading(darkMode: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text("正在搜索视频…", color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun SearchError(message: String, darkMode: Boolean = false, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暂时没有搜到结果", color = if (darkMode) Color(0xFFE7E9ED) else Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("重试", color = Pink) }
    }
}

@Composable
internal fun SearchResults(
    state: SearchState.Success,
    darkMode: Boolean = false,
    gridLayout: Boolean = false,
    onVideoClick: (SearchVideo) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    SearchResultBar(
        count = if (state.total > 0) state.total else state.items.size,
        darkMode = darkMode,
    )
    if (state.items.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        EmptyCard("没有找到视频", "换个关键词再试试，或调整筛选条件。", darkMode)
    } else if (gridLayout) {
        state.items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { video ->
                    SearchGridCard(video, darkMode, Modifier.weight(1f)) { onVideoClick(video) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        state.items.forEach { video ->
            SearchResultCard(video, darkMode, Modifier.offset(x = (-4).dp)) { onVideoClick(video) }
            HorizontalDivider(color = if (darkMode) Color(0xFF30343A) else Color(0xFFDDE3E8), thickness = 1.dp)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onPageChange(state.page - 1) }, enabled = state.page > 1) {
            Text("上一页", color = if (state.page > 1) Pink else Muted)
        }
        Text("第 ${state.page} 页", color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { onPageChange(state.page + 1) }, enabled = state.hasMore) {
            Text("下一页", color = if (state.hasMore) Pink else Muted)
        }
    }
}

@Composable
private fun SearchResultBar(count: Int, darkMode: Boolean) {
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted
    val strong = if (darkMode) Color(0xFFE7E9ED) else Ink
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("找到 ") }
            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(count.toString()) }
            withStyle(SpanStyle(color = muted)) { append(" 个视频") }
        },
        fontSize = 12.sp,
        modifier = Modifier.offset(x = (-2).dp).padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SearchResultCard(video: SearchVideo, darkMode: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(144.dp).height(90.dp).clip(RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            RemoteCover(video.coverUrl, Modifier.fillMaxSize())
            Text(
                formatSearchDuration(video.duration),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .padding(end = 5.dp, bottom = 5.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(90.dp)) {
            Text(
                video.title,
                color = if (darkMode) Color(0xFFE7E9ED) else Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 17.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                AuthorRow(video.author, darkMode)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    VideoMeta(icon = "play", value = video.playCount, darkMode = darkMode)
                    VideoMeta(icon = "comment", value = video.commentCount.toString(), darkMode = darkMode)
                }
            }
        }
    }
}

@Composable
private fun SearchGridCard(video: SearchVideo, darkMode: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(bottom = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            RemoteCover(video.coverUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
            Text(
                formatSearchDuration(video.duration),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .padding(end = 5.dp, bottom = 5.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            video.title,
            color = if (darkMode) Color(0xFFE7E9ED) else Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        AuthorRow(video.author, darkMode)
        Spacer(Modifier.height(5.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            VideoMeta(icon = "play", value = video.playCount, darkMode = darkMode)
            VideoMeta(icon = "comment", value = video.commentCount.toString(), darkMode = darkMode)
        }
    }
}

@Composable
private fun AuthorRow(author: String, darkMode: Boolean) {
    val muted = if (darkMode) Color(0xFF9BA3AE) else Muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.padding(start = 2.dp)
                .size(width = 14.dp, height = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Transparent)
                .border(0.6.dp, muted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("UP", color = muted, fontSize = 6.sp, lineHeight = 7.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(4.dp))
        Text(author, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VideoMeta(icon: String, value: String, darkMode: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
        VideoMetaIcon(kind = icon, modifier = Modifier.size(width = 16.dp, height = 13.dp), tint = if (darkMode) Color(0xFF9BA3AE) else Muted)
        Spacer(Modifier.width(2.dp))
        Text(value, color = if (darkMode) Color(0xFF9BA3AE) else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun VideoMetaIcon(kind: String, modifier: Modifier = Modifier, tint: Color = Muted) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * .065f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = size.width * .14f
        val right = size.width * .86f
        val top = size.height * .14f
        val bottom = size.height * .86f
        when (kind) {
            "play" -> {
                drawRoundRect(tint, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .18f), style = stroke)
                val path = Path().apply {
                    moveTo(size.width * .43f, size.height * .31f)
                    lineTo(size.width * .67f, size.height * .5f)
                    lineTo(size.width * .43f, size.height * .69f)
                    close()
                }
                drawPath(path, tint)
            }
            "comment" -> {
                drawRoundRect(tint, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .16f), style = stroke)
                drawCircle(tint, size.minDimension * .055f, Offset(size.width * .31f, size.height * .4f))
                drawLine(tint, Offset(size.width * .43f, size.height * .4f), Offset(size.width * .7f, size.height * .4f), strokeWidth)
                drawCircle(tint, size.minDimension * .055f, Offset(size.width * .31f, size.height * .62f))
                drawLine(tint, Offset(size.width * .43f, size.height * .62f), Offset(size.width * .62f, size.height * .62f), strokeWidth)
            }
            "date" -> {
                drawRoundRect(tint, Offset(left, top + size.height * .08f),
                    androidx.compose.ui.geometry.Size(right - left, bottom - top - size.height * .08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .12f), style = stroke)
                drawLine(tint, Offset(left, size.height * .38f), Offset(right, size.height * .38f), strokeWidth)
                drawLine(tint, Offset(size.width * .34f, size.height * .08f), Offset(size.width * .34f, size.height * .3f), strokeWidth)
                drawLine(tint, Offset(size.width * .66f, size.height * .08f), Offset(size.width * .66f, size.height * .3f), strokeWidth)
            }
        }
    }
}

@Composable
internal fun RemoteCover(
    url: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                try { connection.inputStream.use(BitmapFactory::decodeStream) } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(shape).background(Color(0xFFE7EDF2)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (bitmap == null) Text("B", color = Pink.copy(alpha = .75f), fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

internal fun toSearchVideo(item: VideoSearchItem): SearchVideo {
    val cleanTitle = item.title.replace(Regex("<[^>]*>"), "")
    return SearchVideo(
        title = android.text.Html.fromHtml(cleanTitle, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
        author = item.author,
        playCount = formatPlayCount(item.play),
        commentCount = item.commentCount,
        publishTime = item.publishTime,
        duration = item.duration,
        bvid = item.bvid,
        coverUrl = when {
            item.pic.startsWith("//") -> "https:${item.pic}"
            item.pic.startsWith("http://") -> item.pic.replaceFirst("http://", "https://")
            else -> item.pic
        },
    )
}

private fun formatPlayCount(raw: String): String {
    val normalized = raw.trim().replace(",", "")
    val numeric = when {
        normalized.endsWith("万") -> normalized.removeSuffix("万").toDoubleOrNull()?.times(10_000.0)
        else -> normalized.toDoubleOrNull()
    } ?: return raw.ifBlank { "--" }
    if (numeric <= 10_000) return numeric.toLong().toString()
    val tenThousands = numeric / 10_000.0
    val formatted = String.format(Locale.US, "%.1f", tenThousands).trimEnd('0').trimEnd('.')
    return "$formatted 万".replace(" ", "")
}

private fun formatPublishDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "--"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}
