package io.github.bilibox.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF7196),
    secondary = Color(0xFF9BA3AE),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF17191D),
    surface = Color(0xFF24272D),
    onBackground = Color(0xFFE7E9ED),
    onSurface = Color(0xFFE7E9ED),
)

private val LightColorScheme = lightColorScheme(
    primary = Pink,
    secondary = OnlineSecondary,
    tertiary = OnlinePink,
    background = OnlineSurface,
    surface = OnlineSurface,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = OnlineText,
)

@Composable
fun BiliBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
