package com.example.fitquest.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One wallet row per user.
 */
@Entity(
    tableName = "user_wallet",
    indices = [Index(value = ["userId"], unique = true)]
)
data class UserWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Int,
    val coins: Int = 0
)
