package com.example.fitquest.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveQuestDao {
    @Query("SELECT * FROM ActiveQuest WHERE userId = :uid LIMIT 1")
    suspend fun getActiveForUser(uid: Int): ActiveQuest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(q: ActiveQuest)

    @Query("DELETE FROM ActiveQuest WHERE userId = :uid")
    suspend fun clearForUser(uid: Int)
}