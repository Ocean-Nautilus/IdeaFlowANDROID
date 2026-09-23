package com.nautilus.ideas.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nautilus.ideas.data.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Запросы к таблице категорий.
 *
 * Методы чтения возвращают Flow: подписчик получает новый список
 * автоматически, как только данные в таблице поменяются.
 * Методы записи помечены suspend — вызвать их можно только из корутины,
 * то есть выполниться в главном потоке они не могут по определению.
 */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getById(categoryId: Long): Category?

    /** Сколько идей лежит в категории — понадобится на экране статистики. */
    @Query("SELECT COUNT(*) FROM ideas WHERE categoryId = :categoryId")
    suspend fun countIdeas(categoryId: Long): Int

    // IGNORE вместо REPLACE: имя категории уникально, и при повторной
    // вставке той же категории лучше молча ничего не делать, чем удалять
    // старую строку вместе со всеми её идеями (сработал бы CASCADE).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
