package com.nautilus.ideas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.repository.IdeaRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Состояние экрана детального просмотра. */
data class DetailsUiState(
    val isLoading: Boolean = true,
    val idea: IdeaWithDetails? = null,
    val isDeleting: Boolean = false
)

/** Разовые события экрана. */
sealed interface DetailsEvent {
    /** Идея удалена - нужно вернуться к списку. */
    data object Deleted : DetailsEvent

    /** Удаление не удалось. Приложение не падает, показываем причину. */
    data class DeleteFailed(val reason: String?) : DetailsEvent
}

/**
 * ViewModel экрана детального просмотра.
 *
 * Данные приходят потоком, а не разовым запросом: если идею
 * отредактировать и вернуться, экран обновится сам.
 */
class IdeaDetailsViewModel(
    private val repository: IdeaRepository,
    private val ideaId: Long
) : ViewModel() {

    private val isDeleting = MutableStateFlow(false)

    val uiState: StateFlow<DetailsUiState> =
        combine(repository.observeIdea(ideaId), isDeleting) { idea, deleting ->
            DetailsUiState(isLoading = false, idea = idea, isDeleting = deleting)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailsUiState()
        )

    private val _events = Channel<DetailsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Удаляет идею.
     *
     * Вызывается уже после того, как пользователь подтвердил действие
     * в диалоге: сам диалог - дело экрана, а не ViewModel.
     */
    fun delete() {
        val idea = uiState.value.idea?.idea ?: return
        if (isDeleting.value) return

        isDeleting.value = true
        viewModelScope.launch {
            try {
                // Связи с тегами уйдут сами: в связующей таблице
                // внешний ключ объявлен с ON DELETE CASCADE.
                repository.deleteIdea(idea)
                _events.trySend(DetailsEvent.Deleted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                isDeleting.value = false
                _events.trySend(DetailsEvent.DeleteFailed(error.message))
            }
        }
    }

    companion object {
        fun factory(ideaId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp
                IdeaDetailsViewModel(app.repository, ideaId)
            }
        }
    }
}
