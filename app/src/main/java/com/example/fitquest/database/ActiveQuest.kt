package com.example.fitquest.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fitquest.models.QuestExercise

@Entity
@TypeConverters(Converters::class)
data class ActiveQuest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val split: String,
    // keep DB column name 'modifier' for now (UI will show "Focus")
    val modifier: String,
    val exercises: List<QuestExercise>,
    // nullable so it's not set until the user actually starts
    val startedAt: Long? = null
)