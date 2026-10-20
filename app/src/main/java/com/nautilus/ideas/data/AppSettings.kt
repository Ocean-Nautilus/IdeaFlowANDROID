package com.nautilus.ideas.data

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject

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

    val categoryColors: Map<String, String>
        get() = runCatching {
            val json = JSONObject(preferences.getString("category_colors", "{}") ?: "{}")
            json.keys().asSequence().filter { it.matches(Regex("#[0-9A-F]{6}")) }
                .mapNotNull { color ->
                    (json.opt(color) as? String)?.takeIf { it.isNotBlank() && it.length <= 24 }
                        ?.let { color to it }
                }.toMap()
        }.getOrDefault(emptyMap())

    fun saveCategoryColor(name: String, color: String) {
        val label = name.trim()
        val hex = color.uppercase()
        require(label.isNotEmpty() && label.length <= 24) { "Название цвета должно содержать от 1 до 24 символов" }
        require(hex.matches(Regex("#[0-9A-F]{6}"))) { "Некорректный цвет" }
        val colors = categoryColors
        require(colors.none { it.key != hex && it.value.equals(label, ignoreCase = true) }) {
            "Цвет с таким названием уже есть"
        }
        preferences.edit().putString("category_colors", JSONObject(colors + (hex to label)).toString()).apply()
    }

    companion object {
        val THEME_MODES = listOf(AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO)
    }
}
