package com.example.fitquest.repository

import android.content.Context
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import android.util.Log

class FitquestRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "fitquestDB"
    ).build()

    private val userDAO = db.userDAO()

    suspend fun insertAll(vararg users: User) {
        Log.d("FitquestDB", "Inserting users: ${users.toList()}")
        userDAO.insertAll(*users)
        Log.d("FitquestDB", "Users inserted successfully")
    }

    suspend fun authenticateUser(username: String, password: String): Int? {
        Log.d("FitquestDB", "Authenticating user: username=$username, password=$password")
        val user = userDAO.authenticateUser(username, password)
        Log.d("FitquestDB", "authenticateUser() result: $user")
        return user?.userId
    }

    suspend fun getAllUsers(): List<User> {
        val users = userDAO.getAllUsers()
        Log.d("FitquestDB", "Fetched all users: $users")
        return users
    }
}