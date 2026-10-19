package com.nautilus.ideas

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.repository.IdeaRepository
import com.nautilus.ideas.viewmodels.IdeaEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputTest {
    @Test
    fun dictationAppendsAndPersistsForNewAndExistingIdeas() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = IdeaRepository.from(database)
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        fun editor(id: Long): IdeaEditorViewModel {
            lateinit var model: IdeaEditorViewModel
            instrumentation.runOnMainSync {
                model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        IdeaEditorViewModel(repository, id) as T
                })[id.toString(), IdeaEditorViewModel::class.java]
            }
            return model
        }
        try {
            val categoryId = database.categoryDao().insert(Category(name = "Тест", colorHex = "#123456"))
            val existingId = repository.saveIdea(Idea(title = "Старая", content = "Текст", categoryId = categoryId,
                createdAt = 123L), emptyList())
            for (id in listOf(IdeaEditorViewModel.NEW_IDEA_ID, existingId)) {
                val model = editor(id)
                withTimeout(5_000) { model.uiState.first { !it.isLoading } }
                instrumentation.runOnMainSync {
                    model.onTitleChanged("Голос $id")
                    val before = model.uiState.value
                    model.onVoiceRecognized("  ")
                    assertEquals(before, model.uiState.value)
                    model.onVoiceRecognized("  Первая мысль  ")
                    model.onVoiceRecognized("Вторая мысль")
                }
                val prefix = if (id == existingId) "Текст\n" else ""
                val expected = prefix + "Первая мысль\nВторая мысль"
                assertEquals(expected, model.uiState.value.content)
                assertTrue(model.uiState.value.isVoiceNote)
                instrumentation.runOnMainSync { model.save() }
                val saved = withTimeout(5_000) {
                    repository.observeIdeas().first { rows -> rows.any { it.idea.title == "Голос $id" } }
                        .single { it.idea.title == "Голос $id" }.idea
                }
                assertEquals(expected, saved.content)
                assertTrue(saved.isVoiceNote)
                if (id == existingId) assertEquals(123L, saved.createdAt)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            database.close()
        }
    }
}
