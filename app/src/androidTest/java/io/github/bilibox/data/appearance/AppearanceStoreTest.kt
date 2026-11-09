package io.github.bilibox.data.appearance

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.PageBackgroundSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("appearance_test", Context.MODE_PRIVATE)

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyHomeSettingsRemainIndependentFromNewPages() {
        preferences.edit().clear()
            .putString("home_background", "CUSTOM")
            .putInt("background_darkness", 37)
            .putInt("background_blur", 12)
            .commit()
        val settings = AppearanceStore(preferences).load()
        assertEquals(PageBackgroundSettings(HomeBackground.CUSTOM, 37, 12), settings.backgroundFor(BackgroundPage.HOME))
        assertEquals(PageBackgroundSettings(), settings.videoBackground)
        assertEquals(PageBackgroundSettings(), settings.profileBackground)
    }

    @Test
    fun reopeningRestoresEachPageAndResetOnlyChangesSelectedPage() {
        val home = PageBackgroundSettings(HomeBackground.CUSTOM, 12, 3)
        val video = PageBackgroundSettings(HomeBackground.SOLID, 27, 8)
        val profile = PageBackgroundSettings(HomeBackground.CUSTOM, 49, 15)
        val settings = AppearanceSettings()
            .withBackground(BackgroundPage.HOME, home)
            .withBackground(BackgroundPage.VIDEO, video)
            .withBackground(BackgroundPage.PROFILE, profile)
        AppearanceStore(preferences).save(settings)
        val reopened = AppearanceStore(preferences).load()
        assertEquals(settings, reopened)
        AppearanceStore(preferences).save(reopened.withBackground(BackgroundPage.VIDEO, PageBackgroundSettings()))
        val reset = AppearanceStore(preferences).load()
        assertEquals(home, reset.backgroundFor(BackgroundPage.HOME))
        assertEquals(PageBackgroundSettings(), reset.videoBackground)
        assertEquals(profile, reset.profileBackground)
    }

    @Test
    fun invalidStylesAndAdjustmentsFallBackOrClampForEachPage() {
        preferences.edit().clear()
            .putString("video_background", "unknown")
            .putInt("video_background_darkness", 500)
            .putInt("video_background_blur", -4)
            .putString("profile_background", "SOLID")
            .putInt("profile_background_darkness", -1)
            .putInt("profile_background_blur", 500)
            .commit()
        val settings = AppearanceStore(preferences).load()
        assertEquals(PageBackgroundSettings(HomeBackground.GRADIENT, 80, 0), settings.videoBackground)
        assertEquals(PageBackgroundSettings(HomeBackground.SOLID, 0, 20), settings.profileBackground)
    }
}
