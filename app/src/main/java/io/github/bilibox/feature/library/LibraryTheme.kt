package io.github.bilibox.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlinePinkBrand

/**
 * 本地视频页的配色与动效令牌。
 *
 * 媒体列表以连续的近白 / 炭黑画布承载，靠封面、文字和细线区分条目。
 * 卡片色保留给悬浮续播条和设置面板，强调色用于选中态和播放进度。
 */
internal data class LibraryPalette(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val chip: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    /** 卡片按下时的叠加色。 */
    val pressTint: Color,
)

/** 头部强调色上的文字颜色：浅色强调色（如青空蓝）改用深色文字保证可读。 */
internal fun accentContentColor(accent: Color): Color = if (accent.luminance() > .62f) Color(0xFF18191C) else Color.White

internal fun libraryPalette(darkMode: Boolean, accent: Color): LibraryPalette {
    val onAccent = accentContentColor(accent)
    return if (darkMode) {
        LibraryPalette(
            dark = true,
            background = Color(0xFF0E1013),
            card = Color(0xFF191C20),
            cardBorder = Color.White.copy(alpha = .05f),
            chip = Color(0xFF23272C),
            ink = Color(0xFFE8EAED),
            ink2 = Color(0xFFA4AAB2),
            ink3 = Color(0xFF71777F),
            line = Color(0xFF2A2F35),
            accent = accent,
            onAccent = onAccent,
            accentSoft = accent.copy(alpha = .20f),
            pressTint = Color.White.copy(alpha = .06f),
        )
    } else {
        LibraryPalette(
            dark = false,
            background = Color(0xFFFAFAFC),
            card = Color.White,
            cardBorder = Color.Transparent,
            chip = Color(0xFFF3F4F6),
            ink = Color(0xFF101215),
            ink2 = Color(0xFF555C64),
            ink3 = Color(0xFF8C939C),
            line = Color(0xFFE6E8EB),
            accent = accent,
            onAccent = onAccent,
            accentSoft = accent.copy(alpha = .10f),
            pressTint = Color(0xFF101215).copy(alpha = .05f),
        )
    }
}

/**
 * 头部渐变底色与配套前景色。
 *
 * 主题色压暗成深调做竖向渐变，白字 + 白搜索框：颜色有品牌感但不刺眼，
 * 比整块高饱和色条更闷、更收敛。
 */
internal data class LibraryHeaderStyle(
    val brush: Brush,
    val content: Color,
    val contentSoft: Color,
    val searchBackground: Color,
    val searchHint: Color,
    val searchText: Color,
    val chipActiveBackground: Color,
    val chipActiveContent: Color,
    val chipIdleContent: Color,
    val accentBar: Color,
    val breadcrumbSoft: Color,
)

internal fun libraryHeaderStyle(palette: LibraryPalette): LibraryHeaderStyle {
    val accent = palette.accent
    val base = if (palette.dark) Color(0xFF0B0D10) else Color(0xFF1B1D22)
    return LibraryHeaderStyle(
        brush = Brush.verticalGradient(
            0f to lerp(accent, base, if (palette.dark) .58f else .34f),
            1f to lerp(accent, base, if (palette.dark) .44f else .20f),
        ),
        content = Color.White,
        contentSoft = Color.White.copy(alpha = .78f),
        searchBackground = Color.White.copy(alpha = .94f),
        searchHint = Color(0xFF8C939C),
        searchText = Color(0xFF101215),
        chipActiveBackground = Color.White,
        chipActiveContent = lerp(accent, Color(0xFF101215), .12f),
        chipIdleContent = Color.White.copy(alpha = .82f),
        accentBar = Color.White.copy(alpha = .92f),
        breadcrumbSoft = Color.White.copy(alpha = .72f),
    )
}

internal val LocalLibraryPalette = staticCompositionLocalOf {
    libraryPalette(darkMode = false, accent = OnlinePinkBrand)
}

/**
 * 轻微按压与无过冲回弹。蒙层在内容之后绘制，封面也能得到一致的反馈。
 */
@Composable
internal fun Modifier.pressFeedback(
    onClick: () -> Unit,
    shape: Shape? = null,
    scaleTo: Float = .985f,
    tint: Color? = null,
): Modifier {
    val palette = LocalLibraryPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press scale",
    )
    val overlay by animateColorAsState(
        targetValue = if (pressed) (tint ?: palette.pressTint) else Color.Transparent,
        animationSpec = tween(if (pressed) 70 else 220),
        label = "press tint",
    )
    var modifier = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    if (shape != null) modifier = modifier.clip(shape)
    return modifier
        .drawWithContent {
            drawContent()
            drawRect(overlay)
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** 底部渐隐蒙层：让滚动内容在悬浮导航与继续观看条之前自然淡出。 */
internal fun Modifier.bottomFade(color: Color, heightFraction: Float = .55f): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = .92f), color),
            startY = size.height * (1f - heightFraction),
        )
    )
}

/** 卡片统一圆角。 */
internal val LibraryCardShape = RoundedCornerShape(16.dp)
