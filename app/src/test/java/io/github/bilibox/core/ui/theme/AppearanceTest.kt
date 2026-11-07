package io.github.bilibox.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test
    fun `catalog keeps twelve unique themes and custom last`() {
        assertEquals(12, ThemeCatalog.size)
        assertEquals(ThemeCatalog.size, ThemeCatalog.map { it.id }.toSet().size)
        assertTrue(ThemeCatalog.last().custom)
        assertEquals("pink", DefaultThemeId)
    }

    @Test
    fun `unknown theme id falls back to the default theme`() {
        assertEquals(DefaultThemeId, themeById("no-such-theme").id)
        assertEquals(DefaultThemeId, AppearanceSettings(themeId = "no-such-theme").theme.id)
    }

    @Test
    fun `appearance mode only follows the system when auto`() {
        assertFalse(AppearanceMode.LIGHT.resolveDark(systemDark = true))
        assertTrue(AppearanceMode.DARK.resolveDark(systemDark = false))
        assertTrue(AppearanceMode.AUTO.resolveDark(systemDark = true))
        assertFalse(AppearanceMode.AUTO.resolveDark(systemDark = false))
    }

    @Test
    fun `settings default to the classic pink theme and home background`() {
        val settings = AppearanceSettings()

        assertEquals(DefaultThemeId, settings.themeId)
        assertEquals(AppearanceMode.LIGHT, settings.mode)
        assertEquals(HomeBackground.HOME, settings.background)
        assertEquals("粉电视 · 经典", settings.theme.name)
    }
}
