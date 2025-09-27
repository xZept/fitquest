package com.example.fitquest.database

import androidx.room.*

// ----- Users -----
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

// ----- User Profile -----
@Dao
interface UserProfileDAO {
    @Insert suspend fun insert(userProfile: UserProfile): Long

    @Query("SELECT * FROM userProfile WHERE userId = :userId LIMIT 1")
    suspend fun getProfileByUserId(userId: Int): UserProfile?

    @Update suspend fun update(userProfile: UserProfile)
}

// ----- User Settings -----
@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM userSettings WHERE userId = :id LIMIT 1")
    suspend fun getByUserId(id: Int): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings): Long

    @Query("DELETE FROM userSettings WHERE userId = :id")
    suspend fun deleteForUser(id: Int)
}

// ----- User Wallet -----
@Dao
interface UserWalletDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(wallet: UserWallet): Long

    @Query("INSERT OR IGNORE INTO userWallet(userId, coins) VALUES(:userId, 0)")
    suspend fun ensure(userId: Int)

    @Query("UPDATE userWallet SET coins = coins + :delta WHERE userId = :userId")
    suspend fun add(userId: Int, delta: Int)

    @Query("SELECT coins FROM userWallet WHERE userId = :userId LIMIT 1")
    suspend fun getCoins(userId: Int): Int?

    @Query("UPDATE userWallet SET coins = :value WHERE userId = :userId")
    suspend fun setCoins(userId: Int, value: Int)
}

// ----- Active Quest -----
@Dao
interface ActiveQuestDao {
    @Query("SELECT * FROM activeQuests WHERE userId = :uid LIMIT 1")
    suspend fun getActiveForUser(uid: Int): ActiveQuest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(q: ActiveQuest): Long

    @Query("DELETE FROM activeQuests WHERE userId = :uid")
    suspend fun clearForUser(uid: Int)
}

// ----- Workout Session -----
@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("""
        UPDATE workoutSession
        SET endedAt = :endedAt,
            completedSets = :completedSets,
            coinsEarned = :coinsEarned
        WHERE id = :sessionId
    """)
    suspend fun finishSession(
        sessionId: Long,
        endedAt: Long,
        completedSets: Int,
        coinsEarned: Int
    )

    @Query("SELECT * FROM workoutSession WHERE userId = :userId ORDER BY startedAt DESC")
    suspend fun getAllByUser(userId: Int): List<WorkoutSession>

    @Query("SELECT * FROM workoutSession WHERE userId = :userId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestForUser(userId: Int): WorkoutSession?

    @Query("SELECT * FROM workoutSession WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: Long): WorkoutSession?

    @Query("DELETE FROM workoutSession WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)
}

// ----- Workout Set Log -----
@Dao
interface WorkoutSetLogDao {
    @Insert
    suspend fun insert(log: WorkoutSetLog): Long

    @Insert
    suspend fun insertAll(logs: List<WorkoutSetLog>): List<Long>

    @Query("SELECT * FROM workoutSetLog WHERE sessionId = :sessionId ORDER BY setNumber ASC, id ASC")
    suspend fun getForSession(sessionId: Long): List<WorkoutSetLog>

    @Query("DELETE FROM workoutSetLog WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFood(food: Food): Long

    @Query("SELECT foodId FROM food WHERE normalizedName = :n LIMIT 1")
    suspend fun findIdByNormalizedName(n: String): Long?

    @Transaction
    suspend fun upsert(food: Food): Long {
        val id = insertFood(food)
        if (id != -1L) return id
        return findIdByNormalizedName(food.normalizedName)
            ?: error("Duplicate but no existing id for ${food.normalizedName}")
    }
}



