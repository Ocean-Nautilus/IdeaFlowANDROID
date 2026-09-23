package com.nautilus.ideas.data.db

import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag

/**
 * Демонстрационные данные для первого запуска.
 *
 * Нужны, чтобы список, фильтры и графики было на чём проверять,
 * пока пользователь не завёл свои идеи.
 */
object DatabaseSeeder {

    suspend fun seed(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        val ideaDao = database.ideaDao()
        val tagDao = database.tagDao()

        // --- категории ---
        val studyId = categoryDao.insert(Category(name = "Учёба", colorHex = "#D2F84B"))
        val workId = categoryDao.insert(Category(name = "Работа", colorHex = "#5CD16A"))
        val personalId = categoryDao.insert(Category(name = "Личное", colorHex = "#4DD0C4"))

        // --- теги ---
        val tagIds = listOf("срочно", "лёгкое", "долгосрочное", "проект", "почитать")
            .associateWith { name -> tagDao.insert(Tag(name = name)) }

        // --- идеи ---
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()

        val samples = listOf(
            Pair(
                Idea(
                    title = "Курсовая по мобильной разработке",
                    content = "Приложение для записи идей: список, статистика, голосовой ввод.",
                    categoryId = studyId,
                    status = IdeaStatus.IN_PROGRESS,
                    rating = 5,
                    createdAt = now - day,
                    updatedAt = now - day
                ),
                listOf("срочно", "проект")
            ),
            Pair(
                Idea(
                    title = "Разобраться с Room",
                    content = "Связи один-ко-многим и многие-ко-многим, внешние ключи.",
                    categoryId = studyId,
                    status = IdeaStatus.DONE,
                    rating = 4,
                    createdAt = now - 3 * day,
                    updatedAt = now - 2 * day
                ),
                listOf("почитать")
            ),
            Pair(
                Idea(
                    title = "Шаблон отчёта",
                    content = "Сделать заготовку, чтобы каждый раз не собирать документ заново.",
                    categoryId = workId,
                    status = IdeaStatus.NEW,
                    rating = 2,
                    createdAt = now - 5 * day,
                    updatedAt = now - 5 * day
                ),
                listOf("лёгкое")
            ),
            Pair(
                Idea(
                    title = "Автоматизировать отчёты",
                    content = "Скрипт, который собирает цифры за неделю сам.",
                    categoryId = workId,
                    status = IdeaStatus.POSTPONED,
                    rating = 3,
                    createdAt = now - 8 * day,
                    updatedAt = now - 8 * day
                ),
                listOf("долгосрочное", "проект")
            ),
            Pair(
                Idea(
                    title = "Список книг на лето",
                    content = "Собрать всё, что откладывала весь год.",
                    categoryId = personalId,
                    status = IdeaStatus.NEW,
                    rating = 1,
                    createdAt = now - 10 * day,
                    updatedAt = now - 10 * day
                ),
                listOf("почитать", "лёгкое")
            ),
            Pair(
                Idea(
                    title = "Утренняя пробежка",
                    content = "Начать с трёх раз в неделю по двадцать минут.",
                    categoryId = personalId,
                    status = IdeaStatus.IN_PROGRESS,
                    rating = 4,
                    createdAt = now - 14 * day,
                    updatedAt = now - 12 * day
                ),
                listOf("долгосрочное")
            )
        )

        samples.forEach { (idea, tagNames) ->
            val ideaId = ideaDao.insert(idea)
            tagNames.forEach { name ->
                tagIds[name]?.let { tagId ->
                    tagDao.linkTagToIdea(IdeaTagCrossRef(ideaId = ideaId, tagId = tagId))
                }
            }
        }
    }
}
