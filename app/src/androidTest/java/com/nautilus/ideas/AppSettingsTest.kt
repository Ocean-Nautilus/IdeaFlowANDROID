package com.nautilus.ideas

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {
    @Test
    fun savesSettingsAndFallsBackForUnknownValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("settings_test", 0)
        preferences.edit().clear().commit()
        try {
            val settings = AppSettings(preferences)
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, settings.themeMode)
            assertTrue(settings.showDescriptions)
            for (mode in AppSettings.THEME_MODES) {
                settings.themeMode = mode
                assertEquals(mode, AppSettings(preferences).themeMode)
            }
            settings.showDescriptions = false
            assertFalse(AppSettings(preferences).showDescriptions)
            preferences.edit().putInt("theme", 999).commit()
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, settings.themeMode)
            preferences.edit().putInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).commit()
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, settings.themeMode)
            assertEquals(2, AppSettings.THEME_MODES.size)
            settings.saveCategoryColor("Морской", "#1267ab")
            assertEquals(mapOf("#1267AB" to "Морской"), AppSettings(preferences).categoryColors)
            settings.saveCategoryColor("Морская волна", "#1267AB")
            assertEquals(1, settings.categoryColors.size)
            try {
                settings.saveCategoryColor("Морская волна", "#123456")
                org.junit.Assert.fail("Duplicate names must be rejected")
            } catch (_: IllegalArgumentException) { }
            preferences.edit().putString("category_colors", "{").commit()
            assertTrue(settings.categoryColors.isEmpty())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
