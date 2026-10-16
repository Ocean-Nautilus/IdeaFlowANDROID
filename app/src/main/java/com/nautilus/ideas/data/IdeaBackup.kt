package com.nautilus.ideas.data

import androidx.room.withTransaction
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ImportResult(val added: Int, val skipped: Int)

class IdeaBackup(private val database: AppDatabase) {
    private data class Entry(val idea: Idea, val tagIds: List<Long>)

    suspend fun exportJson(): String = database.withTransaction {
        val categories = database.categoryDao().getAll()
        val tags = database.tagDao().getAll()
        val ideas = database.ideaDao().getAll()
        JSONObject().put("format", "ideaflow").put("version", 1)
            .put("categories", JSONArray(categories.map {
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("colorHex", it.colorHex).put("createdAt", it.createdAt)
            }))
            .put("tags", JSONArray(tags.map {
                JSONObject().put("id", it.id).put("name", it.name)
            }))
            .put("ideas", JSONArray(ideas.map { item ->
                val idea = item.idea
                JSONObject().put("id", idea.id).put("title", idea.title)
                    .put("content", idea.content).put("categoryId", idea.categoryId)
                    .put("status", idea.status.name).put("rating", idea.rating)
                    .put("isVoiceNote", idea.isVoiceNote).put("createdAt", idea.createdAt)
                    .put("updatedAt", idea.updatedAt).put("tagIds", JSONArray(item.tags.map { it.id }))
            })).toString(2).also { checkSize(it) }
    }

    suspend fun importJson(text: String): ImportResult {
        checkSize(text)
        val tokener = JSONTokener(text)
        val root = tokener.nextValue() as? JSONObject ?: error("Ожидается файл JSON IdeaFlow")
        require(tokener.nextClean() == '\u0000') { "Лишние данные в конце файла" }
        require(root.string("format") == "ideaflow" && root.number("version") == 1L) {
            "Неподдерживаемый формат или версия файла"
        }
        val categories = root.getJSONArray("categories").objects().map {
            Category(id = it.number("id"), name = it.string("name"),
                colorHex = it.string("colorHex"), createdAt = it.number("createdAt"))
        }
        val tags = root.getJSONArray("tags").objects().map {
            Tag(id = it.number("id"), name = it.string("name"))
        }
        val entries = root.getJSONArray("ideas").objects().map {
            val rating = it.number("rating")
            require(rating in 0L..5L) { "Важность должна быть от 0 до 5" }
            val voice = it.get("isVoiceNote")
            require(voice is Boolean) { "Некорректный признак голосовой заметки" }
            val statusName = it.string("status")
            val status = IdeaStatus.entries.firstOrNull { status -> status.name == statusName }
                ?: error("Неизвестный статус идеи")
            val tagIds = it.getJSONArray("tagIds")
            Entry(Idea(id = it.number("id"), title = it.string("title"),
                content = it.string("content"), categoryId = it.number("categoryId"),
                status = status, rating = rating.toInt(),
                isVoiceNote = voice, createdAt = it.number("createdAt"), updatedAt = it.number("updatedAt")),
                List(tagIds.length()) { index -> integer(tagIds.get(index), "tagIds") })
        }
        require(categories.all { it.id > 0 && it.name.isNotBlank() && it.createdAt >= 0 &&
            it.colorHex.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) }) { "Некорректные категории" }
        require(tags.all { it.id > 0 && it.name.isNotBlank() }) { "Некорректные теги" }
        require(categories.map { it.id }.distinct().size == categories.size &&
            categories.map { it.name }.distinct().size == categories.size &&
            tags.map { it.id }.distinct().size == tags.size &&
            tags.map { it.name }.distinct().size == tags.size &&
            entries.map { it.idea.id }.distinct().size == entries.size) { "Повторяющиеся идентификаторы или названия" }
        val categoryIds = categories.map { it.id }.toSet()
        val tagIds = tags.map { it.id }.toSet()
        require(entries.all { entry ->
            val idea = entry.idea
            idea.id > 0 && idea.title.isNotBlank() && idea.title.length <= 80 &&
                idea.createdAt >= 0 && idea.updatedAt >= 0 && idea.categoryId in categoryIds &&
                entry.tagIds.all { it in tagIds } && entry.tagIds.distinct().size == entry.tagIds.size
        }) { "Некорректные идеи или связи с категориями и тегами" }

        // Проверяем весь файл до записи; любая ошибка записи откатывает импорт целиком.
        return database.withTransaction {
            val categoryDao = database.categoryDao()
            val tagDao = database.tagDao()
            val ideaDao = database.ideaDao()
            val existingCategories = categoryDao.getAll().associateBy { it.name }
            val existingTags = tagDao.getAll().associateBy { it.name }
            val categoryMap = categories.associate {
                it.id to (existingCategories[it.name]?.id ?: categoryDao.insert(it.copy(id = 0)))
            }
            val tagMap = tags.associate {
                it.id to (existingTags[it.name]?.id ?: tagDao.insert(it.copy(id = 0)))
            }
            val existing = ideaDao.getAll().map {
                Entry(it.idea.copy(id = 0), it.tags.map { tag -> tag.id }.sorted())
            }.groupingBy { it }.eachCount().toMutableMap()
            var added = 0
            for (entry in entries) {
                val idea = entry.idea.copy(id = 0, categoryId = categoryMap.getValue(entry.idea.categoryId))
                val mappedTags = entry.tagIds.map { tagMap.getValue(it) }.sorted()
                val key = Entry(idea, mappedTags)
                val matches = existing[key] ?: 0
                if (matches > 0) {
                    existing[key] = matches - 1
                } else {
                    val id = ideaDao.insert(idea)
                    mappedTags.forEach { tagDao.linkTagToIdea(IdeaTagCrossRef(id, it)) }
                    added++
                }
            }
            ImportResult(added, entries.size - added)
        }
    }

    private fun JSONObject.string(name: String): String =
        (get(name) as? String) ?: error("Некорректное поле: $name")

    private fun JSONObject.number(name: String): Long = integer(get(name), name)

    private fun integer(value: Any, name: String): Long {
        require(value is Int || value is Long) { "Ожидается целое число: $name" }
        return (value as Number).toLong()
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun checkSize(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Файл превышает 10 МБ" }
    }

    companion object {
        // shortcut: копия целиком в памяти, для файлов больше 10 МБ нужен потоковый формат.
        const val MAX_FILE_BYTES = 10 * 1024 * 1024
    }
}
