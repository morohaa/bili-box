package io.github.bilibox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** 只替换字族，保留现有字号、字重、行高和字间距。 */
internal fun typographyFor(fontFamily: FontFamily): Typography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(fontFamily = fontFamily),
    displayMedium = Typography.displayMedium.copy(fontFamily = fontFamily),
    displaySmall = Typography.displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = Typography.headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = Typography.titleLarge.copy(fontFamily = fontFamily),
    titleMedium = Typography.titleMedium.copy(fontFamily = fontFamily),
    titleSmall = Typography.titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = Typography.bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = Typography.bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = Typography.bodySmall.copy(fontFamily = fontFamily),
    labelLarge = Typography.labelLarge.copy(fontFamily = fontFamily),
    labelMedium = Typography.labelMedium.copy(fontFamily = fontFamily),
    labelSmall = Typography.labelSmall.copy(fontFamily = fontFamily),
)
