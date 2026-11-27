package com.nautilus.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.repository.IdeaRepository
import com.nautilus.ideas.viewmodels.EditorEvent
import com.nautilus.ideas.viewmodels.IdeaEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EditorRecoveryTest {
    @Test fun draftRestoresFieldsAndMissingIdeaCannotBecomeNew(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = IdeaRepository.from(db)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val categoryId = db.categoryDao().insert(Category(name = "Тест", colorHex = "#123456"))
            val handle = SavedStateHandle()
            lateinit var editor: IdeaEditorViewModel
            instrumentation.runOnMainSync { editor = IdeaEditorViewModel(repository, -1, handle); store.put("editor", editor) }
            withTimeout(5_000) { editor.uiState.first { !it.isLoading } }
            instrumentation.runOnMainSync {
                editor.onTitleChanged("Черновик")
                editor.onVoiceRecognized("Длинная мысль")
                editor.onTagsChanged("проект")
                editor.onRatingChanged(4)
            }
            instrumentation.waitForIdleSync()
            val restoredHandle = SavedStateHandle(mapOf("draft" to handle.get<android.os.Bundle>("draft")))
            lateinit var restored: IdeaEditorViewModel
            instrumentation.runOnMainSync { restored = IdeaEditorViewModel(repository, -1, restoredHandle); store.put("restored", restored) }
            val state = withTimeout(5_000) { restored.uiState.first { !it.isLoading } }
            assertEquals("Черновик", state.title)
            assertEquals("Длинная мысль", state.content)
            assertEquals("проект", state.tagsText)
            assertEquals(4, state.rating)
            assertTrue(restored.hasUnsavedChanges)
            instrumentation.runOnMainSync { restored.discardDraft() }
            assertNull(restoredHandle.get<android.os.Bundle>("draft"))

            val id = db.ideaDao().insert(Idea(title = "Удалённая", content = "", categoryId = categoryId))
            db.ideaDao().delete(requireNotNull(db.ideaDao().getById(id)).idea)
            lateinit var missing: IdeaEditorViewModel
            instrumentation.runOnMainSync { missing = IdeaEditorViewModel(repository, id); store.put("missing", missing) }
            withTimeout(5_000) { missing.uiState.first { !it.isLoading } }
            instrumentation.runOnMainSync {
                missing.onTitleChanged("Не создавать копию")
                missing.save()
            }
            assertTrue(withTimeout(5_000) { missing.events.first() } is EditorEvent.SaveFailed)
            assertTrue(db.ideaDao().getAll().isEmpty())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }

    @Test fun savingWithoutCategoryReportsError(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            lateinit var editor: IdeaEditorViewModel
            instrumentation.runOnMainSync { editor = IdeaEditorViewModel(IdeaRepository.from(db), -1); store.put("editor", editor) }
            withTimeout(5_000) { editor.uiState.first { !it.isLoading } }
            instrumentation.runOnMainSync { editor.onTitleChanged("Идея"); editor.save() }
            assertEquals(EditorEvent.MissingCategory, withTimeout(5_000) { editor.events.first() })
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }
}
