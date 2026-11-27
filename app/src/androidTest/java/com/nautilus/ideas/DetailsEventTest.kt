package com.nautilus.ideas

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.repository.IdeaRepository
import com.nautilus.ideas.viewmodels.DetailsEvent
import com.nautilus.ideas.viewmodels.IdeaDetailsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsEventTest {
    @Test fun deletionSurvivesMissingEventSubscriber(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val category = db.categoryDao().insert(Category(name = "Тест", colorHex = "#123456"))
            val id = db.ideaDao().insert(Idea(title = "Удалить", content = "", categoryId = category))
            lateinit var model: IdeaDetailsViewModel
            instrumentation.runOnMainSync {
                model = IdeaDetailsViewModel(IdeaRepository.from(db), id)
                store.put("details", model)
            }
            withTimeout(5_000) { model.uiState.first { it.idea != null } }
            instrumentation.runOnMainSync { model.delete() }
            withTimeout(5_000) { db.ideaDao().observeAll().first { it.isEmpty() } }
            assertEquals(DetailsEvent.Deleted, withTimeout(5_000) { model.events.first() })
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }
}
