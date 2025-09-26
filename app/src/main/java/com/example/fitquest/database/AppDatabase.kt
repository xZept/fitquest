package com.example.fitquest.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [User::class, UserProfile::class, UserSettings::class, ActiveQuest::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDAO(): UserDAO
    abstract fun userProfileDAO(): UserProfileDAO

    abstract fun userSettingsDao(): UserSettingsDao

    abstract fun activeQuestDao(): ActiveQuestDao

}