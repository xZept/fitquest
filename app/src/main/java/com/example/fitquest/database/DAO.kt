package com.example.fitquest.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDAO {
    @Query("SELECT * FROM user")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM user WHERE username = :username AND password = :password LIMIT 1")
    suspend fun authenticateUser(username: String, password: String): User?

    @Query("SELECT * FROM user WHERE userId = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Insert
    suspend fun insert(user: User):Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun delete(user: User)
}

@Dao
interface UserProfileDAO {
    @Insert
    suspend fun insert(userProfile: UserProfile):Long
}
