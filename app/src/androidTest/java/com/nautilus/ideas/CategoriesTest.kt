package com.nautilus.ideas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoriesTest {
    @Test
    fun categoryFilterTracksRenameAndDeletion(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = IdeaRepository.from(database)
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: com.nautilus.ideas.viewmodels.IdeasViewModel
        try {
            val category = Category(name = "Первая", colorHex = "#123456")
            val first = repository.saveCategory(category)
            val second = repository.saveCategory(category.copy(name = "Вторая"))
            repository.saveIdea(Idea(title = "Осталась", content = "", categoryId = second), emptyList())
            instrumentation.runOnMainSync {
                val owner = object : androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore = store }
                model = androidx.lifecycle.ViewModelProvider(owner, object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.nautilus.ideas.viewmodels.IdeasViewModel(repository) as T
                })[com.nautilus.ideas.viewmodels.IdeasViewModel::class.java]
                model.onCategoryFilterChanged(category.copy(id = first))
            }
            withTimeout(5_000) { model.uiState.first { !it.isLoading && it.categoryFilter?.id == first } }
            repository.saveCategory(category.copy(id = first, name = "Переименована"))
            withTimeout(5_000) { model.uiState.first { it.categoryFilter?.name == "Переименована" } }
            repository.deleteCategory(category.copy(id = first), 0)
            withTimeout(5_000) { model.uiState.first { !it.isLoading && it.categoryFilter == null && it.ideas.size == 1 } }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            database.close()
        }
    }

    @Test
    fun validationRenameAndConfirmedCascadeAreSafe() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java).build()
        val repository = IdeaRepository.from(database)
        try {
            val first = repository.saveCategory(Category(name = "  Учёба  ", colorHex = "#123456", createdAt = 123))
            val second = repository.saveCategory(Category(name = "Работа", colorHex = "#654321"))
            assertEquals("Учёба", database.categoryDao().getById(first)?.name)
            suspend fun rejected(action: suspend () -> Unit) {
                try { action(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
            }
            rejected { repository.saveCategory(Category(name = "учёба", colorHex = "#123456")) }
            rejected { repository.saveCategory(Category(name = " ", colorHex = "#123456")) }
            rejected { repository.saveCategory(Category(name = "x".repeat(41), colorHex = "#123456")) }
            rejected { repository.saveCategory(Category(name = "Новая", colorHex = "broken")) }
            val ideaId = repository.saveIdea(Idea(title = "Связанная идея", content = "Текст", categoryId = first), emptyList())
            repository.saveCategory(Category(id = first, name = "Лекции", colorHex = "#ABCDEF"))
            assertEquals(123L, database.categoryDao().getById(first)?.createdAt)
            assertEquals("Лекции", repository.getIdea(ideaId)?.category?.name)
            rejected { repository.deleteCategory(requireNotNull(database.categoryDao().getById(first)), 0) }
            assertNotNull(repository.getIdea(ideaId))
            repository.deleteCategory(requireNotNull(database.categoryDao().getById(first)), 1)
            assertNull(repository.getIdea(ideaId))
            rejected { repository.deleteCategory(requireNotNull(database.categoryDao().getById(second)), 0) }
            assertEquals(1, database.categoryDao().getAll().size)
        } finally { database.close() }
    }
}
