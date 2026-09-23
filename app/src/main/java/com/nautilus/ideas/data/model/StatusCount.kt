package com.nautilus.ideas.data.model

/**
 * Сколько идей в каждом статусе. Заполняется запросом с GROUP BY
 * и уйдёт на экран статистики (неделя 9) как данные для диаграммы.
 */
data class StatusCount(
    val status: IdeaStatus,
    val amount: Int
)
