package com.example.fitquest.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [User::class, UserProfile::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDAO(): UserDAO
    abstract fun userProfileDAO(): UserProfileDAO
}