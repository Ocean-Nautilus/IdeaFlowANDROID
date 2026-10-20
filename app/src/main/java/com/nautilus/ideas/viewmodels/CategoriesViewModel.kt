package com.nautilus.ideas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.CategoryCount
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val rows: List<CategoryCount> = emptyList(),
    val editingId: Long = 0,
    val name: String = "",
    val color: String = "#4DD0C4",
    val isBusy: Boolean = false,
    val isFormOpen: Boolean = false,
    val message: String? = null
)

class CategoriesViewModel(private val repository: IdeaRepository) : ViewModel() {
    private val state = MutableStateFlow(CategoriesUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCountByCategory().collect { rows -> state.update { it.copy(rows = rows) } }
        }
    }

    fun nameChanged(value: String) { state.update { it.copy(name = value) } }
    fun colorChanged(value: String) { state.update { it.copy(color = value) } }
    fun edit(row: CategoryCount) {
        if (!state.value.isBusy) state.update {
            it.copy(editingId = row.categoryId, name = row.categoryName, color = row.colorHex, isFormOpen = true)
        }
    }
    fun add() { state.update { it.copy(editingId = 0, name = "", color = "#4DD0C4", isFormOpen = true) } }
    fun cancelEdit() { state.update { it.copy(editingId = 0, name = "", color = "#4DD0C4", isFormOpen = false) } }
    fun clearMessage() { state.update { it.copy(message = null) } }

    fun save(successMessage: String) {
        val form = state.value
        runOperation(successMessage) {
            repository.saveCategory(Category(id = form.editingId, name = form.name, colorHex = form.color.trim()))
            cancelEdit()
        }
    }

    fun delete(row: CategoryCount, successMessage: String) = runOperation(successMessage) {
        repository.deleteCategory(Category(id = row.categoryId, name = row.categoryName, colorHex = row.colorHex), row.amount)
        if (state.value.editingId == row.categoryId) cancelEdit()
    }

    private fun runOperation(successMessage: String, action: suspend () -> Unit) {
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                action()
                state.update { it.copy(isBusy = false, message = successMessage) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(isBusy = false, message = error.message ?: "Не удалось изменить категорию") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                CategoriesViewModel(app.repository)
            }
        }
    }
}
