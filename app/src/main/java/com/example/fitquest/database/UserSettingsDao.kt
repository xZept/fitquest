package com.example.fitquest.database

import androidx.room.*

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM UserSettings WHERE userId = :id LIMIT 1")
    suspend fun getByUserId(id: Int): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings)
}