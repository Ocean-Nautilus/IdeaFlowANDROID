package com.nautilus.ideas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import com.nautilus.ideas.data.repository.IdeaRepository
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

    @Test
    fun tagFailureRollsBackIdeaAndAllTagChanges(): Unit = runBlocking {
        val category = database.categoryDao().insert(Category(name = "Атомарность", colorHex = "#123456"))
        val repository = IdeaRepository.from(database)
        val id = repository.saveIdea(Idea(title = "До", content = "", categoryId = category), listOf("старый"))
        val original = requireNotNull(repository.getIdea(id))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_new_tag BEFORE INSERT ON tags
            WHEN NEW.name = 'ошибка' BEGIN SELECT RAISE(ABORT, 'test failure'); END
        """.trimIndent())
        try {
            repository.saveIdea(original.idea.copy(title = "После"), listOf("новый", "ошибка"))
            org.junit.Assert.fail("Expected a tag write failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(original, repository.getIdea(id))
        assertEquals(listOf("старый"), database.tagDao().getAll().map { it.name })
    }

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

    @Test
    fun editingPreservesCreationTimeVoiceFlagAndTags() = runBlocking {
        val categoryId = database.categoryDao()
            .insert(Category(name = "Тест", colorHex = "#D2F84B"))
        val repository = IdeaRepository.from(database)
        val id = repository.saveIdea(
            Idea(title = "До", content = "", categoryId = categoryId,
                createdAt = 123L, isVoiceNote = true),
            listOf("проект")
        )

        repository.saveIdea(
            Idea(id = id, title = "После", content = "Новый текст", categoryId = categoryId),
            listOf("проект")
        )

        val saved = requireNotNull(repository.getIdea(id))
        assertEquals(123L, saved.idea.createdAt)
        assertTrue(saved.idea.isVoiceNote)
        assertTrue(saved.idea.updatedAt > saved.idea.createdAt)
        assertEquals("После", saved.idea.title)
        assertEquals("Новый текст", saved.idea.content)
        assertEquals(listOf("проект"), saved.tags.map { it.name })
        assertEquals(1, database.ideaDao().observeCount().first())
    }
}
