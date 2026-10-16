package com.nautilus.ideas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.IdeaBackup
import com.nautilus.ideas.data.ImportResult
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdeaBackupTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val category = source.categoryDao().insert(Category(name = "Импорт", colorHex = "#123456", createdAt = 123))
        source.categoryDao().insert(Category(name = "Пустая", colorHex = "#ABCDEF", createdAt = 456))
        val tag = source.tagDao().insert(Tag(name = "проект"))
        source.tagDao().insert(Tag(name = "неиспользуемый"))
        repeat(2) {
            val id = source.ideaDao().insert(Idea(title = "Копия", content = "Строка\n\"Текст\" 💡",
                categoryId = category, status = IdeaStatus.IN_PROGRESS, rating = 5,
                isVoiceNote = true, createdAt = 1_000, updatedAt = 2_000))
            source.tagDao().linkTagToIdea(IdeaTagCrossRef(id, tag))
        }
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun roundTripPreservesFieldsLinksAndIntentionalCopies() = runBlocking {
        val localId = target.categoryDao().insert(Category(name = "Локальная", colorHex = "#FFFFFF"))
        val json = IdeaBackup(source).exportJson()
        val backup = IdeaBackup(target)
        assertEquals(ImportResult(2, 0), backup.importJson(json))
        val actual = target.ideaDao().getAll()
        val expected = source.ideaDao().getAll().first()
        assertEquals(2, actual.size)
        actual.forEach {
            assertEquals(expected.idea.copy(id = 0, categoryId = 0), it.idea.copy(id = 0, categoryId = 0))
            assertEquals("Импорт", it.category.name)
            assertEquals(listOf("проект"), it.tags.map { tag -> tag.name })
            assertNotEquals(localId, it.idea.categoryId)
        }
        assertEquals(3, target.categoryDao().getAll().size)
        assertEquals(2, target.tagDao().getAll().size)
        assertEquals(ImportResult(0, 2), backup.importJson(json))
        assertEquals(2, target.ideaDao().getAll().size)
        assertEquals("Локальная", target.categoryDao().getById(localId)?.name)
    }

    @Test
    fun invalidFilesNeverChangeDatabase() = runBlocking {
        val valid = IdeaBackup(source).exportJson()
        val backup = IdeaBackup(target)
        val before = backup.exportJson()
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 999) },
            { it.put("format", "other") },
            { it.getJSONArray("ideas").getJSONObject(0).put("rating", 6) },
            { it.getJSONArray("ideas").getJSONObject(0).put("rating", 1.5) },
            { it.getJSONArray("ideas").getJSONObject(0).put("categoryId", 999) },
            { it.getJSONArray("ideas").getJSONObject(0).getJSONArray("tagIds").put(999) },
            { it.getJSONArray("ideas").getJSONObject(0).put("title", " ") },
            { it.getJSONArray("ideas").getJSONObject(0).put("status", "UNKNOWN") },
            { it.getJSONArray("ideas").getJSONObject(0).put("isVoiceNote", "true") },
            { it.getJSONArray("categories").getJSONObject(0).put("colorHex", "invalid") },
            { it.getJSONArray("ideas").getJSONObject(1).put("id", it.getJSONArray("ideas").getJSONObject(0).getLong("id")) }
        )
        val invalid = mutations.map { mutation -> JSONObject(valid).also(mutation).toString() } +
            listOf("{", valid + "garbage", " ".repeat(IdeaBackup.MAX_FILE_BYTES + 1))
        for (json in invalid) {
            try {
                backup.importJson(json)
                fail("Invalid file was accepted")
            } catch (_: Exception) {
                assertEquals(before, backup.exportJson())
            }
        }
    }

    @Test
    fun writeFailureRollsBackIdeasCategoriesAndTags() = runBlocking {
        val backup = IdeaBackup(target)
        val before = backup.exportJson()
        val json = JSONObject(IdeaBackup(source).exportJson())
        json.getJSONArray("ideas").getJSONObject(1).put("title", "Блокировка")
        target.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_import BEFORE INSERT ON ideas
            WHEN NEW.title = 'Блокировка'
            BEGIN SELECT RAISE(ABORT, 'test write failure'); END
        """.trimIndent())
        try {
            backup.importJson(json.toString())
            fail("Write failure was ignored")
        } catch (_: Exception) {
            assertEquals(before, backup.exportJson())
        }
    }

    @Test
    fun emptyBackupIsValid() = runBlocking {
        val backup = IdeaBackup(target)
        assertEquals(ImportResult(0, 0), backup.importJson(backup.exportJson()))
    }
}
