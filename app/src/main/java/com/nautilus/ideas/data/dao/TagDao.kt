package com.nautilus.ideas.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Запросы к тегам и к связующей таблице.
 *
 * Здесь же живут методы работы со связью многие-ко-многим: отдельного
 * DAO для промежуточной таблицы заводить смысла нет, она обслуживает теги.
 */
@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT id FROM tags WHERE name = :name LIMIT 1")
    suspend fun findIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Delete
    suspend fun delete(tag: Tag)

    // --- связь идея <-> тег ---

    /** Повесить тег на идею. Повторная вставка той же пары ничего не меняет. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTagToIdea(crossRef: IdeaTagCrossRef)

    /** Снять с идеи все теги. Используется перед сохранением нового набора. */
    @Query("DELETE FROM idea_tag_cross_ref WHERE ideaId = :ideaId")
    suspend fun clearTagsOfIdea(ideaId: Long)

    /** Теги одной идеи. */
    @Query(
        """
        SELECT t.* FROM tags AS t
        INNER JOIN idea_tag_cross_ref AS ref ON t.id = ref.tagId
        WHERE ref.ideaId = :ideaId
        ORDER BY t.name
        """
    )
    suspend fun getTagsOfIdea(ideaId: Long): List<Tag>
}
