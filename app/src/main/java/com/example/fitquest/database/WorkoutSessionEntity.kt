package com.example.fitquest.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,         // 0 until finished/abandoned
    val totalSets: Int,
    val completedSets: Int,
    val coinsEarned: Int       // 0 if abandoned
)
