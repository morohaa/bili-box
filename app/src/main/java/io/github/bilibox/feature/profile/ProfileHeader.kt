package io.github.bilibox.feature.profile

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.profile.BannerAdjustments

// 头图高度跟屏宽成比例：各机型裁切和观感一致，不再把系统状态栏高度加进高度。
private const val BANNER_ASPECT_RATIO = 1.7f

// 超宽窗口（横屏、平板）下不再继续放大，避免头图吃掉整屏。
private val BannerMaxHeight = 320.dp

// 头图固定在顶部不滚动，所以高度还要受可用高度限制：横屏或小屏时得给内容区留位置。
private const val BANNER_MAX_HEIGHT_FRACTION = 0.42f

/** 头图高度：优先按屏宽比例，再受绝对上限与可用高度限制。 */
internal fun profileBannerHeight(availableWidth: Dp, availableHeight: Dp): Dp = minOf(
    availableWidth / BANNER_ASPECT_RATIO,
    BannerMaxHeight,
    availableHeight * BANNER_MAX_HEIGHT_FRACTION,
)

@Composable
internal fun ProfileHeader(
    session: BiliSession?,
    avatar: ImageBitmap?,
    defaultAvatar: Int,
    customBanner: ImageBitmap?,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBannerClick: () -> Unit,
    bannerHeight: Dp,
    bannerAdjustments: BannerAdjustments = BannerAdjustments(),
) {
    val background = if (darkMode) MyBgDark else MyBgLight

    Box(Modifier.fillMaxWidth().height(bannerHeight + 56.dp)) {
        Banner(
            avatar = avatar,
            customBanner = customBanner,
            modifier = Modifier.fillMaxWidth().height(bannerHeight)
                .clickable(role = Role.Button, onClick = onBannerClick)
                .semantics { contentDescription = "预览头图" },
            adjustments = bannerAdjustments,
        )
        ProfileAvatar(
            session = session,
            avatar = avatar,
            defaultAvatar = defaultAvatar,
            ring = background,
            darkMode = darkMode,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 20.dp, y = bannerHeight - 45.dp),
        )
        ThemeToggle(
            darkMode = darkMode,
            onToggle = onToggleDarkMode,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = bannerHeight + 10.dp),
        )
    }

    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
        Text(
            text = session?.username ?: "未登录",
            color = if (darkMode) MyInkDark else MyInkLight,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (session != null) 18.dp else 10.dp))
        if (session != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(session.level)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (session.nextExp > 0) "${session.currentExp}/${session.nextExp}" else "MAX",
                    color = if (darkMode) MySubDark else MySubLight,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            ExpProgress(
                fraction = if (session.nextExp > 0) session.currentExp.toFloat() / session.nextExp else 1f,
                darkMode = darkMode,
            )
        } else {
            Text(
                text = "登录后可同步收藏、历史与稍后再看",
                color = if (darkMode) MySubDark else MySubLight,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun Banner(
    avatar: ImageBitmap?,
    customBanner: ImageBitmap?,
    modifier: Modifier = Modifier,
    adjustments: BannerAdjustments = BannerAdjustments(),
) {
    // BlurEffect 会把图层向外扩散，必须裁掉，否则头图会糊到信息区里。
    Box(modifier.clipToBounds()) {
        BannerArtwork(avatar, customBanner, Modifier.matchParentSize(), adjustments)
        // 状态栏图标是白色，顶部压一层深色保证可读。
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = .34f), 0.42f to Color.Transparent)
            )
        )
    }
}

/**
 * 头图画面本身：默认由主题色与头像生成，换过头图时直接铺自定义图片。
 *
 * 单独拆出来给全屏预览复用，预览不叠加顶部状态栏遮罩；暗化 / 模糊按预览结果
 * 叠加在同一层画面里，保证个人页与编辑页看到的效果一致。
 */
@Composable
internal fun BannerArtwork(
    avatar: ImageBitmap?,
    customBanner: ImageBitmap?,
    modifier: Modifier = Modifier,
    adjustments: BannerAdjustments = BannerAdjustments(),
) {
    val avatarBlurPx = with(LocalDensity.current) { 26.dp.toPx() }
    // 编辑器里的模糊按 px 计算，与设计稿的滑杆刻度一致。
    val imageBlurPx = adjustments.blur.toFloat()
    val canBlur = Build.VERSION.SDK_INT >= 31
    val accent = LocalAccentColor.current
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                if (canBlur && imageBlurPx > 0f) {
                    renderEffect = BlurEffect(imageBlurPx, imageBlurPx, TileMode.Clamp)
                    val scale = 1f + adjustments.blur / 200f
                    scaleX = scale
                    scaleY = scale
                }
            }
        ) {
            if (customBanner != null) {
                Image(
                    bitmap = customBanner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.linearGradient(
                            listOf(lerp(accent, Color.White, .12f), lerp(accent, Color.Black, .32f))
                        )
                    )
                )
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            alpha = 0.94f
                            if (canBlur) renderEffect = BlurEffect(avatarBlurPx, avatarBlurPx, TileMode.Clamp)
                        },
                    )
                    // 统一成主题强调色，避免头像放大后喧宾夺主。
                    Box(Modifier.matchParentSize().background(accent.copy(alpha = .2f)))
                }
            }
        }
        val darkness = adjustments.darkness.coerceIn(0, 100)
        if (darkness > 0) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = darkness / 100f)))
        }
    }
}

@Composable
internal fun ProfileAvatar(
    session: BiliSession?,
    avatar: ImageBitmap?,
    defaultAvatar: Int,
    ring: Color,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(86.dp).clip(CircleShape)
            .background(if (darkMode) Color(0xFF2A2D33) else Color(0xFFE7ECF3)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (session == null) {
            Image(
                painter = painterResource(defaultAvatar),
                contentDescription = "默认头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = session.username.take(1),
                color = LocalAccentColor.current,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.matchParentSize().border(3.dp, ring, CircleShape))
    }
}

@Composable
internal fun ThemeToggle(darkMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val sunAlpha by animateFloatAsState(if (darkMode) 0f else 1f, label = "sun")
    val moonAlpha by animateFloatAsState(if (darkMode) 1f else 0f, label = "moon")
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp).graphicsLayer { alpha = sunAlpha }) {
            val unit = size.minDimension / 24f
            val tint = Color(0xFFF59E0B)
            val stroke = Stroke(2f * unit, cap = StrokeCap.Round)
            drawCircle(tint, 4f * unit, Offset(12f * unit, 12f * unit), style = stroke)
            for (index in 0 until 8) {
                val angle = Math.PI * index / 4.0
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                drawLine(
                    tint,
                    Offset(12f * unit + cos * 7.5f * unit, 12f * unit + sin * 7.5f * unit),
                    Offset(12f * unit + cos * 9.5f * unit, 12f * unit + sin * 9.5f * unit),
                    stroke.width,
                    StrokeCap.Round,
                )
            }
        }
        Canvas(Modifier.size(24.dp).graphicsLayer { alpha = moonAlpha }) {
            val unit = size.minDimension / 24f
            val path = PathParser().parsePathString("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z").toPath()
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(
                    path,
                    color = Color(0xFFE2E8F0),
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private val LevelBadgeShape = GenericShape { size, _ ->
    val u = size.width / 36f
    moveTo(4 * u, 0f)
    lineTo(30 * u, 0f)
    lineTo(36 * u, 6 * u)
    lineTo(36 * u, 18 * u)
    lineTo(4 * u, 18 * u)
    arcTo(Rect(Offset(0f, 10 * u), Size(8 * u, 8 * u)), 90f, 90f, false)
    lineTo(0f, 4 * u)
    arcTo(Rect(Offset(0f, 0f), Size(8 * u, 8 * u)), 180f, 90f, false)
    close()
}

@Composable
internal fun LevelBadge(level: Int) {
    Box(
        Modifier.width(32.dp).height(16.dp).background(MyOrange, LevelBadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LV${level.coerceAtLeast(0)}",
            color = Color.White,
            style = TextStyle(
                fontSize = 9.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.3.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

@Composable
internal fun ExpProgress(fraction: Float, darkMode: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (darkMode) MyTrackDark else MyTrackLight)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFFF6600), Color(0xFFFF3300))),
                    RoundedCornerShape(2.dp),
                )
        )
    }
}
