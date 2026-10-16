package com.nautilus.ideas

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.utils.IdeaCountFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdeaCountFormatterTest {
    @Test
    fun russianEndingsDoNotDependOnPhoneLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("en", "ru")) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(config)
            val cases = mapOf(0 to "идей", 1 to "идея", 2 to "идеи", 4 to "идеи",
                5 to "идей", 8 to "идей", 9 to "идей", 11 to "идей", 12 to "идей",
                14 to "идей", 21 to "идея", 22 to "идеи", 25 to "идей", 111 to "идей")
            for ((count, word) in cases) {
                assertEquals("$count $word", IdeaCountFormatter.format(localized, count))
            }
        }
    }
}
