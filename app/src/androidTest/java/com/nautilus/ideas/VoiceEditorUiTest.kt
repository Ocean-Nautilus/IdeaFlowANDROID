package com.nautilus.ideas

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceEditorUiTest {
    @Test
    fun recognitionResultAppendsSurvivesRecreationAndCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.addIdeaButton)).perform(click())
            // Wait for the actual editor state, without writing any user records.
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            var ready = false
            while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ready = it.findViewById<android.view.View>(R.id.voiceInputButton)?.isEnabled == true }
                if (!ready) android.os.SystemClock.sleep(50)
            }
            onView(withId(R.id.contentInput)).perform(replaceText("Draft"), closeSoftKeyboard())
            fun recognize(code: Int, data: Intent?) {
                val monitor = instrumentation.addMonitor(
                    IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                    Instrumentation.ActivityResult(code, data), true
                )
                try {
                    onView(withId(R.id.voiceInputButton)).perform(scrollTo(), click())
                    assertEquals(1, monitor.hits)
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }
            recognize(Activity.RESULT_OK, Intent().putStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS, arrayListOf("Распознанная мысль", "Альтернатива")))
            onView(withId(R.id.contentInput)).check(matches(withText("Draft\nРаспознанная мысль")))
            scenario.recreate()
            onView(withId(R.id.contentInput)).check(matches(withText("Draft\nРаспознанная мысль")))
            recognize(Activity.RESULT_CANCELED, null)
            onView(withId(R.id.contentInput)).check(matches(withText("Draft\nРаспознанная мысль")))
            recognize(Activity.RESULT_OK, Intent())
            onView(withId(R.id.contentInput)).check(matches(withText("Draft\nРаспознанная мысль")))
        }
    }
}
