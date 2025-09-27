package com.example.fitquest.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings", indices = [Index("userId")])
data class UserSettings(
    @PrimaryKey val userId: Int,
    val restTimerSec: Int = 180,
    val equipmentCsv: String = "" // pipe-joined names e.g. "dumbbell|bench"
)
