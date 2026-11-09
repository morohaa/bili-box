package io.github.bilibox.feature.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 竖屏浅色弹层与横屏深色侧栏共用设置内容。 */
internal data class DanmakuSettingsColors(
    val pinkSoft: Color = Color(0xFFFFE8EF),
    val pinkTrack: Color = Color(0xFFFFB3C6),
    val card: Color = Color.White,
    val ink: Color = Color(0xFF17191B),
    val muted: Color = Color(0xFF9499A0),
    val idleTrack: Color = Color(0xFFE1E4E6),
    val iconIdle: Color = Color(0xFFF4F5F7),
    val glyphTint: Color = Color(0xFF111315),
)

internal val DarkDanmakuSettingsColors = DanmakuSettingsColors(
    pinkSoft = Color(0xFF49303D),
    pinkTrack = Color(0xFFBD5877),
    card = Color(0xFF25262E),
    ink = Color(0xFFF6F6F8),
    muted = Color(0xFF92939C),
    idleTrack = Color(0xFF454650),
    iconIdle = Color(0xFF30313B),
    glyphTint = Color(0xFFE2E2E8),
)

internal val LocalDanmakuSettingsColors = staticCompositionLocalOf { DanmakuSettingsColors() }

internal val DanmakuPinkSoft: Color
    @Composable get() = LocalDanmakuSettingsColors.current.pinkSoft

internal val DanmakuPinkTrack: Color
    @Composable get() = LocalDanmakuSettingsColors.current.pinkTrack

internal val DanmakuCard: Color
    @Composable get() = LocalDanmakuSettingsColors.current.card

internal val DanmakuInk: Color
    @Composable get() = LocalDanmakuSettingsColors.current.ink

internal val DanmakuMuted: Color
    @Composable get() = LocalDanmakuSettingsColors.current.muted

internal val DanmakuIdleTrack: Color
    @Composable get() = LocalDanmakuSettingsColors.current.idleTrack

internal val DanmakuIconIdle: Color
    @Composable get() = LocalDanmakuSettingsColors.current.iconIdle

internal val DanmakuGlyphTint: Color
    @Composable get() = LocalDanmakuSettingsColors.current.glyphTint
