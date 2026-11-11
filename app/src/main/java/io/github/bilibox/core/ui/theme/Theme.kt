package io.github.bilibox.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    secondary = Color(0xFF9BA3AE),
    tertiary = accent,
    background = Color(0xFF17191D),
    surface = Color(0xFF24272D),
    onPrimary = Color.White,
    onBackground = Color(0xFFE7E9ED),
    onSurface = Color(0xFFE7E9ED),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    secondary = OnlineSecondary,
    tertiary = OnlinePinkBrand,
    background = OnlineSurface,
    surface = OnlineSurface,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = OnlineText,
)

@Composable
fun BiliBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 主题外观选中的强调色；动态取色开启时以系统色板为准。
    accent: Color = OnlinePinkBrand,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme(accent)

        else -> lightScheme(accent)
    }

    val typography = remember(fontFamily) { typographyFor(fontFamily) }
    CompositionLocalProvider(LocalAccentColor provides accent, LocalAppFontFamily provides fontFamily) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
