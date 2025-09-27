package com.example.fitquest.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveQuestDao {
    @Query("SELECT * FROM active_quests WHERE userId = :uid LIMIT 1")
    suspend fun getActiveForUser(uid: Int): ActiveQuest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(q: ActiveQuest): Long

    @Query("DELETE FROM active_quests WHERE userId = :uid")
    suspend fun clearForUser(uid: Int)
}
