package com.nautilus.ideas.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Идея вместе со своей категорией и списком тегов.
 *
 * Это не таблица, а результат выборки: Room сам делает нужные JOIN-ы
 * и собирает объект. Экранам удобно получать всё одним куском,
 * чтобы не дёргать базу отдельно за категорией и отдельно за тегами.
 */
data class IdeaWithDetails(
    @Embedded
    val idea: Idea,

    // Связь один-ко-многим со стороны "много":
    // categoryId идеи указывает на id категории.
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: Category,

    // Связь многие-ко-многим: идёт через связующую таблицу.
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = IdeaTagCrossRef::class,
            parentColumn = "ideaId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>
)
