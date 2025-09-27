package com.example.fitquest.database

import androidx.room.*

// ---------------- Users ----------------
@Dao
interface UserDAO {
    @Query("SELECT * FROM user")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM user WHERE username = :username AND password = :password LIMIT 1")
    suspend fun authenticateUser(username: String, password: String): User?

    @Query("SELECT * FROM user WHERE userId = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Insert suspend fun insert(user: User): Long
    @Update suspend fun updateUser(user: User)
    @Delete suspend fun delete(user: User)
}

// ------------- User Profile -------------
@Dao
interface UserProfileDAO {
    @Insert suspend fun insert(userProfile: UserProfile): Long

    @Query("SELECT * FROM userProfile WHERE userId = :userId LIMIT 1")
    suspend fun getProfileByUserId(userId: Int): UserProfile?

    @Update suspend fun update(userProfile: UserProfile)
}

