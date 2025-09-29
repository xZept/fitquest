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

// ----- Quest History -----
@Dao
interface QuestHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: QuestHistory): Long

    // FIX: use backticks around the column name `key`
    @Query("UPDATE questHistory SET lastUsedAt = :ts WHERE userId = :userId AND `key` = :questKey")
    suspend fun touch(userId: Int, questKey: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE questHistory SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("""
        SELECT * FROM questHistory
        WHERE userId = :userId
        ORDER BY pinned DESC, lastUsedAt DESC
    """)
    suspend fun getAllOrdered(userId: Int): List<QuestHistory>

    @Query("""
        DELETE FROM questHistory
        WHERE userId = :userId AND pinned = 0
        AND id NOT IN (
            SELECT id FROM questHistory
            WHERE userId = :userId AND pinned = 0
            ORDER BY lastUsedAt DESC
            LIMIT 10
        )
    """)
    suspend fun pruneUnpinned(userId: Int)

    @Query("SELECT * FROM questHistory WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getById(userId: Int, id: Long): QuestHistory?
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

    @Query("SELECT * FROM workoutSession WHERE userId = :userId AND coinsEarned > 0 ORDER BY startedAt DESC")
    suspend fun getCompletedByUser(userId: Int): List<WorkoutSession>
}

// ----- Workout Set Log -----
@Dao
interface WorkoutSetLogDao {
    @Insert
    suspend fun insert(log: WorkoutSetLog): Long

    @Insert
    suspend fun insertAll(logs: List<WorkoutSetLog>): List<Long>

    @Query("""
    SELECT * FROM workoutSetLog
    WHERE sessionId = :sessionId
    ORDER BY exerciseName COLLATE NOCASE ASC, setNumber ASC, id ASC
""")
    suspend fun getForSession(sessionId: Long): List<WorkoutSetLog>

    @Query("DELETE FROM workoutSetLog WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

// ----- Food -----
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

// ----- Monster Shop -----
data class MonsterListItem(
    val code: String,
    val name: String,
    val spriteRes: String,
    val price: Int,
    val owned: Boolean
)

@Dao
interface MonsterDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(monsters: List<Monster>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(monster: Monster): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun own(row: UserMonster): Long

    @Query("SELECT * FROM monster WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Monster?

    @Query("SELECT EXISTS(SELECT 1 FROM userMonster WHERE userId = :userId AND monsterCode = :code)")
    suspend fun isOwned(userId: Int, code: String): Boolean

    @Query("DELETE FROM monster WHERE code NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)

    // NEW: allow price updates during seeding
    @Query("UPDATE monster SET price = :price WHERE code = :code")
    suspend fun updatePrice(code: String, price: Int)

    // NEW (optional): allow changing display name or sprite without changing code
    @Query("UPDATE monster SET name = :name, spriteRes = :spriteRes WHERE code = :code")
    suspend fun updateMeta(code: String, name: String, spriteRes: String)

    @Query("""
        SELECT m.code AS code, m.name AS name, m.spriteRes AS spriteRes, m.price AS price,
               CASE WHEN um.id IS NULL THEN 0 ELSE 1 END AS owned
        FROM monster m
        LEFT JOIN userMonster um
          ON um.monsterCode = m.code AND um.userId = :userId
        ORDER BY m.price ASC, m.name ASC
    """)
    suspend fun listForUser(userId: Int): List<MonsterListItem>

    @Query("""
        SELECT m.* FROM userMonster um
        JOIN monster m ON m.code = um.monsterCode
        WHERE um.userId = :userId
        ORDER BY um.acquiredAt DESC
        LIMIT 1
    """)
    suspend fun getLatestOwnedForUser(userId: Int): Monster?
}
