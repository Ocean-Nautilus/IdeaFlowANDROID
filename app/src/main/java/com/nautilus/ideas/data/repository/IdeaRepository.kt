package com.nautilus.ideas.data.repository

import com.nautilus.ideas.data.dao.CategoryDao
import com.nautilus.ideas.data.dao.IdeaDao
import com.nautilus.ideas.data.dao.TagDao
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.model.StatusCount
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.flow.Flow

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
    private val tagDao: TagDao
) {

    // --- чтение ---

    fun observeIdeas(): Flow<List<IdeaWithDetails>> = ideaDao.observeAll()

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
            ideaDao.update(idea.copy(updatedAt = System.currentTimeMillis()))
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

    suspend fun addCategory(category: Category): Long = categoryDao.insert(category)

    suspend fun updateCategory(category: Category) = categoryDao.update(category)

    /** Удаление категории удаляет и все её идеи — так настроен внешний ключ. */
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    companion object {
        /** Собирает репозиторий из уже открытой базы. */
        fun from(database: AppDatabase): IdeaRepository = IdeaRepository(
            ideaDao = database.ideaDao(),
            categoryDao = database.categoryDao(),
            tagDao = database.tagDao()
        )
    }
}
