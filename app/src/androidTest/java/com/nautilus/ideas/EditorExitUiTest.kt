package com.nautilus.ideas

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test

class EditorExitUiTest {
    @Test fun cancelKeepsTextUntilDiscardIsConfirmed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.addIdeaButton)).perform(click())
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            var loaded = false
            while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { loaded = it.findViewById<android.view.View>(R.id.voiceInputButton)?.isEnabled == true }
                if (!loaded) android.os.SystemClock.sleep(50)
            }
            onView(withId(R.id.titleInput)).perform(replaceText("Несохранённый черновик"), closeSoftKeyboard())
            androidx.test.espresso.Espresso.pressBack()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withText(R.string.draft_exit_title)).check(matches(isDisplayed()))
            onView(withText(R.string.draft_continue)).perform(click())
            onView(withId(R.id.titleInput)).check(matches(withText("Несохранённый черновик")))
            androidx.test.espresso.Espresso.pressBack()
            onView(withText(R.string.draft_discard)).perform(click())
            onView(withId(R.id.addIdeaButton)).check(matches(isDisplayed()))
        }
    }
}
