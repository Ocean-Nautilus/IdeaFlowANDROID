package com.nautilus.ideas

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.model.IdeaStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsAndSettingsUiTest {
    @Test
    fun summariesMatchDatabaseAndImportanceSettingSurvivesRecreation(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IdeasApp
        val original = app.settings.showImportance
        val ideas = app.repository.observeIdeas().first()
        app.settings.showImportance = true
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        fun checkImportance(expected: Int) {
            if (ideas.isEmpty()) return
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            var visibility: Int? = null
            while (visibility == null && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.ideasList)
                    visibility = list.findViewHolderForAdapterPosition(0)?.itemView
                        ?.findViewById<View>(R.id.importanceLamps)?.visibility
                }
                if (visibility == null) android.os.SystemClock.sleep(50)
            }
            assertEquals(expected, visibility)
        }
        try {
            onView(withId(R.id.statsFragment)).perform(click())
            fun checkTile(id: Int, expected: Int) {
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                var displayed: String? = null
                while (displayed != expected.toString() && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        displayed = activity.findViewById<View>(id)
                            .findViewById<android.widget.TextView>(R.id.statValue).text.toString()
                    }
                    if (displayed != expected.toString()) android.os.SystemClock.sleep(50)
                }
                onView(allOf(withId(R.id.statValue), isDescendantOfA(withId(id))))
                    .check(matches(withText(expected.toString())))
            }
            checkTile(R.id.totalStat, ideas.size)
            checkTile(R.id.newStat, ideas.count { it.idea.status == IdeaStatus.NEW })
            checkTile(R.id.inProgressStat, ideas.count { it.idea.status == IdeaStatus.IN_PROGRESS })
            checkTile(R.id.completedStat, ideas.count { it.idea.status == IdeaStatus.DONE })
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.showImportanceSwitch)).perform(scrollTo(), click())
            assertFalse(app.settings.showImportance)
            scenario.recreate()
            onView(withId(R.id.showImportanceSwitch)).check(matches(not(isChecked())))
            onView(withId(R.id.ideasFragment)).perform(click())
            checkImportance(View.GONE)
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.showImportanceSwitch)).perform(scrollTo(), click())
            assertTrue(app.settings.showImportance)
            onView(withId(R.id.ideasFragment)).perform(click())
            checkImportance(View.VISIBLE)
        } finally {
            scenario.close()
            app.settings.showImportance = original
            assertEquals(ideas, app.repository.observeIdeas().first())
        }
    }
}
