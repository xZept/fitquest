package com.example.fitquest.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WorkoutSetLogDao {
    @Insert
    suspend fun insert(log: WorkoutSetLogEntity): Long

    @Insert
    suspend fun insertAll(logs: List<WorkoutSetLogEntity>): List<Long>

    @Query("SELECT * FROM workout_set_logs WHERE sessionId = :sessionId ORDER BY setNumber ASC, id ASC")
    suspend fun getForSession(sessionId: Long): List<WorkoutSetLogEntity>

    @Query("DELETE FROM workout_set_logs WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
