package com.example.fitquest.database

import androidx.room.*

@Entity(
    tableName = "workout_set_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class WorkoutSetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val exerciseName: String,
    val setNumber: Int,
    val repsMin: Int,
    val repsMax: Int,
    val loadType: String,       // "Bodyweight" | "External load (kg)" | "Assisted (-kg)" | "Band level" | "Skipped log"
    val loadValueText: String,  // e.g. "25 kg", "-20 kg", "Green (~15 kg)"
    val loggedAt: Long
)
