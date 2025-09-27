package com.example.fitquest.database

import androidx.room.*

@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("""
        UPDATE workout_sessions
        SET endedAt = :endedAt,
            completedSets = :completedSets,
            coinsEarned = :coinsEarned
        WHERE id = :sessionId
    """)
    suspend fun finishSession(
        sessionId: Long,
        endedAt: Long,
        completedSets: Int,
        coinsEarned: Int
    )

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY startedAt DESC")
    suspend fun getAllByUser(userId: Int): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestForUser(userId: Int): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: Long): WorkoutSessionEntity?

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)
}
