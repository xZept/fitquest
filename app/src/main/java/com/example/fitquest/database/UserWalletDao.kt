package com.example.fitquest.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserWalletDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(wallet: UserWallet): Long

    @Query("INSERT OR IGNORE INTO user_wallet(userId, coins) VALUES(:userId, 0)")
    suspend fun ensure(userId: Int)

    @Query("UPDATE user_wallet SET coins = coins + :delta WHERE userId = :userId")
    suspend fun add(userId: Int, delta: Int)

    @Query("SELECT coins FROM user_wallet WHERE userId = :userId LIMIT 1")
    suspend fun getCoins(userId: Int): Int?

    @Query("UPDATE user_wallet SET coins = :value WHERE userId = :userId")
    suspend fun setCoins(userId: Int, value: Int)
}
