package io.github.bilibox.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.util.formatSearchDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 搜索、收藏与离线缓存共用的视频卡片：列表卡片与宫格卡片。
 *
 * 列表卡片把封面放在左侧，标题与两行元信息在右侧；宫格卡片把 16:10 封面放在上方，
 * 元信息只保留 UP 主与播放 / 弹幕，避免在窄卡片里挤入日期。两者共用同一套
 * 卡片底色、圆角、阴影与元信息排版，仅根据内容宽度调整字号与留白。
 */
private val CardShape = RoundedCornerShape(14.dp)
private val CoverWidth = 156.dp
private val ListCardHeight = 100.dp

/** 列表布局：左封面 + 右信息，整卡可点。 */
@Composable
internal fun VideoListCard(
    video: VideoSummary,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    additionalInfo: String? = null,
    statusText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val publishDate = remember(video.publishTime) { formatPublishDate(video.publishTime) }
    VideoListCardLayout(
        title = video.title,
        author = video.author,
        darkMode = darkMode,
        modifier = modifier,
        publishDate = publishDate,
        additionalInfo = additionalInfo,
        enabled = enabled,
        cover = {
            RemoteCover(
                video.coverUrl,
                Modifier.fillMaxSize(),
                RectangleShape,
                placeholder = { VideoCoverPlaceholder(Modifier.fillMaxSize()) },
            )
            DurationBadge(video.duration, Modifier.align(Alignment.BottomEnd))
        },
        metadata = { color ->
            if (statusText == null) {
                VideoStatsLine(video.playCount, video.commentCount, color, 11.sp, 12.5.dp)
            } else {
                Text(statusText, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, lineHeight = metaLineHeight(11.sp))
            }
        },
        onClick = onClick,
    )
}

/** 在线与离线共用的列表卡片，封面、元信息及管理操作由调用方提供。 */
@Composable
internal fun VideoListCardLayout(
    title: String,
    author: String,
    darkMode: Boolean,
    cover: @Composable BoxScope.() -> Unit,
    metadata: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    publishDate: String? = null,
    additionalInfo: String? = null,
    enabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = videoCardPalette(darkMode)
    VideoCardSurface(darkMode, modifier.fillMaxWidth(), onClick, enabled) {
        // 封面不参与高度测量，由右栏文字决定卡高，系统字体放大后仍填满左缘。
        Row(Modifier.matchParentSize()) {
            Box(Modifier.width(CoverWidth).fillMaxHeight().clip(RectangleShape), content = cover)
            Spacer(Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().heightIn(min = ListCardHeight + if (additionalInfo == null) 0.dp else 19.dp)
                .padding(start = CoverWidth + 12.dp, top = 9.dp, end = 10.dp, bottom = 9.dp)
        ) {
            VideoCardTitle(title, palette.title, 14.sp, 19.sp)
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(end = if (action == null) 0.dp else 36.dp)) {
                VideoAuthorLine(author, publishDate, palette.author, palette.muted, 11.sp, 12.5.dp)
                Spacer(Modifier.height(5.dp))
                metadata(palette.muted)
                if (additionalInfo != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        additionalInfo,
                        color = palette.muted,
                        fontSize = 11.sp,
                        lineHeight = metaLineHeight(11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (action != null) {
            Box(Modifier.align(Alignment.BottomEnd), contentAlignment = Alignment.BottomEnd) { action() }
        }
    }
}

/** 宫格布局：上封面 + 下信息，两列排布。 */
@Composable
internal fun VideoGridCard(
    video: VideoSummary,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = videoCardPalette(darkMode)
    val publishDate = remember(video.publishTime) { formatPublishDate(video.publishTime) }
    VideoCardSurface(darkMode, modifier.fillMaxWidth(), onClick) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                RemoteCover(
                    video.coverUrl,
                    Modifier.fillMaxSize(),
                    RectangleShape,
                    placeholder = { VideoCoverPlaceholder(Modifier.fillMaxSize()) },
                )
                DurationBadge(video.duration, Modifier.align(Alignment.BottomEnd))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
                VideoCardTitle(
                    title = video.title,
                    color = palette.title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                VideoAuthorLine(
                    author = video.author,
                    date = null,
                    authorColor = palette.author,
                    mutedColor = palette.muted,
                    fontSize = 11.sp,
                    iconSize = 12.5.dp,
                )
                Spacer(Modifier.height(4.dp))
                VideoStatsLine(
                    playCount = video.playCount,
                    commentCount = video.commentCount,
                    color = palette.muted,
                    fontSize = 11.sp,
                    iconSize = 12.5.dp,
                )
            }
        }
    }
}

/**
 * 卡片外壳：半透明底色、大圆角、轻阴影，按下时轻微收缩。
 * 阴影只在高亮模式开启，深色模式改用极淡描边区分卡片与页面底色。
 */
@Composable
private fun VideoCardSurface(
    darkMode: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = videoCardPalette(darkMode)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "video card press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (darkMode) 0.dp else 5.dp,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = .05f),
                spotColor = Color.Black.copy(alpha = .10f),
            )
            .clip(CardShape)
            .background(palette.surface.copy(alpha = .78f))
            .border(1.dp, palette.edge, CardShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

@Composable
private fun VideoCardTitle(
    title: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    Text(
        text = title,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.SemiBold,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** UP 主 · 日期；日期为空（未知发布时间）时只显示 UP 主。 */
@Composable
private fun VideoAuthorLine(
    author: String,
    date: String?,
    authorColor: Color,
    mutedColor: Color,
    fontSize: TextUnit,
    iconSize: Dp,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VideoMetaIcon("author", Modifier.size(iconSize), tint = mutedColor)
        Spacer(Modifier.width(4.dp))
        Text(
            text = author,
            color = authorColor,
            fontSize = fontSize,
            lineHeight = metaLineHeight(fontSize),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (date != null) {
            Text(
                text = "·",
                color = mutedColor.copy(alpha = .6f),
                fontSize = fontSize,
                lineHeight = metaLineHeight(fontSize),
                modifier = Modifier.padding(horizontal = 3.dp),
            )
            Text(text = date, color = mutedColor, fontSize = fontSize, lineHeight = metaLineHeight(fontSize), maxLines = 1)
        }
    }
}

/** 播放量与弹幕量；图标与文字同色，保持一条 13dp 高的轻量信息行。 */
@Composable
private fun VideoStatsLine(
    playCount: String,
    commentCount: Long,
    color: Color,
    fontSize: TextUnit,
    iconSize: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VideoStat("play", playCount, color, fontSize, iconSize)
        VideoStat("comment", commentCount.toString(), color, fontSize, iconSize)
    }
}

@Composable
private fun VideoStat(
    icon: String,
    value: String,
    color: Color,
    fontSize: TextUnit,
    iconSize: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VideoMetaIcon(icon, Modifier.size(iconSize), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            color = color,
            fontSize = fontSize,
            lineHeight = metaLineHeight(fontSize),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 封面右下角的时长胶囊，深色半透明底保证在任何封面上都能读清。 */
@Composable
private fun DurationBadge(duration: String, modifier: Modifier = Modifier) {
    VideoCoverBadge(formatSearchDuration(duration), modifier)
}

/** 时长、分集数与离线播放进度共用的封面角标。 */
@Composable
internal fun VideoCoverBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .background(Color.Black.copy(alpha = .58f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

private class VideoCardPalette(
    val surface: Color,
    val title: Color,
    val author: Color,
    val muted: Color,
    val edge: Color,
)

private fun videoCardPalette(darkMode: Boolean): VideoCardPalette = if (darkMode) {
    VideoCardPalette(
        surface = Color(0xFF1D1F21),
        title = Color(0xFFE7E9ED),
        author = Color(0xFFA2A7AE),
        muted = Color(0xFF8A9099),
        edge = Color.White.copy(alpha = .06f),
    )
} else {
    VideoCardPalette(
        surface = Color.White,
        title = Color(0xFF18191C),
        author = Color(0xFF61666D),
        muted = Color(0xFF9499A0),
        edge = Color.Transparent,
    )
}

/** 元信息使用固定行高，让两行卡片的高度不受字体自带行距影响。 */
private fun metaLineHeight(fontSize: TextUnit): TextUnit = fontSize * 1.3f

private fun formatPublishDate(timestampSeconds: Long): String? {
    if (timestampSeconds <= 0L) return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
}
