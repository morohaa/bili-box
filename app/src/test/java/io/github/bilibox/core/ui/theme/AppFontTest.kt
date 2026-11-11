package io.github.bilibox.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontTest {
    @Test
    fun `old settings and unknown font ids retain system typography`() {
        assertEquals(AppFont.DEFAULT, AppearanceSettings().font)
        assertEquals(AppFont.DEFAULT, AppFont.fromStorageId(null))
        assertEquals(AppFont.DEFAULT, AppFont.fromStorageId("removed-font"))
        AppFont.entries.forEach { font ->
            assertEquals(font, AppFont.fromStorageId(font.storageId))
        }
    }

    @Test
    fun `font switching reaches every material text role and preserves text metrics`() {
        val original = styles(Typography)
        val changed = styles(typographyFor(FontFamily.Serif))
        original.zip(changed).forEach { (before, after) ->
            assertEquals(FontFamily.Serif, after.fontFamily)
            assertEquals(before.copy(fontFamily = FontFamily.Serif), after)
        }
    }

    private fun styles(typography: androidx.compose.material3.Typography) = with(typography) {
        listOf(
            displayLarge, displayMedium, displaySmall,
            headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall,
            bodyLarge, bodyMedium, bodySmall,
            labelLarge, labelMedium, labelSmall,
        )
    }
}
