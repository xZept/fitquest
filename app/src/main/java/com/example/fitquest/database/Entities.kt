package com.example.fitquest.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fitquest.models.QuestExercise

// ----- User -----

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Int = 0,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "birthday") val birthday: String,
    @ColumnInfo(name = "age") val age: Int,
    @ColumnInfo(name = "sex") val sex: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password") val password: String,
)

// ----- User Profile -----
@Entity(tableName = "userProfile",
    foreignKeys = [
        ForeignKey(
            entity = User::class,                // parent table
            parentColumns = ["userId"],          // PK in parent
            childColumns = ["userId"],           // FK in child
            onDelete = ForeignKey.CASCADE
        )], indices = [androidx.room.Index("userId")]
)
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val profileId: Int = 0,
    @ColumnInfo(name = "userId") val userId: Int,
    @ColumnInfo(name = "height") val height: Int, // cm
    @ColumnInfo(name = "weight") val weight: Int, // kg
    @ColumnInfo(name = "activity_level") val activityLevel: String? = null,
    @ColumnInfo(name = "goal") val goal: String? = null,
    @ColumnInfo(name = "equipment") val equipment: String? = null
)

// ----- User Settings -----
@Entity(tableName = "user_settings", indices = [Index("userId")])
data class UserSettings(
    @PrimaryKey val userId: Int,
    val restTimerSec: Int = 180,
    val equipmentCsv: String = "" // pipe-joined names e.g. "dumbbell|bench"
)

// ----- User Wallet -----
@Entity(
    tableName = "user_wallet",
    indices = [Index(value = ["userId"], unique = true)]
)
data class UserWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val coins: Int = 0
)

// ----- Active Quest -----
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

// ----- Workout Session -----
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

// ----- Workout Set Log -----
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




