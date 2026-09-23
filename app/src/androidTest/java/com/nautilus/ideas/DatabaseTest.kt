package com.nautilus.ideas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет, что связи в базе описаны верно.
 *
 * База поднимается в памяти (inMemoryDatabaseBuilder), поэтому тест
 * не трогает реальные данные на устройстве и не оставляет следов.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /** Связь многие-ко-многим: у идеи должны читаться оба повешенных тега. */
    @Test
    fun ideaKeepsItsTags() = runBlocking {
        val categoryId = database.categoryDao()
            .insert(Category(name = "Учёба", colorHex = "#D2F84B"))
        val ideaId = database.ideaDao()
            .insert(Idea(title = "Курсовая", content = "Текст", categoryId = categoryId))

        val firstTag = database.tagDao().insert(Tag(name = "срочно"))
        val secondTag = database.tagDao().insert(Tag(name = "проект"))
        database.tagDao().linkTagToIdea(IdeaTagCrossRef(ideaId, firstTag))
        database.tagDao().linkTagToIdea(IdeaTagCrossRef(ideaId, secondTag))

        val loaded = database.ideaDao().getById(ideaId)

        assertEquals(2, loaded?.tags?.size)
        assertEquals("Учёба", loaded?.category?.name)
    }

    /** Внешний ключ с CASCADE: удаление категории уносит её идеи. */
    @Test
    fun deletingCategoryRemovesItsIdeas() = runBlocking {
        val category = Category(name = "Работа", colorHex = "#5CD16A")
        val categoryId = database.categoryDao().insert(category)
        database.ideaDao()
            .insert(Idea(title = "Отчёт", content = "Текст", categoryId = categoryId))

        assertEquals(1, database.ideaDao().observeCount().first())

        database.categoryDao().delete(category.copy(id = categoryId))

        assertEquals(0, database.ideaDao().observeCount().first())
    }

    /** Одна и та же пара идея-тег не должна задваиваться. */
    @Test
    fun sameTagIsNotLinkedTwice() = runBlocking {
        val categoryId = database.categoryDao()
            .insert(Category(name = "Личное", colorHex = "#4DD0C4"))
        val ideaId = database.ideaDao()
            .insert(Idea(title = "Пробежка", content = "Текст", categoryId = categoryId))
        val tagId = database.tagDao().insert(Tag(name = "спорт"))

        database.tagDao().linkTagToIdea(IdeaTagCrossRef(ideaId, tagId))
        database.tagDao().linkTagToIdea(IdeaTagCrossRef(ideaId, tagId))

        assertTrue(database.tagDao().getTagsOfIdea(ideaId).size == 1)
    }
}
