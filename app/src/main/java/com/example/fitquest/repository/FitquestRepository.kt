// app/src/main/java/com/example/fitquest/repository/FitquestRepository.kt
package com.example.fitquest.repository

import android.content.Context
import android.util.Log
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile

class FitquestRepository(context: Context) {

    // Use the singleton — it already has fallbackToDestructiveMigration() in AppDatabase.
    private val db: AppDatabase = AppDatabase.getInstance(context)

    private val userDAO = db.userDAO()
    private val userProfileDAO = db.userProfileDAO()

    // --- Users ---

    /** Inserts a single user and returns the new rowId (userId). */
    suspend fun insertUser(user: User): Long {
        Log.d("FitquestDB", "Inserting user: $user")
        val newId = userDAO.insert(user)
        Log.d("FitquestDB", "User inserted with ID: $newId")
        return newId
    }

    /** Returns the authenticated user's id, or null if no match. */
    suspend fun authenticateUser(username: String, password: String): Int? {
        Log.d("FitquestDB", "Authenticating user: username=$username")
        val user = userDAO.authenticateUser(username, password)
        Log.d("FitquestDB", "authenticateUser() -> $user")
        return user?.userId
    }

    suspend fun getAllUsers(): List<User> {
        val users = userDAO.getAllUsers()
        Log.d("FitquestDB", "Fetched users: $users")
        return users
    }

    // --- Profiles ---

    /** Insert profile rows captured during registration. */
    suspend fun insertUserProfile(userProfile: UserProfile): Long {
        Log.d("FitquestDB", "Inserting profile: $userProfile")
        val newId = userProfileDAO.insert(userProfile)
        Log.d("FitquestDB", "Profile inserted with ID: $newId")
        return newId
    }

    suspend fun getProfileById(userId: Int): UserProfile? {
        val profile = userProfileDAO.getProfileByUserId(userId)
        Log.d("FitquestDB", "Fetched profile($userId): $profile")
        return profile
    }
}
