package com.nautilus.ideas.viewmodels

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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
    val tagsText: String = "",
    val originalTagNames: List<String> = emptyList(),
    val isVoiceNote: Boolean = false,
    val isSaving: Boolean = false
)

/** Разовые события: показать ошибку, закрыть экран. */
sealed interface EditorEvent {
    /** Идея сохранена, можно возвращаться к списку. */
    data object Saved : EditorEvent

    /** Заголовок пустой - подсветить поле. */
    data object EmptyTitle : EditorEvent
    data object MissingCategory : EditorEvent

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
    private val ideaId: Long,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState(isNew = ideaId == NEW_IDEA_ID))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Сохраняем результат, пока после поворота или системного окна снова появится получатель.
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var initial: EditorUiState? = null
    private var missingIdea = false
    val hasUnsavedChanges: Boolean
        get() = initial?.let { formValues(it) != formValues(_uiState.value) } ?: false

    private fun formValues(state: EditorUiState) = listOf(state.title, state.content, state.tagsText,
        state.categories.getOrNull(state.selectedCategoryIndex)?.id, state.status, state.rating, state.isVoiceNote)

    fun discardDraft() { savedState.remove<Bundle>("draft") }

    init {
        loadInitialData()
        viewModelScope.launch {
            uiState.collect { state ->
                if (!state.isLoading && hasUnsavedChanges) {
                    savedState["draft"] = Bundle().apply {
                        putString("title", state.title)
                        putString("content", state.content)
                        putString("tags", state.tagsText)
                        putLong("category", state.categories.getOrNull(state.selectedCategoryIndex)?.id ?: -1)
                        putString("status", state.status.name)
                        putInt("rating", state.rating)
                        putBoolean("voice", state.isVoiceNote)
                    }
                } else if (!state.isLoading) {
                    discardDraft()
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // first() - берём текущий список категорий один раз:
            // пока форма открыта, справочник меняться не может.
            val categories = repository.observeCategories().first()
            val existing = if (ideaId == NEW_IDEA_ID) null else repository.getIdea(ideaId)

            missingIdea = ideaId != NEW_IDEA_ID && existing == null
            val loaded = EditorUiState(
                isLoading = false,
                isNew = ideaId == NEW_IDEA_ID,
                title = existing?.idea?.title.orEmpty(),
                content = existing?.idea?.content.orEmpty(),
                categories = categories,
                selectedCategoryIndex = categories
                    .indexOfFirst { it.id == existing?.idea?.categoryId }
                    // Для новой идеи и на случай удалённой категории - первая в списке.
                    .coerceAtLeast(0),
                status = existing?.idea?.status ?: IdeaStatus.NEW,
                rating = existing?.idea?.rating ?: 0,
                tagsText = existing?.tags?.map { it.name }?.sorted()?.joinToString(", ").orEmpty(),
                originalTagNames = existing?.tags?.map { it.name }?.sorted().orEmpty(),
                isVoiceNote = existing?.idea?.isVoiceNote ?: false
            )
            initial = loaded
            val draft = savedState.get<Bundle>("draft")
            _uiState.value = if (draft == null) loaded else loaded.copy(
                title = draft.getString("title").orEmpty(),
                content = draft.getString("content").orEmpty(),
                tagsText = draft.getString("tags").orEmpty(),
                selectedCategoryIndex = categories.indexOfFirst { it.id == draft.getLong("category") }.coerceAtLeast(0),
                status = IdeaStatus.entries.firstOrNull { it.name == draft.getString("status") } ?: loaded.status,
                rating = draft.getInt("rating"),
                isVoiceNote = draft.getBoolean("voice")
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

    fun onTagsChanged(value: String) {
        _uiState.value = _uiState.value.copy(tagsText = value)
    }

    fun onVoiceRecognized(value: String) {
        val text = value.trim()
        val state = _uiState.value
        if (text.isEmpty() || state.isLoading || state.isSaving) return
        _uiState.value = state.copy(
            content = if (state.content.isBlank()) text else state.content + "\n" + text,
            isVoiceNote = true
        )
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
        if (state.isLoading || state.isSaving) return
        if (missingIdea) {
            _events.trySend(EditorEvent.SaveFailed("Идея больше не существует. Закройте редактор и создайте новую идею."))
            return
        }

        if (title.isEmpty()) {
            _events.trySend(EditorEvent.EmptyTitle)
            return
        }
        val category = state.categories.getOrNull(state.selectedCategoryIndex)
        if (category == null) {
            _events.trySend(EditorEvent.MissingCategory)
            return
        }
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
                        rating = state.rating,
                        isVoiceNote = state.isVoiceNote
                    ),
                    tagNames = if (state.tagsText == state.originalTagNames.joinToString(", ")) {
                        state.originalTagNames
                    } else state.tagsText.split(',', '\n')
                )
                initial = _uiState.value
                discardDraft()
                _events.trySend(EditorEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Приложение не должно падать из-за ошибки записи -
                // требование ТЗ. Показываем сообщение и оставляем форму.
                _uiState.value = _uiState.value.copy(isSaving = false)
                _events.trySend(EditorEvent.SaveFailed(error.message))
            }
        }
    }

    companion object {
        /** Признак того, что экран открыт для создания новой идеи. */
        const val NEW_IDEA_ID = -1L

        fun factory(ideaId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                IdeaEditorViewModel(app.repository, ideaId, createSavedStateHandle())
            }
        }
    }
}
