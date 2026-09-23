package com.nautilus.ideas.data.db

import androidx.room.TypeConverter
import com.nautilus.ideas.data.model.IdeaStatus

/**
 * SQLite умеет хранить только числа, строки и массивы байт.
 * Здесь описано, как класть в базу то, чего она не понимает.
 *
 * Сейчас такой тип один — перечисление [IdeaStatus].
 */
class Converters {

    @TypeConverter
    fun fromStatus(status: IdeaStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): IdeaStatus =
        // Если в базе окажется неизвестное значение (например, статус
        // убрали из кода), приложение не упадёт, а покажет идею как новую.
        runCatching { IdeaStatus.valueOf(value) }.getOrDefault(IdeaStatus.NEW)
}
