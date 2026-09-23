package com.nautilus.ideas.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Связующая (промежуточная) таблица для связи многие-ко-многим
 * между идеями и тегами. Это четвёртая таблица, которую требует ТЗ.
 *
 * Своего id у неё нет: первичный ключ составной — пара (идея, тег).
 * Благодаря этому один и тот же тег нельзя повесить на идею дважды.
 */
@Entity(
    tableName = "idea_tag_cross_ref",
    primaryKeys = ["ideaId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Idea::class,
            parentColumns = ["id"],
            childColumns = ["ideaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // По ideaId индекс создаётся автоматически (первый столбец
    // составного ключа), а по tagId его нужно добавить руками.
    indices = [Index("tagId")]
)
data class IdeaTagCrossRef(
    val ideaId: Long,
    val tagId: Long
)
