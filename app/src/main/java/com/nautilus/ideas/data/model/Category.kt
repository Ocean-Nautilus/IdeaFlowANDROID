package com.nautilus.ideas.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Категория идеи: "Учёба", "Работа", "Личное" и т.п.
 *
 * Сторона "один" в связи один-ко-многим: одна категория содержит
 * много идей (см. внешний ключ в [Idea]).
 */
@Entity(
    tableName = "categories",
    // Двух категорий с одинаковым названием быть не должно.
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** Цвет метки категории в формате #RRGGBB. */
    val colorHex: String,

    val createdAt: Long = System.currentTimeMillis()
)
