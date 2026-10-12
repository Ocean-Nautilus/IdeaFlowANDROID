package com.nautilus.ideas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.CategoryCount
import com.nautilus.ideas.data.model.StatusCount
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val isLoading: Boolean = true,
    val total: Int = 0,
    val statusCounts: List<StatusCount> = emptyList(),
    val categoryCounts: List<CategoryCount> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && total == 0
}

class StatsViewModel(repository: IdeaRepository) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeIdeaCount(),
        repository.observeCountByStatus(),
        repository.observeCountByCategory()
    ) { total, statuses, categories ->
        StatsUiState(isLoading = false, total = total, statusCounts = statuses, categoryCounts = categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                StatsViewModel(app.repository)
            }
        }
    }
}