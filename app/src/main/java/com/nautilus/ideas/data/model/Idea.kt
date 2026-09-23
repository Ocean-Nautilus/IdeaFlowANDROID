package com.nautilus.ideas.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Идея — главная сущность приложения.
 *
 * Сторона "много" в связи один-ко-многим: каждая идея принадлежит
 * ровно одной категории. Внешний ключ объявлен с onDelete = CASCADE,
 * поэтому удаление категории удаляет и все её идеи — этого требует ТЗ.
 */
@Entity(
    tableName = "ideas",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Индекс по внешнему ключу: без него выборка идей категории
    // просматривает всю таблицу, а Room выдаёт предупреждение.
    indices = [Index("categoryId")]
)
data class Idea(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val content: String,

    val categoryId: Long,

    val status: IdeaStatus = IdeaStatus.NEW,

    /** Оценка от 0 до 5. Нужна для сортировки списка по важности. */
    val rating: Int = 0,

    /** Идея надиктована голосом, а не набрана руками (изюминка, неделя 11). */
    val isVoiceNote: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)
