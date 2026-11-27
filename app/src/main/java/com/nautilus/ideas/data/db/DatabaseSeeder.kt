package com.nautilus.ideas.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaStatus

/**
 * Демонстрационные данные для первого запуска.
 *
 * Нужны, чтобы список, фильтры и графики было на чём проверять,
 * пока пользователь не завёл свои идеи.
 */
object DatabaseSeeder {

    // Room вызывает onCreate внутри транзакции: первый читатель получит все примеры сразу.
    fun seed(db: SupportSQLiteDatabase) {
        fun insertCategory(category: Category): Long = db.compileStatement(
            "INSERT INTO categories(name, colorHex, createdAt) VALUES (?, ?, ?)"
        ).use { statement ->
            statement.bindString(1, category.name)
            statement.bindString(2, category.colorHex)
            statement.bindLong(3, category.createdAt)
            statement.executeInsert()
        }

        // --- категории ---
        val studyId = insertCategory(Category(name = "Учёба", colorHex = "#D2F84B"))
        val workId = insertCategory(Category(name = "Работа", colorHex = "#5CD16A"))
        val personalId = insertCategory(Category(name = "Личное", colorHex = "#4DD0C4"))

        // --- идеи ---
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()

        val samples = listOf(
            Idea(
                title = "Курсовая по мобильной разработке",
                content = "Приложение для записи идей: список, статистика, голосовой ввод.",
                categoryId = studyId,
                status = IdeaStatus.IN_PROGRESS,
                rating = 5,
                createdAt = now - day,
                updatedAt = now - day
            ),
            Idea(
                title = "Разобраться с Room",
                content = "Связи один-ко-многим и многие-ко-многим, внешние ключи.",
                categoryId = studyId,
                status = IdeaStatus.DONE,
                rating = 4,
                createdAt = now - 3 * day,
                updatedAt = now - 2 * day
            ),
            Idea(
                title = "Шаблон отчёта",
                content = "Сделать заготовку, чтобы каждый раз не собирать документ заново.",
                categoryId = workId,
                status = IdeaStatus.NEW,
                rating = 2,
                createdAt = now - 5 * day,
                updatedAt = now - 5 * day
            ),
            Idea(
                title = "Автоматизировать отчёты",
                content = "Скрипт, который собирает цифры за неделю сам.",
                categoryId = workId,
                status = IdeaStatus.POSTPONED,
                rating = 3,
                createdAt = now - 8 * day,
                updatedAt = now - 8 * day
            ),
            Idea(
                title = "Список книг на лето",
                content = "Собрать всё, что откладывала весь год.",
                categoryId = personalId,
                status = IdeaStatus.NEW,
                rating = 1,
                createdAt = now - 10 * day,
                updatedAt = now - 10 * day
            ),
            Idea(
                title = "Утренняя пробежка",
                content = "Начать с трёх раз в неделю по двадцать минут.",
                categoryId = personalId,
                status = IdeaStatus.IN_PROGRESS,
                rating = 4,
                createdAt = now - 14 * day,
                updatedAt = now - 12 * day
            )
        )

        samples.forEach { idea ->
            db.execSQL("""
                INSERT INTO ideas(title, content, categoryId, status, rating, isVoiceNote, createdAt, updatedAt, backupId)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(), arrayOf(idea.title, idea.content, idea.categoryId, idea.status.name,
                idea.rating, if (idea.isVoiceNote) 1 else 0, idea.createdAt, idea.updatedAt, idea.backupId))
        }
    }
}
