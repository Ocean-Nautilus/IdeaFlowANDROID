package com.nautilus.ideas.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.model.StatusCount
import kotlinx.coroutines.flow.Flow

/**
 * Запросы к таблице идей — главной сущности приложения.
 *
 * Аннотация @Transaction на выборках с @Relation обязательна: Room
 * выполняет несколько запросов (идеи, затем их категории и теги),
 * и без транзакции между ними данные могут успеть измениться.
 */
@Dao
interface IdeaDao {

    @Transaction
    @Query("SELECT * FROM ideas ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<IdeaWithDetails>>

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :ideaId")
    fun observeById(ideaId: Long): Flow<IdeaWithDetails?>

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :ideaId")
    suspend fun getById(ideaId: Long): IdeaWithDetails?

    /** Общее количество идей. Нужно для пустого состояния и статистики. */
    @Query("SELECT COUNT(*) FROM ideas")
    fun observeCount(): Flow<Int>

    /** Разбивка по статусам — данные для диаграммы на экране статистики. */
    @Query("SELECT status, COUNT(*) AS amount FROM ideas GROUP BY status")
    fun observeCountByStatus(): Flow<List<StatusCount>>

    @Insert
    suspend fun insert(idea: Idea): Long

    @Update
    suspend fun update(idea: Idea)

    @Delete
    suspend fun delete(idea: Idea)
}
