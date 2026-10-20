package com.nautilus.ideas.data.repository

import com.nautilus.ideas.data.dao.CategoryDao
import com.nautilus.ideas.data.dao.IdeaDao
import com.nautilus.ideas.data.dao.TagDao
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.SortOption
import com.nautilus.ideas.data.model.CategoryCount
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.model.StatusCount
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

/**
 * Единая точка доступа к данным для слоя представления.
 *
 * ViewModel не должна знать ни про Room, ни про то, сколько DAO
 * участвует в операции: она просит "сохрани идею с такими тегами",
 * а как это разложится по таблицам — забота репозитория.
 */
class IdeaRepository(
    private val ideaDao: IdeaDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val database: AppDatabase
) {

    fun observeIdeas(): Flow<List<IdeaWithDetails>> = ideaDao.observeAll()

    fun observeIdeas(
        query: String = "",
        status: IdeaStatus? = null,
        categoryId: Long? = null,
        sort: SortOption = SortOption.DATE_DESC
    ): Flow<List<IdeaWithDetails>> = ideaDao.observeFiltered(
        query = query,
        status = status?.name,
        categoryId = categoryId,
        sort = sort.name
    )

    fun observeCountByCategory(): Flow<List<CategoryCount>> = categoryDao.observeCountByCategory()
    fun observeIdea(ideaId: Long): Flow<IdeaWithDetails?> = ideaDao.observeById(ideaId)

    fun observeIdeaCount(): Flow<Int> = ideaDao.observeCount()

    fun observeCountByStatus(): Flow<List<StatusCount>> = ideaDao.observeCountByStatus()

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()

    suspend fun getIdea(ideaId: Long): IdeaWithDetails? = ideaDao.getById(ideaId)

    // --- запись ---

    /**
     * Сохраняет идею вместе с набором тегов.
     *
     * Теги приходят названиями: те, которых ещё нет в справочнике,
     * создаются на лету. Старые связи полностью перезаписываются,
     * поэтому метод годится и для создания, и для редактирования.
     *
     * @return id сохранённой идеи
     */
    suspend fun saveIdea(idea: Idea, tagNames: List<String>): Long {
        val ideaId = if (idea.id == 0L) {
            ideaDao.insert(idea)
        } else {
            val original = requireNotNull(ideaDao.getById(idea.id)) { "Идея больше не существует" }.idea
            ideaDao.update(idea.copy(
                createdAt = original.createdAt,
                isVoiceNote = original.isVoiceNote || idea.isVoiceNote,
                updatedAt = System.currentTimeMillis()
            ))
            idea.id
        }

        tagDao.clearTagsOfIdea(ideaId)
        tagNames
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { name ->
                // insert с IGNORE возвращает -1, если тег уже существует,
                // поэтому в таком случае берём его id отдельным запросом.
                val newId = tagDao.insert(Tag(name = name))
                val tagId = if (newId != -1L) newId else tagDao.findIdByName(name)
                tagId?.let { tagDao.linkTagToIdea(IdeaTagCrossRef(ideaId, it)) }
            }

        return ideaId
    }

    suspend fun deleteIdea(idea: Idea) = ideaDao.delete(idea)

    suspend fun saveCategory(category: Category): Long = database.withTransaction {
        val name = category.name.trim()
        require(name.isNotEmpty() && name.length <= 40) { "Название должно содержать от 1 до 40 символов" }
        require(category.colorHex.matches(Regex("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))) { "Некорректный цвет категории" }
        val categories = categoryDao.getAll()
        require(categories.none { it.id != category.id && it.name.equals(name, ignoreCase = true) }) {
            "Категория с таким названием уже есть"
        }
        if (category.id == 0L) {
            categoryDao.insert(category.copy(name = name)).also { check(it != -1L) { "Категория уже существует" } }
        } else {
            val original = requireNotNull(categories.find { it.id == category.id }) { "Категория удалена" }
            categoryDao.update(original.copy(name = name, colorHex = category.colorHex))
            category.id
        }
    }

    suspend fun deleteCategory(category: Category, expectedIdeaCount: Int) = database.withTransaction {
        require(categoryDao.getAll().size > 1) { "Нельзя удалить последнюю категорию" }
        requireNotNull(categoryDao.getById(category.id)) { "Категория уже удалена" }
        require(categoryDao.countIdeas(category.id) == expectedIdeaCount) {
            "Количество идей изменилось. Подтвердите удаление ещё раз"
        }
        categoryDao.delete(category)
    }

    companion object {
        /** Собирает репозиторий из уже открытой базы. */
        fun from(database: AppDatabase): IdeaRepository = IdeaRepository(
            ideaDao = database.ideaDao(),
            categoryDao = database.categoryDao(),
            tagDao = database.tagDao(),
            database = database
        )
    }
}
