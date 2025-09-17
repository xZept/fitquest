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

    // --- START OF CHANGES ---

    // MODIFIED: This function now inserts a SINGLE user and returns its new ID.
    // It calls the `insert` function in your DAO that you correctly updated.
    suspend fun insert(user: User): Long {
        Log.d("FitquestDB", "Inserting single user to get ID: $user")
        val newId = userDAO.insert(user)
        Log.d("FitquestDB", "User inserted successfully with ID: $newId")
        return newId
    }

    // This function is likely unused now, but can be kept if you ever need to insert
    // multiple users at once without needing their IDs back.
    //suspend fun insertAll(vararg users: User) {
        //Log.d("FitquestDB", "Inserting multiple users: ${users.toList()}")
        //userDAO.insertAll(*users)
        //Log.d("FitquestDB", "Users inserted successfully")
    //}

    // --- END OF CHANGES ---

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
