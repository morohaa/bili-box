package io.github.bilibox.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlinePinkBrand

/**
 * 本地视频页的配色与动效令牌。
 *
 * 页面只保留三层：画布（浅灰）、卡片（纯白）、信息（文字与细线）。
 * 颜色只出现在少量语义位置（选中态、进度、强调点），其余交给留白和字重，
 * 这样即使主题色很鲜艳也不会显得廉价。
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
    /** 头部强调色光晕，替代整块彩色标题栏。 */
    val glow: Color,
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
            glow = accent.copy(alpha = .16f),
            pressTint = Color.White.copy(alpha = .06f),
        )
    } else {
        LibraryPalette(
            dark = false,
            background = Color(0xFFF2F3F5),
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
            glow = accent.copy(alpha = .11f),
            pressTint = Color(0xFF101215).copy(alpha = .05f),
        )
    }
}

internal val LocalLibraryPalette = staticCompositionLocalOf {
    libraryPalette(darkMode = false, accent = OnlinePinkBrand)
}

/**
 * 按下的即时反馈：轻微缩小 + 一层淡淡的压暗，松开后回弹。
 *
 * 相比默认水波纹，这种“整块变暗再回弹”的手感更接近 Telegram，
 * 在卡片、胶囊、图标按钮上表现一致。
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
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
        .background(overlay)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** 页面顶部的强调色光晕：比整块彩色标题栏安静得多，但仍保留品牌感。 */
internal fun Modifier.topGlow(color: Color): Modifier = drawBehind {
    if (color.alpha <= 0f) return@drawBehind
    drawRect(
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = color.alpha * .45f), Color.Transparent),
            center = Offset(size.width * .18f, 0f),
            radius = size.width * 1.05f,
        )
    )
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
