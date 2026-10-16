package com.nautilus.ideas.utils

import android.content.Context
import android.content.res.Configuration
import com.nautilus.ideas.R
import java.util.Locale

object IdeaCountFormatter {
    fun format(context: Context, count: Int): String {
        // Интерфейс русский даже на телефоне с другим системным языком.
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru"))
        }
        return context.createConfigurationContext(config).resources
            .getQuantityString(R.plurals.ideas_count, count, count)
    }
}
