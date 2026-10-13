package com.nautilus.ideas.utils

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.nautilus.ideas.R
import com.nautilus.ideas.data.model.IdeaStatus

/**
 * Как показывать статус идеи на экране.
 *
 * Вынесено из [IdeaStatus] намеренно: сама сущность не должна знать
 * ни про строковые ресурсы, ни про цвета — иначе слой данных окажется
 * привязан к интерфейсу, и его нельзя будет протестировать отдельно.
 */
object IdeaStatusUi {

    @StringRes
    fun labelOf(status: IdeaStatus): Int = when (status) {
        IdeaStatus.NEW -> R.string.status_new
        IdeaStatus.IN_PROGRESS -> R.string.status_in_progress
        IdeaStatus.DONE -> R.string.status_done
        IdeaStatus.POSTPONED -> R.string.status_postponed
    }

    @ColorRes
    fun colorOf(status: IdeaStatus): Int = when (status) {
        IdeaStatus.NEW -> R.color.status_new
        IdeaStatus.IN_PROGRESS -> R.color.status_in_progress
        IdeaStatus.DONE -> R.color.status_done
        IdeaStatus.POSTPONED -> R.color.status_postponed
    }
}
