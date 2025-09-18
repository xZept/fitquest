package com.example.fitquest.repository

import android.content.Context
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import android.util.Log
import com.example.fitquest.database.UserProfile

class FitquestRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "fitquestDB"
    ).fallbackToDestructiveMigration().build() // For development phase only

    private val userDAO = db.userDAO()
    private val userProfileDAO = db.userProfileDAO()

    // Inserts a SINGLE user and returns its new ID.
    suspend fun insertUser(user: User): Long {
        Log.d("FitquestDB", "Inserting single user to get ID: $user")
        val newId = userDAO.insert(user)
        Log.d("FitquestDB", "User inserted successfully with ID: $newId")
        return newId
    }

    // Insert user values from registration to user profile database
    suspend fun insertUserProfile(userProfile: UserProfile): Long {
        Log.d("FitquestDB", "Inserting single user to get ID: $userProfile")
        val newId = userProfileDAO.insert(userProfile)
        Log.d("FitquestDB", "User inserted successfully with ID: $newId")
        return newId
    }

    // Multiple users at once without needing their IDs back.
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

    suspend fun getProfileById(userId: Int): UserProfile? {
        val userProfile = userProfileDAO.getProfileByUserId(userId)
        Log.d("FitquestDB", "Fetched profile: $userProfile")
        return userProfile
    }

    suspend fun getAllUsers(): List<User> {
        val users = userDAO.getAllUsers()
        Log.d("FitquestDB", "Fetched all users: $users")
        return users
    }
}
