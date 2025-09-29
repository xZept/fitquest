package com.example.fitquest.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [User::class, UserProfile::class, UserSettings::class, ActiveQuest::class,
    WorkoutSession::class, WorkoutSetLog::class, UserWallet::class, QuestHistory::class,
    Monster::class, UserMonster::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDAO(): UserDAO
    abstract fun userProfileDAO(): UserProfileDAO
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun activeQuestDao(): ActiveQuestDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetLogDao(): WorkoutSetLogDao
    abstract fun userWalletDao(): UserWalletDao
    abstract fun questHistoryDao(): QuestHistoryDao
    abstract fun monsterDao(): MonsterDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitquestDB"
                )
                    // nukes & recreates schema
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

