package io.github.bilibox.feature.online

import android.content.Context
import android.content.Intent
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.SearchVideo
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.OnlineDivider
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineSecondary
import io.github.bilibox.core.ui.theme.OnlineSurface
import io.github.bilibox.core.ui.theme.OnlineText
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.bili.UserCardData
import io.github.bilibox.data.bili.VideoInfo
import io.github.bilibox.feature.player.formatPlaybackTime
import io.github.bilibox.feature.search.RemoteCover
import io.github.bilibox.feature.search.VideoMetaIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun OnlineVideoDetails(
    video: SearchVideo,
    info: VideoInfo?,
    creatorCard: UserCardData?,
    context: Context,
    selectedPage: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onOpenEpisodes: () -> Unit,
    cacheLabel: String,
    cacheSelected: Boolean,
    onCache: () -> Unit,
) {
    var following by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var liked by rememberSaveable(video.bvid) { mutableStateOf(false) }
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
                    ownerName,
                    color = OnlinePink,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val creatorStats = creatorCard?.let {
                    "${formatCompactCount(it.card.fans)} 粉丝    ${it.archiveCount} 视频"
                } ?: "哔哩哔哩 UP 主"
                Text(
                    creatorStats,
                    color = OnlineMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            drawLine(
                                Color.White,
                                Offset(center.x, inset),
                                Offset(center.x, size.height - inset),
                                1.4.dp.toPx(),
                                StrokeCap.Round
                            )
                            drawLine(
                                Color.White,
                                Offset(inset, center.y),
                                Offset(size.width - inset, center.y),
                                1.4.dp.toPx(),
                                StrokeCap.Round
                            )
                        }
                    }
                    Text(
                        if (following) "已关注" else "关注",
                        color = if (following) OnlineSecondary else Color.White,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
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
                    formatPublishDateTime(publishedAt),
                    color = OnlineMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(duration, color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 1)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(top = 10.dp), color = OnlineDivider)
                Text(
                    info?.bvid?.ifBlank { video.bvid } ?: video.bvid,
                    color = OnlineMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                info?.desc?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description,
                        color = OnlineSecondary,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
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
            }
            OnlineAction("coin", "投币", formatOptionalCount(stat?.coin ?: 0L), coined) { coined = !coined }
            OnlineAction("favorite", "收藏", formatOptionalCount(stat?.favorite ?: 0L), saved) { saved = !saved }
            OnlineAction("cache", "缓存", cacheLabel, cacheSelected, onCache)
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
internal fun OnlineAutoPlayChip(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
internal fun OnlineChevronRight(color: Color, modifier: Modifier = Modifier) {
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
            value.ifBlank { label },
            color = tint,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun OnlineMetric(kind: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VideoMetaIcon(kind, Modifier.size(14.dp), OnlineMuted)
        Spacer(Modifier.width(4.dp))
        Text(value, color = OnlineMuted, fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 1)
    }
}

@Composable
internal fun OnlineActionIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    // Use the reference's 24-unit vector shapes to keep all five icons consistent.
    val path = remember(kind) {
        val data = when (kind) {
            "like" -> "M14.6 3.4c-.6-.4-1.4-.2-1.8.4l-3.2 5.4c-.2.3-.5.5-.9.5H5.5C4.1 9.7 3 10.8 3 12.2v6.3C3 19.9 4.1 21 5.5 21h11.3c1.2 0 2.2-.8 2.5-1.9l1.6-6.8c.4-1.6-.8-3.1-2.5-3.1h-3.6l.8-3.8c.2-.8-.2-1.6-1-2zM7 19H5.5c-.3 0-.5-.2-.5-.5v-6.3c0-.3.2-.5.5-.5H7V19z"
            "favorite" -> "M12 2.8c.4 0 .8.2 1 .6l2.4 4.9 5.4.8c.9.1 1.2 1.2.6 1.8l-3.9 3.8.9 5.4c.2.9-.8 1.5-1.6 1.1L12 18.7l-4.8 2.5c-.8.4-1.8-.2-1.6-1.1l.9-5.4-3.9-3.8c-.6-.6-.3-1.7.6-1.8l5.4-.8 2.4-4.9c.2-.4.6-.6 1-.6z"
            "cache" -> "M12 3v11m0 0 4-4m-4 4-4-4M5 18v2h14v-2"
            "share" -> "M13.8 4.2c0-.9 1.1-1.3 1.7-.7l6.2 6.2c.4.4.4 1 0 1.4l-6.2 6.2c-.6.6-1.7.2-1.7-.7v-3.1c-4.8 0-8.2 1.5-10.3 4.7-.4.6-1.4.2-1.3-.5 1.1-6.5 5.2-9.8 11.6-10.2V4.2z"
            else -> ""
        }
        PathParser().parsePathString(data).toPath()
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            if (kind == "coin") {
                drawCircle(tint, 9.2f, Offset(12f, 12f), style = Stroke(2f))
                drawPath(
                    Path().apply {
                        moveTo(8.5f, 9.2f)
                        lineTo(15.5f, 9.2f)
                        moveTo(10f, 9.2f)
                        lineTo(10f, 15.5f)
                        moveTo(14f, 9.2f)
                        lineTo(14f, 15.5f)
                        moveTo(7.8f, 12.2f)
                        lineTo(16.2f, 12.2f)
                    },
                    tint,
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else if (kind == "cache") {
                drawPath(path, tint, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, tint)
            }
        }
    }
}

@Composable
internal fun ChevronDownIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        drawPath(
            Path().apply {
                moveTo(size.width * .25f, size.height * .375f)
                lineTo(size.width * .5f, size.height * .625f)
                lineTo(size.width * .75f, size.height * .375f)
            },
            tint,
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

internal fun formatOptionalCount(value: Long): String = value.takeIf { it > 0 }?.let(::formatCompactCount).orEmpty()

internal fun formatCompactCount(value: Long): String = when {
    value >= 100_000_000 -> "${String.format(Locale.US, "%.1f", value / 100_000_000.0).trimEnd('0').trimEnd('.')}亿"
    value >= 10_000 -> "${String.format(Locale.US, "%.1f", value / 10_000.0).trimEnd('0').trimEnd('.')}万"
    else -> value.toString()
}

internal fun formatPublishDateTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return "--"
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}

internal fun normalizeImageUrl(url: String): String = when {
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
                    drawRoundRect(tint, topLeft = Offset(2 * u, 10 * u), size = androidx.compose.ui.geometry.Size(4 * u, 11 * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u))
                    drawPath(
                        Path().apply {
                            moveTo(8 * u, 10 * u)
                            lineTo(12 * u, 3 * u)
                            cubicTo(16 * u, 2 * u, 15 * u, 7 * u, 14 * u, 9 * u)
                            lineTo(20 * u, 9 * u)
                            quadraticTo(23 * u, 9 * u, 22 * u, 12 * u)
                            lineTo(20 * u, 20 * u)
                            lineTo(8 * u, 20 * u)
                            close()
                        },
                        tint
                    )
                }

                1 -> {
                    drawCircle(tint, radius = 10 * u, style = stroke)
                    drawLine(tint, Offset(7 * u, 8 * u), Offset(17 * u, 8 * u), strokeWidth = 1.8f * u)
                    drawLine(tint, Offset(12 * u, 6 * u), Offset(12 * u, 19 * u), strokeWidth = 1.8f * u)
                    drawLine(tint, Offset(8 * u, 12 * u), Offset(16 * u, 12 * u), strokeWidth = 1.8f * u)
                }

                2 -> drawPath(
                    Path().apply {
                        for (point in 0..9) {
                            val angle = Math.PI * point / 5 - Math.PI / 2
                            val radius = if (point % 2 == 0) 11 * u else 5 * u
                            val x = 12 * u + kotlin.math.cos(angle).toFloat() * radius
                            val y = 12 * u + kotlin.math.sin(angle).toFloat() * radius
                            if (point == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    },
                    tint
                )

                3 -> drawPath(
                    Path().apply {
                        moveTo(3 * u, 21 * u)
                        cubicTo(3 * u, 11 * u, 7 * u, 7 * u, 15 * u, 7 * u)
                        lineTo(15 * u, 2 * u)
                        lineTo(23 * u, 11 * u)
                        lineTo(15 * u, 18 * u)
                        lineTo(15 * u, 13 * u)
                        quadraticTo(7 * u, 13 * u, 3 * u, 21 * u)
                        close()
                    },
                    tint
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (selected) Pink else Muted, fontSize = 11.sp)
    }
}

/** Startup gets this long before a non-progressing CDN host is abandoned. */
internal const val STARTUP_STALL_TIMEOUT_MS = 6_000L

/** Buffered position must not have grown for this long to count as stalled. */
internal const val STARTUP_NO_PROGRESS_MS = 3_000L

/** Hard cap for one URL set: a trickling CDN can keep "progressing" forever. */
internal const val STARTUP_DEADLINE_MS = 12_000L
