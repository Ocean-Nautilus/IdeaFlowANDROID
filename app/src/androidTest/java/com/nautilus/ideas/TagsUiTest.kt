package com.nautilus.ideas

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.model.Idea
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class TagsUiTest {
    @Test
    fun optionalTagsShareOneEntityAndEditingOnlyChangesOneIdeasLinks(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IdeasApp
        val repository = app.repository
        val beforeIdeas = repository.observeIdeas().first()
        val beforeTags = app.database.tagDao().getAll()
        val suffix = System.nanoTime().toString()
        val title = "TagsQA_$suffix"
        val otherTitle = "TagsQA_Other_$suffix"
        val shared = "sharedqa$suffix"
        val second = "secondqa$suffix"
        val draft = "$shared, $second, ${shared.uppercase()}"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.addIdeaButton)).perform(click())
            onView(withId(R.id.titleInput)).perform(replaceText(title))
            onView(withId(R.id.tagsInput)).perform(scrollTo(), replaceText(draft), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.tagsInput)).check(matches(withText(draft)))
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.saveButton)).perform(click())
            val firstIdea = withTimeout(5_000) {
                repository.observeIdeas(query = title).first { rows -> rows.any { it.idea.title == title } }
                    .single { it.idea.title == title }
            }
            assertEquals(listOf(second, shared).sorted(), firstIdea.tags.map { it.name }.sorted())
            val otherId = repository.saveIdea(Idea(title = otherTitle, content = "", categoryId = firstIdea.idea.categoryId), listOf(shared))
            assertEquals(firstIdea.tags.single { it.name == shared }.id,
                requireNotNull(repository.getIdea(otherId)).tags.single().id)
            onView(withId(R.id.searchInput)).perform(replaceText(title), closeSoftKeyboard())
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(allOf(withId(R.id.titleText), withText(title))).perform(click())
            onView(withText(shared)).check(matches(isDisplayed()))
            onView(withText(second)).check(matches(isDisplayed()))
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.editButton)).perform(click())
            val editDeadline = android.os.SystemClock.uptimeMillis() + 5_000
            var editorReady = false
            while (!editorReady && android.os.SystemClock.uptimeMillis() < editDeadline) {
                scenario.onActivity { editorReady = it.findViewById<android.view.View>(R.id.voiceInputButton)?.isEnabled == true }
                if (!editorReady) android.os.SystemClock.sleep(50)
            }
            assertTrue("Editor did not open", editorReady)
            onView(withId(R.id.tagsInput)).perform(scrollTo(), replaceText(""), closeSoftKeyboard())
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            onView(withId(R.id.saveButton)).perform(click())
            withTimeout(5_000) { repository.observeIdea(firstIdea.idea.id).first { it?.tags?.isEmpty() == true } }
            onView(withId(R.id.tagChips)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            assertEquals(listOf(shared), requireNotNull(repository.getIdea(otherId)).tags.map { it.name })
        } finally {
            scenario.close()
            repository.observeIdeas().first().filter { it.idea.title == title || it.idea.title == otherTitle }
                .forEach { repository.deleteIdea(it.idea) }
            app.database.tagDao().getAll().filter { it.name == shared || it.name == second }.forEach { tag ->
                val references = app.database.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM idea_tag_cross_ref WHERE tagId = ?", arrayOf(tag.id)).use {
                    it.moveToFirst(); it.getInt(0)
                }
                if (references == 0) app.database.tagDao().delete(tag)
            }
            assertEquals(beforeIdeas, repository.observeIdeas().first())
            assertEquals(beforeTags, app.database.tagDao().getAll())
        }
    }
}
