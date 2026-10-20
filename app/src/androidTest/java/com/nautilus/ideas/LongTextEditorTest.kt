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
        lateinit var editorModel: com.nautilus.ideas.viewmodels.IdeaEditorViewModel
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
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
                val editor = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                editorModel = androidx.lifecycle.ViewModelProvider(editor)[com.nautilus.ideas.viewmodels.IdeaEditorViewModel::class.java]
                val state = editorModel.uiState.value
                assertEquals("ViewModel title after rotation", title, state.title)
                assertEquals("ViewModel content after rotation", content, state.content)
                assertTrue("Selected category must exist", state.categories.getOrNull(state.selectedCategoryIndex) != null)
            }
            // Rotation can reopen the IME and move the footer during a coordinate-based click.
            onView(withId(R.id.saveButton)).perform(closeSoftKeyboard())
            androidx.test.espresso.Espresso.onIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.saveButton)).perform(click())
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
