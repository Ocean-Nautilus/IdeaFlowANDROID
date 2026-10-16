package com.nautilus.ideas.data

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class AppSettings(private val preferences: SharedPreferences) {
    var themeMode: Int
        get() = preferences.getInt("theme", AppCompatDelegate.MODE_NIGHT_YES)
            .takeIf { it in THEME_MODES } ?: AppCompatDelegate.MODE_NIGHT_YES
        set(value) {
            require(value in THEME_MODES)
            preferences.edit().putInt("theme", value).apply()
        }

    var showDescriptions: Boolean
        get() = preferences.getBoolean("show_descriptions", true)
        set(value) { preferences.edit().putBoolean("show_descriptions", value).apply() }

    companion object {
        val THEME_MODES = listOf(AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
