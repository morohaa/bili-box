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
import android.os.SystemClock
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.TextStyle
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
import com.example.bilibox.bili.PlayData
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


@Composable
private fun OnlineVideoDetails(
    video: SearchVideo,
    info: VideoInfo?,
    creatorCard: com.example.bilibox.bili.UserCardData?,
    context: Context,
    selectedPage: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onOpenEpisodes: () -> Unit,
) {
    var following by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var liked by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var disliked by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var coined by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var saved by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }

    val owner = info?.owner
    val ownerName = owner?.name?.takeIf { it.isNotBlank() }
        ?: creatorCard?.card?.name?.takeIf { it.isNotBlank() }
        ?: video.author
    val avatar = normalizeImageUrl(owner?.face.orEmpty().ifBlank { creatorCard?.card?.face.orEmpty() })
    val stat = info?.stat
    val title = info?.title?.takeIf { it.isNotBlank() } ?: video.title
    val publishedAt = info?.pubdate?.takeIf { it > 0 } ?: video.publishTime
    val duration = info?.duration?.takeIf { it > 0 }?.let { formatPlaybackTime(it * 1_000L) }
        ?: video.duration

    Column(Modifier.fillMaxWidth().background(OnlineSurface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteCover(
                avatar,
                Modifier.size(38.dp).border(1.dp, OnlineDivider, CircleShape).padding(1.dp),
                shape = CircleShape
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ownerName, color = OnlinePink, fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val creatorStats = creatorCard?.let {
                    "${formatCompactCount(it.card.fans)} 粉丝    ${it.archiveCount} 视频"
                } ?: "哔哩哔哩 UP 主"
                Text(
                    creatorStats, color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            // A compact visual button with the platform's expanded touch target.
            Box(
                Modifier.height(38.dp).widthIn(min = 72.dp)
                    .semantics { contentDescription = if (following) "取消关注" else "关注 UP 主" }
                    .clickable(role = Role.Button) { following = !following },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    Modifier.height(28.dp)
                        .background(if (following) OnlineDivider else OnlinePink, CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!following) {
                        Canvas(Modifier.size(14.dp)) {
                            val inset = size.width * 5f / 24f
                            drawLine(Color.White, Offset(center.x, inset), Offset(center.x, size.height - inset),
                                1.4.dp.toPx(), StrokeCap.Round)
                            drawLine(Color.White, Offset(inset, center.y), Offset(size.width - inset, center.y),
                                1.4.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    Text(
                        if (following) "已关注" else "关注",
                        color = if (following) OnlineSecondary else Color.White,
                        fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    title, color = OnlineText, fontSize = 15.5.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.padding(top = 2.dp).size(24.dp)
                        .semantics { contentDescription = if (expanded) "收起简介" else "展开简介" },
                    contentAlignment = Alignment.Center
                ) {
                    ChevronDownIcon(Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), OnlineMuted)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OnlineMetric("play", stat?.let { formatCompactCount(it.view) } ?: video.playCount)
                OnlineMetric("comment", (stat?.danmaku ?: video.commentCount).toString())
                Text(
                    formatPublishDateTime(publishedAt), color = OnlineMuted,
                    fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(duration, color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 1)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(top = 10.dp), color = OnlineDivider)
                Text(
                    info?.bvid?.ifBlank { video.bvid } ?: video.bvid,
                    color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                info?.desc?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description, color = OnlineSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            OnlineAction("like", "点赞", formatOptionalCount(stat?.like ?: 0L), liked) {
                liked = !liked
                if (liked) disliked = false
            }
            OnlineAction("dislike", "不喜欢", "", disliked) {
                disliked = !disliked
                if (disliked) liked = false
            }
            OnlineAction("coin", "投币", formatOptionalCount(stat?.coin ?: 0L), coined) { coined = !coined }
            OnlineAction("favorite", "收藏", formatOptionalCount(stat?.favorite ?: 0L), saved) { saved = !saved }
            OnlineAction("share", "分享", "", false) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "$title https://www.bilibili.com/video/${video.bvid}")
                }
                context.startActivity(Intent.createChooser(intent, "分享视频"))
            }
        }
        info?.pages?.takeIf { it.size > 1 }?.let { pages ->
            OnlineEpisodesSection(
                pages = pages,
                selectedPage = selectedPage,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChange = onAutoPlayNextChange,
                onPageSelected = onPageSelected,
                onOpenEpisodes = onOpenEpisodes,
            )
        }
        HorizontalDivider(color = OnlineDivider)
    }
}

/** Horizontal "正片" picker shown on the online video detail page. */
@Composable
internal fun OnlineEpisodesSection(
    pages: List<Page>,
    selectedPage: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onOpenEpisodes: () -> Unit,
) {
    val pageIndex = pages.indexOfFirst { it.page == selectedPage }
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("正片", color = OnlineText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(5.dp))
        Text(
            "(${if (pageIndex >= 0) pageIndex + 1 else selectedPage}/${pages.size})",
            color = OnlineMuted,
            fontSize = 12.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        OnlineAutoPlayChip(autoPlayNext, onAutoPlayNextChange)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onOpenEpisodes)
                .semantics { contentDescription = "查看全部选集" }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共${pages.size}P · 查看全部",
                color = OnlineMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            OnlineChevronRight(color = OnlineMuted, modifier = Modifier.size(12.dp))
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pageIndex.coerceAtLeast(0)
    )
    LaunchedEffect(selectedPage) {
        if (pageIndex >= 0) listState.animateScrollToItem(pageIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(pages, key = { it.cid }) { page ->
            OnlineEpisodeCard(
                page = page,
                selected = page.page == selectedPage,
                onClick = { onPageSelected(page.page) }
            )
        }
    }
}

/** Compact auto-play-next chip used in the "正片" section header. */
@Composable
private fun OnlineAutoPlayChip(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.height(24.dp)
            .clip(CircleShape)
            .background(OnlineDivider)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = "自动连播" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (checked) OnlinePink else Color(0xFFB9BEC5))
        )
        Spacer(Modifier.width(4.dp))
        Text("自动连播", color = OnlineSecondary, fontSize = 10.5.sp, maxLines = 1)
    }
}

@Composable
private fun OnlineChevronRight(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.4.dp.toPx()
        drawLine(
            color,
            Offset(size.width * .38f, size.height * .26f),
            Offset(size.width * .66f, size.height * .5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * .66f, size.height * .5f),
            Offset(size.width * .38f, size.height * .74f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun RowScope.OnlineAction(
    kind: String,
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val iconScale = remember { Animatable(1f) }
    val tint by animateColorAsState(
        targetValue = if (selected) OnlinePink else OnlineSecondary,
        animationSpec = tween(180),
        label = "$kind 按钮颜色"
    )
    Column(
        Modifier.weight(1f).semantics { contentDescription = "$label $value" }
            .clickable(role = Role.Button) {
                onClick()
                scope.launch {
                    iconScale.stop()
                    iconScale.snapTo(1f)
                    iconScale.animateTo(
                        1f,
                        keyframes {
                            durationMillis = 380
                            1f at 0
                            1.26f at 152 using CubicBezierEasing(.34f, 1.56f, .64f, 1f)
                            .92f at 285 using CubicBezierEasing(.34f, 1.56f, .64f, 1f)
                            1f at 380 using CubicBezierEasing(.34f, 1.56f, .64f, 1f)
                        }
                    )
                }
            }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            OnlineActionIcon(
                kind,
                tint,
                Modifier.size(22.dp).graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { label }, color = tint, fontSize = 11.5.sp, lineHeight = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OnlineMetric(kind: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VideoMetaIcon(kind, Modifier.size(14.dp), OnlineMuted)
        Spacer(Modifier.width(4.dp))
        Text(value, color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 1)
    }
}

@Composable
private fun OnlineActionIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    // Use the reference's 24-unit vector shapes to keep all five icons consistent.
    val path = remember(kind) {
        val data = when (kind) {
            "like" -> "M14.6 3.4c-.6-.4-1.4-.2-1.8.4l-3.2 5.4c-.2.3-.5.5-.9.5H5.5C4.1 9.7 3 10.8 3 12.2v6.3C3 19.9 4.1 21 5.5 21h11.3c1.2 0 2.2-.8 2.5-1.9l1.6-6.8c.4-1.6-.8-3.1-2.5-3.1h-3.6l.8-3.8c.2-.8-.2-1.6-1-2zM7 19H5.5c-.3 0-.5-.2-.5-.5v-6.3c0-.3.2-.5.5-.5H7V19z"
            "dislike" -> "M9.4 20.6c.6.4 1.4.2 1.8-.4l3.2-5.4c.2-.3.5-.5.9-.5h3.2c1.4 0 2.5-1.1 2.5-2.5V5.5C21 4.1 19.9 3 18.5 3H7.2C6 3 5 3.8 4.7 4.9L3.1 11.7c-.4 1.6.8 3.1 2.5 3.1h3.6l-.8 3.8c-.2.8.2 1.6 1 2zM17 5h1.5c.3 0 .5.2.5.5v6.3c0 .3-.2.5-.5.5H17V5z"
            "favorite" -> "M12 2.8c.4 0 .8.2 1 .6l2.4 4.9 5.4.8c.9.1 1.2 1.2.6 1.8l-3.9 3.8.9 5.4c.2.9-.8 1.5-1.6 1.1L12 18.7l-4.8 2.5c-.8.4-1.8-.2-1.6-1.1l.9-5.4-3.9-3.8c-.6-.6-.3-1.7.6-1.8l5.4-.8 2.4-4.9c.2-.4.6-.6 1-.6z"
            "share" -> "M13.8 4.2c0-.9 1.1-1.3 1.7-.7l6.2 6.2c.4.4.4 1 0 1.4l-6.2 6.2c-.6.6-1.7.2-1.7-.7v-3.1c-4.8 0-8.2 1.5-10.3 4.7-.4.6-1.4.2-1.3-.5 1.1-6.5 5.2-9.8 11.6-10.2V4.2z"
            else -> ""
        }
        PathParser().parsePathString(data).toPath()
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            if (kind == "coin") {
                drawCircle(tint, 9.2f, Offset(12f, 12f), style = Stroke(2f))
                drawPath(Path().apply {
                    moveTo(8.5f, 9.2f); lineTo(15.5f, 9.2f)
                    moveTo(10f, 9.2f); lineTo(10f, 15.5f)
                    moveTo(14f, 9.2f); lineTo(14f, 15.5f)
                    moveTo(7.8f, 12.2f); lineTo(16.2f, 12.2f)
                }, tint, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, tint)
            }
        }
    }
}

@Composable
private fun ChevronDownIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        drawPath(Path().apply {
            moveTo(size.width * .25f, size.height * .375f)
            lineTo(size.width * .5f, size.height * .625f)
            lineTo(size.width * .75f, size.height * .375f)
        }, tint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun formatOptionalCount(value: Long): String = value.takeIf { it > 0 }?.let(::formatCompactCount).orEmpty()

internal fun formatCompactCount(value: Long): String = when {
    value >= 100_000_000 -> "${String.format(Locale.US, "%.1f", value / 100_000_000.0).trimEnd('0').trimEnd('.')}亿"
    value >= 10_000 -> "${String.format(Locale.US, "%.1f", value / 10_000.0).trimEnd('0').trimEnd('.')}万"
    else -> value.toString()
}

private fun formatPublishDateTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "--"
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}

private fun normalizeImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> url.replaceFirst("http://", "https://")
    else -> url
}

@Composable
internal fun RowScope.VideoAction(index: Int, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (selected) Pink else Color(0xFF62666D).copy(alpha = if (enabled) 1f else .4f)
    Column(
        Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(26.dp)) {
            val u = size.width / 24f
            val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (index) {
                0 -> {
                    drawRoundRect(tint, topLeft = Offset(2*u, 10*u), size = androidx.compose.ui.geometry.Size(4*u, 11*u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u))
                    drawPath(Path().apply {
                        moveTo(8*u, 10*u); lineTo(12*u, 3*u)
                        cubicTo(16*u, 2*u, 15*u, 7*u, 14*u, 9*u)
                        lineTo(20*u, 9*u); quadraticTo(23*u, 9*u, 22*u, 12*u)
                        lineTo(20*u, 20*u); lineTo(8*u, 20*u); close()
                    }, tint)
                }
                1 -> {
                    drawCircle(tint, radius = 10*u, style = stroke)
                    drawLine(tint, Offset(7*u, 8*u), Offset(17*u, 8*u), strokeWidth = 1.8f*u)
                    drawLine(tint, Offset(12*u, 6*u), Offset(12*u, 19*u), strokeWidth = 1.8f*u)
                    drawLine(tint, Offset(8*u, 12*u), Offset(16*u, 12*u), strokeWidth = 1.8f*u)
                }
                2 -> drawPath(Path().apply {
                    for (point in 0..9) {
                        val angle = Math.PI * point / 5 - Math.PI / 2
                        val radius = if (point % 2 == 0) 11*u else 5*u
                        val x = 12*u + kotlin.math.cos(angle).toFloat()*radius
                        val y = 12*u + kotlin.math.sin(angle).toFloat()*radius
                        if (point == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }, tint)
                3 -> drawPath(Path().apply {
                    moveTo(3*u, 21*u); cubicTo(3*u, 11*u, 7*u, 7*u, 15*u, 7*u)
                    lineTo(15*u, 2*u); lineTo(23*u, 11*u); lineTo(15*u, 18*u)
                    lineTo(15*u, 13*u); quadraticTo(7*u, 13*u, 3*u, 21*u); close()
                }, tint)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (selected) Pink else Muted, fontSize = 11.sp)
    }
}


/** Startup gets this long before a non-progressing CDN host is abandoned. */
private const val STARTUP_STALL_TIMEOUT_MS = 8_000L
/** Buffered position must not have grown for this long to count as stalled. */
private const val STARTUP_NO_PROGRESS_MS = 4_000L

@Composable
internal fun BiliVideoDetail(video: SearchVideo, session: BiliSession?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(video.bvid, session?.cookie) {
        BiliClient().also { it.cookieHeader = session?.cookie }
    }
    val exoPlayer = remember(video.bvid, session?.cookie) {
        val dataSourceFactory = DefaultDataSource.Factory(context, BiliPlayback.dataSourceFactory(session?.cookie, video.bvid))
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
    var playbackState by remember(video.bvid, session?.cookie) { mutableStateOf<PlaybackState>(PlaybackState.Loading) }
    var playbackData by remember(video.bvid, session?.cookie) { mutableStateOf<com.example.bilibox.bili.PlayData?>(null) }
    var playbackCid by remember(video.bvid, session?.cookie) { mutableLongStateOf(0L) }
    var playbackCandidateIndex by remember(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    var playbackCandidateCount by remember(video.bvid, session?.cookie) { mutableIntStateOf(1) }
    // Candidate lists depend on the qn they were built with; host rotation must reuse it.
    var playbackCandidateQn by remember(video.bvid, session?.cookie) { mutableIntStateOf(BiliQn.P1080) }
    var playbackRefreshTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var dashFallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var durlFallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var html5FallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var videoInfo by remember(video.bvid, session?.cookie) { mutableStateOf<VideoInfo?>(null) }
    var selectedPage by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(1) }
    var selectedQn by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(BiliQn.P1080) }
    // The quality the server actually started, which can be lower than the requested one.
    var actualQn by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    // Selecting the same quality again must still be able to retry a clamped stream.
    var qualityRequestToken by remember(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    var qualityResumeMs by remember(video.bvid, session?.cookie) { mutableLongStateOf(0L) }
    var qualityResumePlaying by remember(video.bvid, session?.cookie) { mutableStateOf(true) }
    var episodesSheetVisible by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(false) }
    var autoPlayNext by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(true) }
    var episodesInGridView by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(false) }
    var creatorCard by remember(video.bvid, session?.cookie) { mutableStateOf<com.example.bilibox.bili.UserCardData?>(null) }
    // Cache entries belong to one quality, so do not reuse 1080P urls after a quality change.
    val episodePlayCache = remember(video.bvid, session?.cookie, selectedQn) { EpisodePlayCache() }
    var retryToken by remember(video.bvid) { mutableIntStateOf(0) }
    /** Attaches fresh playurl data, resuming where the failed stream stopped. */
    fun attachPlayData(data: com.example.bilibox.bili.PlayData, qn: Int, resumeMs: Long = exoPlayer.currentPosition) {
        playbackData = data
        playbackCandidateIndex = 0
        playbackCandidateQn = qn
        playbackCandidateCount = BiliPlayback.candidateCount(data, preferredQn = qn)
        BiliPlayback.attach(exoPlayer, data, preferredQn = qn, cookie = session?.cookie, bvid = video.bvid, startPositionMs = resumeMs)
    }
    /** Moves to the next CDN host pair of the current playurl response. */
    fun rotateCandidate(): Boolean {
        val data = playbackData ?: return false
        val nextIndex = playbackCandidateIndex + 1
        if (nextIndex >= playbackCandidateCount) return false
        val resumeMs = exoPlayer.currentPosition
        playbackCandidateIndex = nextIndex
        playbackState = PlaybackState.Loading
        BiliPlayback.attach(
            exoPlayer, data, preferredQn = playbackCandidateQn, candidateIndex = nextIndex,
            cookie = session?.cookie, bvid = video.bvid, startPositionMs = resumeMs,
        )
        return true
    }
    suspend fun tryHtml5Fallback(): String? {
        html5FallbackTried = true
        return try {
            val fallback = client.playUrlHtml5(video.bvid, playbackCid, qn = BiliQn.P1080)
            attachPlayData(fallback, BiliQn.P1080)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.message ?: "HTML5 单文件流也不可用"
        }
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playbackState = PlaybackState.Ready
            }

            override fun onPlayerError(error: PlaybackException) {
                if (rotateCandidate()) return
                // Signed CDN URLs are short-lived and a CDN can reject one
                // host while another URL returned by a fresh playurl request
                // is still usable. Refresh DASH once before falling back to
                // the older single-file format.
                if (!playbackRefreshTried && playbackCid > 0) {
                    playbackRefreshTried = true
                    playbackState = PlaybackState.Loading
                    scope.launch {
                        try {
                            val refreshed = client.playUrl(
                                bvid = video.bvid,
                                cid = playbackCid,
                                qn = BiliQn.P1080,
                                fnval = BiliClient.FNVAL_DASH_ALL,
                            )
                            attachPlayData(refreshed, BiliQn.P1080)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // The DURL fallback below provides the next
                            // compatibility path and its error is user-facing.
                            playbackState = PlaybackState.Loading
                            durlFallbackTried = true
                            try {
                                val fallback = client.playUrl(
                                    bvid = video.bvid,
                                    cid = playbackCid,
                                    qn = BiliQn.P720,
                                    fnval = BiliClient.FNVAL_DURL,
                                )
                                attachPlayData(fallback, BiliQn.P720)
                            } catch (error2: CancellationException) {
                                throw error2
                            } catch (error2: Exception) {
                                val html5Error = tryHtml5Fallback()
                                if (html5Error != null) {
                                    playbackState = PlaybackState.Error("播放器无法读取视频流：${error2.message ?: "单文件流也不可用"}；HTML5 回退失败：$html5Error")
                                }
                            }
                        }
                    }
                    return
                }
                if (!dashFallbackTried && !durlFallbackTried && playbackCid > 0) {
                    dashFallbackTried = true
                    playbackState = PlaybackState.Loading
                    scope.launch {
                        try {
                            val fallback = client.playUrl(
                                bvid = video.bvid,
                                cid = playbackCid,
                                qn = BiliQn.P720,
                                fnval = BiliClient.FNVAL_DASH_ALL,
                            )
                            durlFallbackTried = fallback.dash == null
                            attachPlayData(fallback, BiliQn.P720)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            durlFallbackTried = true
                            try {
                                val fallback = client.playUrl(
                                    bvid = video.bvid,
                                    cid = playbackCid,
                                    qn = BiliQn.P720,
                                    fnval = BiliClient.FNVAL_DURL,
                                )
                                attachPlayData(fallback, BiliQn.P720)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                val html5Error = tryHtml5Fallback()
                                if (html5Error != null) {
                                    playbackState = PlaybackState.Error("播放器无法读取视频流：${error.message ?: "低清晰度视频流也不可用"}；HTML5 回退失败：$html5Error")
                                }
                            }
                        }
                    }
                    return
                }
                // DASH uses separate audio/video URLs. If every CDN pair is rejected,
                // request the older single-file stream format as a compatibility fallback.
                if (!durlFallbackTried && playbackCid > 0) {
                    durlFallbackTried = true
                    playbackState = PlaybackState.Loading
                    scope.launch {
                        try {
                            val fallback = client.playUrl(
                                bvid = video.bvid,
                                cid = playbackCid,
                                qn = BiliQn.P720,
                                fnval = BiliClient.FNVAL_DURL,
                            )
                            attachPlayData(fallback, BiliQn.P720)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            val html5Error = tryHtml5Fallback()
                            if (html5Error != null) {
                                playbackState = PlaybackState.Error("播放器无法读取视频流：${error.message ?: "单文件流也不可用"}；HTML5 回退失败：$html5Error")
                            }
                        }
                    }
                    return
                }
                if (!html5FallbackTried && playbackCid > 0) {
                    playbackState = PlaybackState.Loading
                    scope.launch {
                        val fallbackError = tryHtml5Fallback()
                        if (fallbackError != null) {
                            playbackState = PlaybackState.Error("播放器无法读取视频流：$fallbackError")
                        }
                    }
                    return
                }
                val httpStatus = generateSequence(error as Throwable?) { it.cause }
                    .mapNotNull { (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode }
                    .firstOrNull()
                val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                val message = if (httpStatus == 403 ||
                    (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && detail?.contains("403") == true)
                ) {
                    "CDN 返回 HTTP 403，当前网络出口可能被 B 站拒绝"
                } else {
                    "播放器无法读取视频流：${error.errorCodeName}${detail?.let { "（$it）" }.orEmpty()}"
                }
                playbackState = PlaybackState.Error(message)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    // A CDN node can accept the connection and then trickle or stall without
    // ever raising an error, leaving the player on "正在获取视频流". If startup
    // makes no buffering progress for a while, move to the next backup host.
    LaunchedEffect(playbackData, playbackCandidateIndex) {
        if (playbackData == null) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        var lastBuffered = -1L
        var lastProgressAt = startedAt
        while (playbackState == PlaybackState.Loading) {
            delay(1_000)
            val now = SystemClock.elapsedRealtime()
            val buffered = exoPlayer.bufferedPosition
            if (buffered > lastBuffered) {
                lastBuffered = buffered
                lastProgressAt = now
            }
            if (now - startedAt >= STARTUP_STALL_TIMEOUT_MS &&
                now - lastProgressAt >= STARTUP_NO_PROGRESS_MS
            ) {
                // No more hosts here: let the read timeout raise an error so
                // onPlayerError walks the refresh / DURL / HTML5 fallbacks.
                rotateCandidate()
                return@LaunchedEffect
            }
        }
    }
    // Part loading runs in its own scope instead of the effect: when listening in
    // the background, Compose stops recomposing, so the end-of-part listener and the
    // notification "next" start the next part directly through [startLoad].
    val loadScope = remember(video.bvid, session?.cookie) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var loadJob by remember(video.bvid, session?.cookie) { mutableStateOf<Job?>(null) }
    var lastRequestedLoad by remember(video.bvid, session?.cookie) { mutableStateOf<List<Any?>?>(null) }
    DisposableEffect(loadScope) { onDispose { loadScope.cancel() } }
    suspend fun loadPlayback() {
        val resumeAtMs = qualityResumeMs
        val resumePlaying = qualityResumePlaying
        qualityResumeMs = 0L
        qualityResumePlaying = true
        playbackState = PlaybackState.Loading
        playbackRefreshTried = false
        dashFallbackTried = false
        durlFallbackTried = false
        html5FallbackTried = false
        playbackData = null
        playbackCid = 0L
        exoPlayer.stop()
        try {
            // View + creator data do not change between parts; only playurl
            // needs a fresh request when switching episodes.
            val info = videoInfo ?: run {
                client.warmUp()
                client.videoInfo(video.bvid).also { loaded ->
                    videoInfo = loaded
                    if (loaded.owner.mid > 0) {
                        scope.launch {
                            creatorCard = runCatching { client.userCard(loaded.owner.mid) }.getOrNull()
                        }
                    }
                }
            }
            val page = info.pages.firstOrNull { it.page == selectedPage }
                ?: info.pages.firstOrNull()?.also { selectedPage = it.page }
            val cid = page?.cid ?: info.cid
            if (cid <= 0) throw IllegalStateException("B 站没有返回视频分 P 信息")
            val play = episodePlayCache.take(cid)
                ?: client.playUrl(info.bvid.ifBlank { video.bvid }, cid, qn = selectedQn)
            playbackCid = cid
            attachPlayData(play, selectedQn, resumeMs = resumeAtMs)
            if (!resumePlaying) exoPlayer.playWhenReady = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackState = PlaybackState.Error(error.message ?: "无法获取视频播放地址")
        }
    }
    fun startLoad(key: List<Any?>) {
        lastRequestedLoad = key
        loadJob?.cancel()
        loadJob = loadScope.launch { loadPlayback() }
    }
    LaunchedEffect(video.bvid, session?.cookie, retryToken, selectedPage, selectedQn, qualityRequestToken) {
        val key = listOf(retryToken, selectedPage, selectedQn, qualityRequestToken)
        // The background queue may already have started exactly this part.
        if (key != lastRequestedLoad) startLoad(key)
    }
    fun listenSubtitle(page: Int): String = listOfNotNull(
        videoInfo?.owner?.name?.takeIf { it.isNotBlank() } ?: video.author.takeIf { it.isNotBlank() },
        videoInfo?.pages?.takeIf { it.size > 1 }?.firstOrNull { it.page == page }
            ?.let { "P${it.page} ${it.part}".trim() },
    ).joinToString(" · ")
    /** Switches part without waiting for recomposition (background-safe). */
    fun advanceToPart(page: Int) {
        selectedPage = page
        startLoad(listOf(retryToken, page, selectedQn, qualityRequestToken))
        ListenPlaybackBridge.updateMetadata(exoPlayer, video.title, listenSubtitle(page))
    }
    val latestAdvance by rememberUpdatedState(::advanceToPart)
    // Fallback paths can lower the stream too, so derive the badge from every
    // successful playurl response instead of only the primary request.
    LaunchedEffect(playbackData) {
        playbackData?.quality?.takeIf { it > 0 }?.let { actualQn = it }
    }
    val episodePages = videoInfo?.pages?.takeIf { it.size > 1 }
    val latestEpisodePages by rememberUpdatedState(episodePages)
    val latestAutoPlayNext by rememberUpdatedState(autoPlayNext)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED || !latestAutoPlayNext) return
                val parts = latestEpisodePages ?: return
                // Read the live page, not a composition snapshot: in the background
                // several parts can finish before the UI recomposes once.
                val index = parts.indexOfFirst { it.page == selectedPage }
                if (index in 0 until parts.lastIndex) {
                    latestAdvance(parts[index + 1].page)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    LaunchedEffect(exoPlayer, selectedPage, autoPlayNext, episodePages, selectedQn) {
        if (!autoPlayNext) return@LaunchedEffect
        val parts = episodePages ?: return@LaunchedEffect
        val currentIndex = parts.indexOfFirst { it.page == selectedPage }
        val nextPart = parts.getOrNull(currentIndex + 1) ?: return@LaunchedEffect
        while (true) {
            delay(1_000)
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            if (duration <= 0L || position <= 0L) continue
            if (duration - position > NEXT_PART_PREFETCH_AHEAD_MS) continue
            if (episodePlayCache.containsFresh(nextPart.cid)) break
            try {
                val play = client.playUrl(
                    bvid = video.bvid,
                    cid = nextPart.cid,
                    qn = selectedQn,
                )
                episodePlayCache.put(nextPart.cid, play)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The regular fetch when the current part ends is the fallback.
            }
            break
        }
    }
    val episodePicker: (@Composable (onDismiss: () -> Unit) -> Unit)? =
        if (episodePages == null) null else { onDismiss ->
            OnlineVideoEpisodes(episodePages, selectedPage, onDismiss) { selectedPage = it.page }
        }
    val episodeOverlayVisible = LocalConfiguration.current.orientation
        .let { it != Configuration.ORIENTATION_LANDSCAPE } &&
        episodesSheetVisible && episodePages != null
    val overlayPages = episodePages
    val episodeOverlayContent: (@Composable () -> Unit)? = if (overlayPages != null) {
        {
            OnlineEpisodesBottomSheet(
                pages = overlayPages,
                selectedPage = selectedPage,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChange = { autoPlayNext = it },
                gridView = episodesInGridView,
                onGridViewChange = { episodesInGridView = it },
                onDismiss = { episodesSheetVisible = false },
                onPageSelected = { selectedPage = it }
            )
        }
    } else null
    val qualityOptions = remember(playbackData, actualQn) { buildQualityOptions(playbackData, actualQn) }
    // Show what is actually playing; fall back to the requested value while loading.
    val effectiveQuality = actualQn.takeIf { qn -> qn > 0 && qualityOptions.any { it.qn == qn } }
        ?: qualityOptions.firstOrNull { it.qn == selectedQn }?.qn
        ?: qualityOptions.maxByOrNull { it.qn }?.qn
    val nextPage = videoInfo?.pages?.let { pages ->
        val currentIndex = pages.indexOfFirst { it.page == selectedPage }
        if (currentIndex >= 0) pages.getOrNull(currentIndex + 1) else null
    }
    val listenQueue = remember(exoPlayer) {
        object : ListenQueue {
            private fun next() = latestEpisodePages?.let { parts ->
                val index = parts.indexOfFirst { it.page == selectedPage }
                if (index >= 0) parts.getOrNull(index + 1) else null
            }
            override val hasNext: Boolean get() = next() != null
            override fun skipToNext() { next()?.let { latestAdvance(it.page) } }
        }
    }
    VideoPlayerScreen(
        player = exoPlayer,
        title = video.title,
        playbackError = (playbackState as? PlaybackState.Error)?.message,
        loadingMessage = if (playbackState == PlaybackState.Loading) "正在获取视频流…" else null,
        sourceLabel = "哔哩哔哩",
        commentMessage = "暂不支持查看在线评论",
        onBack = onBack,
        onRetry = { retryToken++ },
        onNextEpisode = nextPage?.let { page -> { advanceToPart(page.page) } },
        qualityOptions = qualityOptions,
        selectedQuality = effectiveQuality,
        onQualitySelected = { qn ->
            if (qn == actualQn) {
                // Already streaming at this quality; just keep the requested value in sync.
                selectedQn = qn
            } else {
                qualityResumeMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                qualityResumePlaying = exoPlayer.playWhenReady
                selectedQn = qn
                // The selected qn may already match while the server clamped the
                // stream, so use a token to force a fresh playurl request.
                qualityRequestToken++
            }
        },
        episodes = episodePicker,
        episodeOverlay = episodeOverlayContent,
        episodeOverlayVisible = episodeOverlayVisible,
        episodeOverlayDismiss = if (episodeOverlayVisible) {
            { episodesSheetVisible = false }
        } else null,
        detailsBackground = OnlineSurface,
        showDetailTabs = false,
        listenInfo = ListenInfo(
            subtitle = listenSubtitle(selectedPage),
            online = true,
            coverUrl = videoInfo?.pic?.takeIf { it.isNotBlank() }?.let(::normalizeImageUrl) ?: video.coverUrl,
        ),
        listenQueue = listenQueue,
        remainingVideos = episodePages?.let { parts ->
            val index = parts.indexOfFirst { it.page == selectedPage }
            if (index >= 0) (parts.size - index).coerceAtLeast(1) else 1
        } ?: 1,
        details = { _ ->
            Box(Modifier.fillMaxWidth()) {
                OnlineVideoDetails(
                    video = video,
                    info = videoInfo,
                    creatorCard = creatorCard,
                    context = context,
                    selectedPage = selectedPage,
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChange = { autoPlayNext = it },
                    onPageSelected = { selectedPage = it },
                    onOpenEpisodes = { episodesSheetVisible = true }
                )
            }
        }
    )
}

@Composable
internal fun OnlineVideoEpisodes(
    pages: List<Page>,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Page) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pages.indexOfFirst { it.page == selectedPage }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("分集(${pages.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages, key = { _, page -> page.cid }) { _, page ->
                    OnlineEpisodeRow(page, page.page == selectedPage) { onPageSelected(page); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun OnlineEpisodeRow(page: Page, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) OnlinePink.copy(alpha = .18f) else Color.White.copy(alpha = .05f))
            .border(1.dp, if (selected) OnlinePink else Color.Transparent, RoundedCornerShape(11.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) EqualizerBars()
        Text(
            "P${page.page} ${page.part.ifBlank { "第 ${page.page} 部分" }}${if (selected) " (当前播放)" else ""}",
            color = if (selected) OnlinePink else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (selected) 4.dp else 0.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else Color.White.copy(alpha = .45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun EqualizerBars() {
    Row(Modifier.width(20.dp).height(16.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val heights = listOf(8.dp, 13.dp, 10.dp)
        heights.forEach { height -> Box(Modifier.width(3.dp).height(height).clip(RoundedCornerShape(2.dp)).background(OnlinePink)) }
    }
}

/**
 * Maps the playurl `accept_quality` list to the compact labels used by the
 * fullscreen quality drawer. Anonymous playback is reliable up to 1080P; the
 * 4K/8K/Dolby tiers need account privileges and are intentionally hidden for
 * now instead of offering an option that cannot start.
 */
private fun buildQualityOptions(play: PlayData?, actualQn: Int): List<PlayerQualityOption> {
    val accepted = (play?.acceptQuality.orEmpty() + listOf(actualQn))
        .filter { it in BiliQn.P360..BiliQn.P1080 }
        .distinct()
    val qns = accepted.ifEmpty {
        listOf(BiliQn.P1080, BiliQn.P720, BiliQn.P480, BiliQn.P360)
    }.sortedDescending()
    return qns.map { qn ->
        when (qn) {
            74 -> PlayerQualityOption(qn, "720P 60帧", "高帧率")
            BiliQn.P1080 -> PlayerQualityOption(qn, "1080P", "高清 · 推荐")
            BiliQn.P720 -> PlayerQualityOption(qn, "720P", "高清 · 省流")
            BiliQn.P480 -> PlayerQualityOption(qn, "480P", "清晰")
            BiliQn.P360 -> PlayerQualityOption(qn, "360P", "流畅")
            else -> PlayerQualityOption(qn, "${qn}P", "")
        }
    }
}
