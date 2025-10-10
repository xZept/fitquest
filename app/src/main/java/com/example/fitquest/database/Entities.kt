package com.example.fitquest.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fitquest.models.QuestExercise
import java.time.Instant

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
@Entity(tableName = "userSettings", indices = [Index("userId")])
data class UserSettings(
    @PrimaryKey val userId: Int,
    val restTimerSec: Int = 180,
    val equipmentCsv: String = "", // pipe-joined names e.g. "dumbbell|bench"

    val mandatoryRest: Boolean = false
)

// ----- User Wallet -----
@Entity(
    tableName = "userWallet",
    indices = [Index(value = ["userId"], unique = true)]
)
data class UserWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val coins: Int = 0
)

// ----- Active Quest -----
@Entity(tableName = "activeQuests")
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

// ----- Quest History -----
@Entity(
    tableName = "questHistory",
    indices = [Index(value = ["userId", "key"], unique = true)]
)
@TypeConverters(Converters::class) // you already use this for List<QuestExercise>
data class QuestHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val key: String,                  // split|modifier|hash(exercises)
    val title: String,                // e.g., "Push • Strength"
    val split: String,
    val modifier: String,
    val exercises: List<QuestExercise>,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

// ----- Workout Session -----
@Entity(tableName = "workoutSession")
data class WorkoutSession(
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
    tableName = "workoutSetLog",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class WorkoutSetLog(
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

// ----- Food -----
@Entity (tableName = "food",
    indices = [Index(value=["normalizedName"], unique = true)])
data class Food (
    @PrimaryKey(autoGenerate = true) val foodId: Long = 0L,
    val foodName: String,
    val normalizedName: String,
    val category: String? = null,
    val locale: String? = null,
    val source: String? = null,
    val sourceRef: String? = null,
    val kcalPer100g: Double,
    val carbPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val ediblePortionPercent: Double? = 100.0,
    val lastUpdated: Instant = Instant.now()
)


// ----- Portion -----
@Entity(
    tableName = "portion",
    foreignKeys = [ForeignKey(
        entity = Food::class,
        parentColumns = ["foodId"],
        childColumns = ["foodId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("foodId"),
        Index(value = ["foodId","measurementType","quantity"], unique = true)
    ]
)
data class Portion(
    @PrimaryKey(autoGenerate = true) val portionId: Long = 0L,
    val foodId: Long,
    val measurementType: MeasurementType,
    val quantity: Double,
    val gramWeight: Double,
    val isApproximate: Boolean = false
)


// ----- Monster Catalog -----
@Entity(
    tableName = "monster",
    indices = [Index(value = ["code"], unique = true)]
)
data class Monster(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,           // stable id like "slime_blue"
    val name: String,           // shown in UI
    val spriteRes: String,      // drawable name, e.g. "monster_slime_blue"
    val price: Int              // cost in coins
)

// ----- User-Owned Monsters -----
@Entity(
    tableName = "userMonster",
    indices = [Index(value = ["userId", "monsterCode"], unique = true)]
)
data class UserMonster(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val monsterCode: String,
    val acquiredAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "foodLog",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId"), Index("foodId"), Index(value = ["userId", "loggedAt"])]
)
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0L,
    val userId: Int,
    val foodId: Long,
    val grams: Double,
    val calories: Double,
    val protein: Double,
    val carbohydrate: Double,
    val fat: Double,

    // timestamps
    val loggedAt: Long,          // epoch ms UTC
    val dayKey: Int,             // e.g., 20251004 for Oct 4, 2025 in user's tz

    // optional: group the UI by meals
    val mealType: String
)

@Entity(
    tableName = "macroPlan",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MacroPlan(
    @PrimaryKey(autoGenerate = true) val macroId: Long = 0L,
    val userId : Int,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "macroDiary",
    indices = [Index(value = ["userId","dayKey"], unique = true)]
)
data class MacroDiary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val dayKey: Int,              // YYYYMMDD in Asia/Manila
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,

    val planCalories: Int = 0,
    val planProtein: Int = 0,
    val planCarbs: Int = 0,
    val planFat: Int = 0,

    val capturedAt: Long = System.currentTimeMillis()
)

