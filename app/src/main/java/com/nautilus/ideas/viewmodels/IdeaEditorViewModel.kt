package com.nautilus.ideas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Состояние экрана создания и редактирования идеи. */
data class EditorUiState(
    val isLoading: Boolean = true,
    /** true - создаём новую идею, false - правим существующую. */
    val isNew: Boolean = true,
    val title: String = "",
    val content: String = "",
    val categories: List<Category> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val status: IdeaStatus = IdeaStatus.NEW,
    val rating: Int = 0,
    /** Теги существующей идеи. Экран их пока не правит (неделя 7),
     *  но обязан сохранить, иначе при редактировании они пропадут. */
    val tagNames: List<String> = emptyList(),
    val isSaving: Boolean = false
)

/** Разовые события: показать ошибку, закрыть экран. */
sealed interface EditorEvent {
    /** Идея сохранена, можно возвращаться к списку. */
    data object Saved : EditorEvent

    /** Заголовок пустой - подсветить поле. */
    data object EmptyTitle : EditorEvent

    /** Не удалось сохранить: место на диске, повреждённая база и т.п. */
    data class SaveFailed(val reason: String?) : EditorEvent
}

/**
 * ViewModel экрана редактора.
 *
 * Один и тот же экран работает в двух режимах. Отличает их [ideaId]:
 * [NEW_IDEA_ID] означает «создаём новую», любое другое число —
 * «открываем существующую на правку».
 */
class IdeaEditorViewModel(
    private val repository: IdeaRepository,
    private val ideaId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState(isNew = ideaId == NEW_IDEA_ID))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // extraBufferCapacity = 1, чтобы tryEmit срабатывал даже когда
    // экран в этот момент не слушает (например, во время поворота).
    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = _events

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // first() - берём текущий список категорий один раз:
            // пока форма открыта, справочник меняться не может.
            val categories = repository.observeCategories().first()
            val existing = if (ideaId == NEW_IDEA_ID) null else repository.getIdea(ideaId)

            _uiState.value = EditorUiState(
                isLoading = false,
                isNew = existing == null,
                title = existing?.idea?.title.orEmpty(),
                content = existing?.idea?.content.orEmpty(),
                categories = categories,
                selectedCategoryIndex = categories
                    .indexOfFirst { it.id == existing?.idea?.categoryId }
                    // Для новой идеи и на случай удалённой категории - первая в списке.
                    .coerceAtLeast(0),
                status = existing?.idea?.status ?: IdeaStatus.NEW,
                rating = existing?.idea?.rating ?: 0,
                tagNames = existing?.tags?.map { it.name }.orEmpty()
            )
        }
    }

    // --- изменения полей формы ---

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onContentChanged(value: String) {
        _uiState.value = _uiState.value.copy(content = value)
    }

    fun onCategorySelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedCategoryIndex = index)
    }

    fun onStatusSelected(status: IdeaStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating)
    }

    /**
     * Проверяет форму и сохраняет идею.
     *
     * Проверка живёт здесь, а не во фрагменте: правила «что считать
     * корректной идеей» относятся к логике приложения, и при смене
     * разметки их не придётся переносить.
     */
    fun save() {
        val state = _uiState.value
        val title = state.title.trim()

        if (title.isEmpty()) {
            _events.tryEmit(EditorEvent.EmptyTitle)
            return
        }
        val category = state.categories.getOrNull(state.selectedCategoryIndex) ?: return
        if (state.isSaving) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                repository.saveIdea(
                    idea = Idea(
                        id = if (state.isNew) 0 else ideaId,
                        title = title,
                        content = state.content.trim(),
                        categoryId = category.id,
                        status = state.status,
                        rating = state.rating
                    ),
                    tagNames = state.tagNames
                )
                _events.tryEmit(EditorEvent.Saved)
            } catch (error: Exception) {
                // Приложение не должно падать из-за ошибки записи -
                // требование ТЗ. Показываем сообщение и оставляем форму.
                _uiState.value = _uiState.value.copy(isSaving = false)
                _events.tryEmit(EditorEvent.SaveFailed(error.message))
            }
        }
    }

    companion object {
        /** Признак того, что экран открыт для создания новой идеи. */
        const val NEW_IDEA_ID = -1L

        fun factory(ideaId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                IdeaEditorViewModel(app.repository, ideaId)
            }
        }
    }
}
