package io.github.bilibox.feature.online

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.VideoMetaIcon
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.OnlineDivider
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineSecondary
import io.github.bilibox.core.ui.theme.OnlineSurface
import io.github.bilibox.core.ui.theme.OnlineText
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.bili.UserCardData
import io.github.bilibox.data.bili.VideoInfo
import io.github.bilibox.feature.player.LocalPlayerDetailActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun OnlineVideoDetails(
    video: VideoSummary,
    info: VideoInfo?,
    creatorCard: UserCardData?,
    context: Context,
    selectedPage: Int,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onOpenEpisodes: () -> Unit,
) {
    var following by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var liked by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var coined by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var saved by rememberSaveable(video.bvid) { mutableStateOf(false) }
    var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }
    val playerActions = LocalPlayerDetailActions.current

    val owner = info?.owner
    val ownerName = owner?.name?.takeIf { it.isNotBlank() }
        ?: creatorCard?.card?.name?.takeIf { it.isNotBlank() }
        ?: video.author
    val avatar = normalizeImageUrl(owner?.face.orEmpty().ifBlank { creatorCard?.card?.face.orEmpty() })
    val stat = info?.stat
    val title = info?.title?.takeIf { it.isNotBlank() } ?: video.title
    val bvId = info?.bvid?.ifBlank { video.bvid } ?: video.bvid
    val publishedAt = info?.pubdate?.takeIf { it > 0 } ?: video.publishTime
    val duration = info?.duration?.takeIf { it > 0 }?.let { formatPlaybackTime(it * 1_000L) }
        ?: video.duration

    Column(Modifier.fillMaxWidth().background(OnlineSurface)) {
        OnlineCreatorCard(
            name = ownerName,
            avatar = avatar,
            fans = creatorCard?.card?.fans,
            archiveCount = creatorCard?.archiveCount,
            level = creatorCard?.card?.levelInfo?.currentLevel,
            following = following,
            onToggleFollow = { following = !following },
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    title, color = OnlineText, fontSize = 15.5.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
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
                Row(
                    Modifier.padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (manager != null) {
                                manager.setPrimaryClip(ClipData.newPlainText("BV 号", bvId))
                                Toast.makeText(context, "BV 号已复制", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .semantics { contentDescription = "复制 BV 号 $bvId" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        bvId,
                        color = OnlineMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(5.dp))
                    AppGlyphIcon(AppIcon.COPY, OnlineMuted, Modifier.size(12.dp))
                }
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

        Column(Modifier.fillMaxWidth().background(OnlineSurface)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OnlineAction(
                    kind = "like",
                    label = "点赞",
                    value = formatOptionalCount((stat?.like ?: 0L) + if (liked) 1L else 0L),
                    selected = liked,
                ) { liked = !liked }
                OnlineAction(
                    kind = "coin",
                    label = "投币",
                    value = formatOptionalCount((stat?.coin ?: 0L) + if (coined) 1L else 0L),
                    selected = coined,
                ) { coined = !coined }
                OnlineAction(
                    kind = "favorite",
                    label = "收藏",
                    value = formatOptionalCount((stat?.favorite ?: 0L) + if (saved) 1L else 0L),
                    selected = saved,
                ) { saved = !saved }
                OnlineAction(kind = "screenshot", label = "截图", value = "", selected = false) {
                    val actions = playerActions
                    if (actions != null && actions.screenshotEnabled) {
                        actions.takeScreenshot()
                    } else {
                        Toast.makeText(context, "暂时无法截图，请等待视频画面显示", Toast.LENGTH_SHORT).show()
                    }
                }
                OnlineAction(kind = "share", label = "分享", value = "", selected = false) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$title https://www.bilibili.com/video/${video.bvid}")
                    }
                    context.startActivity(Intent.createChooser(intent, "分享视频"))
                }
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
    val iconTint by animateColorAsState(
        targetValue = if (selected) OnlinePink else OnlineSecondary,
        animationSpec = tween(180),
        label = "$kind 图标颜色"
    )
    val textTint by animateColorAsState(
        targetValue = if (selected) OnlinePink else OnlineMuted,
        animationSpec = tween(180),
        label = "$kind 文字颜色"
    )
    Column(
        Modifier.weight(1f)
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
            }
            .semantics(mergeDescendants = true) {
                contentDescription = if (value.isBlank()) label else "$label $value"
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnlineActionIcon(
            kind,
            iconTint,
            Modifier.size(24.dp).graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value.ifBlank { label },
            color = textTint,
            fontSize = 12.sp,
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
