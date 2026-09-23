package com.nautilus.ideas.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Тег — короткая метка, которую можно повесить на любое количество идей.
 *
 * Одна из сторон связи многие-ко-многим: идея имеет много тегов,
 * тег висит на многих идеях. Связь хранится в [IdeaTagCrossRef].
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String
)
