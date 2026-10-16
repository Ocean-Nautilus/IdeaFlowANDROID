package com.nautilus.ideas

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.AutoCompleteTextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongTextEditorTest {
    @Test
    fun longTextSurvivesRotationAndSaving() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as IdeasApp
        val title = "CodexLongTextTest_${System.nanoTime()}"
        val content = "LongDescription ".repeat(100) + "END"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.addIdeaButton)).perform(click())
            val deadline = SystemClock.uptimeMillis() + 5_000
            var loaded = false
            while (!loaded && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    loaded = it.findViewById<AutoCompleteTextView>(R.id.categoryInput)
                        ?.text?.isNotEmpty() == true
                }
                if (!loaded) SystemClock.sleep(50)
            }
            assertTrue("Editor categories did not load", loaded)
            onView(withId(R.id.titleInput)).perform(replaceText(title))
            onView(withId(R.id.contentInput)).perform(replaceText(content), closeSoftKeyboard())
            onView(withId(R.id.titleInput)).check(matches(withText(title)))
            onView(withId(R.id.contentInput)).check(matches(withText(content)))
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val rotationDeadline = SystemClock.uptimeMillis() + 5_000
            var rotated = false
            while (!rotated && SystemClock.uptimeMillis() < rotationDeadline) {
                scenario.onActivity {
                    rotated = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                if (!rotated) SystemClock.sleep(50)
            }
            assertTrue("Screen did not rotate", rotated)
            onView(withId(R.id.contentInput)).check(matches(withText(content)))
            onView(withId(R.id.titleInput)).check(matches(withText(title)))
            onView(withId(R.id.saveButton)).perform(click())
            scenario.onActivity {
                android.util.Log.i("IdeaQA", "After save: title=" +
                    it.findViewById<android.widget.EditText>(R.id.titleInput)?.text +
                    "; category=" + it.findViewById<AutoCompleteTextView>(R.id.categoryInput)?.text +
                    "; error=" + it.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.titleLayout)?.error +
                    "; saveEnabled=" + it.findViewById<android.view.View>(R.id.saveButton)?.isEnabled)
            }
            val saved = withTimeout(5_000) {
                app.repository.observeIdeas(query = title).first { ideas ->
                    ideas.any { it.idea.title == title }
                }.single { it.idea.title == title }
            }
            assertEquals(content, saved.idea.content)
        } finally {
            app.repository.observeIdeas(query = title).first()
                .filter { it.idea.title == title }
                .forEach { app.repository.deleteIdea(it.idea) }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            scenario.close()
        }
    }
}
