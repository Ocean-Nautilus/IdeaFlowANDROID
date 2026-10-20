package com.nautilus.ideas

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoriesUiTest {
    @Test
    fun createRenameRotateAndDeleteOnlyTestCategory(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IdeasApp
        val repository = app.repository
        val preferences = app.getSharedPreferences("settings", 0)
        val beforePalette = preferences.getString("category_colors", null)
        val colorName = "QA${System.nanoTime() % 1_000_000_000}"
        val beforeIdeas = repository.observeIdeas().first()
        val beforeCategories = repository.observeCategories().first()
        val name = "CategoryQA_${System.nanoTime()}"
        val renamed = "${name}_R"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.categoriesButton)).perform(scrollTo(), click())
            onView(withId(R.id.deleteCategoryButton)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            onView(withId(R.id.addCategoryButton)).perform(click())
            onView(withId(R.id.categoryNameInput)).perform(replaceText(name), closeSoftKeyboard())
            onView(withText("Фиолетовый")).perform(scrollTo(), click())
            onView(withId(R.id.createColorButton)).perform(scrollTo(), click())
            onView(withId(android.R.id.button2)).perform(click())
            onView(withText("Фиолетовый")).check(matches(isChecked()))
            onView(withId(R.id.createColorButton)).perform(scrollTo(), click())
            onView(withId(R.id.colorNameInput)).perform(replaceText(colorName), closeSoftKeyboard())
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
                val fragment = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                val picker = fragment.childFragmentManager.findFragmentByTag("colorPicker") as androidx.fragment.app.DialogFragment
                val dialog = requireNotNull(picker.dialog)
                dialog.findViewById<com.google.android.material.slider.Slider>(R.id.redSlider).value = 18f
                dialog.findViewById<com.google.android.material.slider.Slider>(R.id.greenSlider).value = 128f
                dialog.findViewById<com.google.android.material.slider.Slider>(R.id.blueSlider).value = 224f
            }
            scenario.recreate()
            onView(withId(R.id.colorNameInput)).check(matches(withText(colorName)))
            onView(withId(android.R.id.button1)).perform(click())
            scenario.recreate()
            onView(withId(R.id.categoryNameInput)).check(matches(withText(name)))
            onView(withText(colorName)).check(matches(isChecked()))
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.saveCategoryButton)).perform(scrollTo(), click())
            withTimeout(5_000) { repository.observeCategories().first { rows -> rows.any { it.name == name } } }
            assertEquals("#1280E0", repository.observeCategories().first().single { it.name == name }.colorHex)
            onView(withText(name)).perform(scrollTo(), click())
            onView(withId(R.id.categoryNameInput)).perform(replaceText(renamed), closeSoftKeyboard())
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.saveCategoryButton)).perform(scrollTo(), click())
            withTimeout(5_000) { repository.observeCategories().first { rows -> rows.any { it.name == renamed } } }
            onView(withText(renamed)).perform(scrollTo(), click())
            onView(withId(R.id.deleteCategoryButton)).perform(scrollTo(), click())
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals(1, repository.observeCategories().first().count { it.name == renamed })
            onView(withId(R.id.deleteCategoryButton)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())
            withTimeout(5_000) { repository.observeCategories().first { rows -> rows.none { it.name == renamed } } }
        } finally {
            scenario.close()
            preferences.edit().apply {
                if (beforePalette == null) remove("category_colors") else putString("category_colors", beforePalette)
            }.commit()
            repository.observeCategories().first().filter { it.name == name || it.name == renamed }.forEach {
                repository.deleteCategory(it, 0)
            }
            assertEquals(beforeCategories, repository.observeCategories().first())
            assertEquals(beforeIdeas, repository.observeIdeas().first())
        }
    }
}
