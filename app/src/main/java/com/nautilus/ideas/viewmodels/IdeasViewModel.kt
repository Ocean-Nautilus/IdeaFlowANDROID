package com.nautilus.ideas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.model.SortOption
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class IdeasUiState(
    val ideas: List<IdeaWithDetails> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val statusFilter: IdeaStatus? = null,
    val categoryFilter: Category? = null,
    val categories: List<Category> = emptyList(),
    val sort: SortOption = SortOption.DATE_DESC
) {
    val isEmpty: Boolean get() = !isLoading && ideas.isEmpty()

    /** Пусто из-за поиска или фильтров, а не потому что идей нет вообще. */
    val isEmptyFromFilters: Boolean
        get() = isEmpty && (query.isNotBlank() || statusFilter != null || categoryFilter != null)
}

private data class FilterState(
    val query: String = "",
    val status: IdeaStatus? = null,
    val category: Category? = null,
    val sort: SortOption = SortOption.DATE_DESC
)

class IdeasViewModel(repository: IdeaRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<IdeaStatus?>(null)
    private val _categoryFilter = MutableStateFlow<Category?>(null)
    private val _sort = MutableStateFlow(SortOption.DATE_DESC)

    private val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    private val filterState: StateFlow<FilterState> = combine(
        _query.debounce(250).distinctUntilChanged(),
        _statusFilter,
        combine(_categoryFilter, repository.observeCategories()) { selected, current ->
            current.find { it.id == selected?.id }
        }.distinctUntilChanged(),
        _sort
    ) { query, status, category, sort -> FilterState(query, status, category, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredIdeas = filterState.flatMapLatest { f ->
        repository.observeIdeas(f.query, f.status, f.category?.id, f.sort)
    }

    val uiState: StateFlow<IdeasUiState> = combine(
        filteredIdeas, filterState, categories
    ) { ideas, filters, cats ->
        IdeasUiState(
            ideas = ideas,
            isLoading = false,
            query = filters.query,
            statusFilter = filters.status,
            categoryFilter = filters.category,
            categories = cats,
            sort = filters.sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IdeasUiState()
    )

    fun onQueryChanged(query: String) { _query.value = query }
    fun onStatusFilterChanged(status: IdeaStatus?) { _statusFilter.value = status }
    fun onCategoryFilterChanged(category: Category?) { _categoryFilter.value = category }
    fun onSortChanged(sort: SortOption) { _sort.value = sort }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                IdeasViewModel(app.repository)
            }
        }
    }
}
