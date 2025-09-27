package com.example.fitquest.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fitquest.models.QuestExercise

@Entity(tableName = "active_quests")
@TypeConverters(Converters::class)
data class ActiveQuest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val split: String,
    // DB column name remains 'modifier' (UI shows this as Focus)
    val modifier: String,
    val exercises: List<QuestExercise>,
    val startedAt: Long? = null
)
