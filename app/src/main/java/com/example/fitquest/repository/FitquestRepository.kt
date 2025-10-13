package com.example.fitquest.repository

import android.content.Context
import android.util.Log
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.MacroPlan
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.utils.MacroCalculator

class FitquestRepository(context: Context) {

    // Use the singleton — it already has fallbackToDestructiveMigration() in AppDatabase.
    private val db: AppDatabase = AppDatabase.getInstance(context)

    private val userDAO = db.userDAO()
    private val userProfileDAO = db.userProfileDAO()
    private val macroPlanDao = db.macroPlanDao()

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



    // Macro calculation
    suspend fun computeAndSaveMacroPlan(userId: Int): MacroPlan {
        val user = userDAO.getUserById(userId)
            ?: error("User $userId not found")
        val profile = userProfileDAO.getProfileByUserId(userId)
            ?: error("UserProfile for $userId not found")

        val input = MacroCalculator.Input(
            userId = userId,
            age = user.age,
            sex = user.sex,
            weightKg   = profile.weight.toDouble(),
            heightCm   = profile.height.toDouble(),
            activityLevel = profile.activityLevel ?: "sedentary",
            goal          = profile.goal ?: "maintain"
        )

        val plan = MacroCalculator.calculatePlan(input)
        macroPlanDao.upsert(plan)
        Log.d("FitquestMacros", "Saved MacroPlan for $userId -> $plan")

        macroPlanDao.upsert(plan)

        // Mirror plan into today's MacroDiary (preserve actual intake so far)
        val zone = java.time.ZoneId.of("Asia/Manila")
        val now  = java.time.ZonedDateTime.now(zone)
        val dk   = now.year * 10_000 + now.monthValue * 100 + now.dayOfMonth

        val totals = db.foodLogDao().totalsForDay(userId, dk)

        db.macroDiaryDao().upsert(
            com.example.fitquest.database.MacroDiary(
                userId = userId,
                dayKey = dk,
                calories = totals.calories.toInt(),
                protein  = totals.protein.toInt(),
                carbs    = totals.carbohydrate.toInt(),
                fat      = totals.fat.toInt(),
                planCalories = plan.calories,
                planProtein  = plan.protein,
                planCarbs    = plan.carbs,
                planFat      = plan.fat,
                capturedAt   = System.currentTimeMillis()
            )
        )
        return plan
    }


    suspend fun getMacroPlan(userId: Int): MacroPlan? =
        macroPlanDao.getLatestForUser(userId)
}
