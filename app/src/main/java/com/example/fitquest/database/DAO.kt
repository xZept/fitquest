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

    // backticked `key`
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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(food: Food): Long

    @Query("SELECT * FROM food WHERE foodId = :id")
    fun getById(id: Long): Food?

    @Query("SELECT * FROM food WHERE normalizedName = :norm LIMIT 1")
    fun getByNormalizedName(norm: String): Food?

    @Query("SELECT * FROM food ORDER BY lastUpdated DESC LIMIT :limit")
    fun recent(limit: Int): List<Food>
}

// ----- Monster Shop -----
data class MonsterListItem(
    val code: String,
    val name: String,
    val spriteRes: String,
    val price: Int,
    val owned: Boolean,
    val locked: Boolean   // ← NEW: locked if cheaper monsters aren’t owned yet
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

    // Allow price/name/sprite updates without recreating rows
    @Query("UPDATE monster SET price = :price WHERE code = :code")
    suspend fun updatePrice(code: String, price: Int)

    @Query("UPDATE monster SET name = :name, spriteRes = :spriteRes WHERE code = :code")
    suspend fun updateMeta(code: String, name: String, spriteRes: String)

    // Number of cheaper monsters the user hasn't bought (progress prereqs)
    @Query("""
        SELECT COUNT(*) FROM monster m
        WHERE m.price < (SELECT price FROM monster WHERE code = :code)
          AND NOT EXISTS (
              SELECT 1 FROM userMonster um
              WHERE um.userId = :userId AND um.monsterCode = m.code
          )
    """)
    suspend fun countMissingPrereqs(userId: Int, code: String): Int

    // List with owned+locked flags (locked if any cheaper unowned monster exists)
    @Query("""
        SELECT 
            m.code       AS code,
            m.name       AS name,
            m.spriteRes  AS spriteRes,
            m.price      AS price,
            CASE WHEN um.id IS NULL THEN 0 ELSE 1 END AS owned,
            CASE WHEN EXISTS (
                SELECT 1
                FROM monster m2
                WHERE m2.price < m.price
                  AND NOT EXISTS (
                      SELECT 1 FROM userMonster um2
                      WHERE um2.userId = :userId AND um2.monsterCode = m2.code
                  )
            ) THEN 1 ELSE 0 END AS locked
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

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM foodLog WHERE logId = :logId LIMIT 1")
    suspend fun getById(logId: Long): FoodLog?

    @Query("""
        UPDATE foodLog
        SET grams = :grams,
            calories = :calories,
            protein = :protein,
            carbohydrate = :carbohydrate,
            fat = :fat
        WHERE logId = :logId
    """)
    suspend fun updateServing(
        logId: Long,
        grams: Double,
        calories: Double,
        protein: Double,
        carbohydrate: Double,
        fat: Double
    ): Int

    @Query("DELETE FROM foodLog WHERE logId = :logId")
    suspend fun deleteById(logId: Long): Int

    // Fallback: delete by passing the whole entity (works even if your PK name differs)
    @Delete
    suspend fun delete(log: FoodLog)

    @Query("UPDATE foodLog SET grams = :grams WHERE logId = :logId")
    suspend fun updateGrams(logId: Long, grams: Double)

    @Insert
    fun insert(log: FoodLog): Long

    @Query("""
        SELECT fl.*, f.foodName AS foodName
        FROM foodLog AS fl
        LEFT JOIN food AS f ON f.foodId = fl.foodId
        WHERE fl.userId = :uid AND fl.dayKey = :dayKey
        ORDER BY fl.loggedAt DESC
    """)
    suspend fun logsForDay(uid: Int, dayKey: Int): List<FoodLogRow>

    @Query("""
        SELECT 
            COALESCE(SUM(calories), 0)      AS calories,
            COALESCE(SUM(protein), 0)       AS protein,
            COALESCE(SUM(carbohydrate), 0)  AS carbohydrate,
            COALESCE(SUM(fat), 0)           AS fat
        FROM foodLog
        WHERE userId = :uid AND dayKey = :dayKey
    """)
    suspend fun totalsForDay(uid: Int, dayKey: Int): DayTotals

    @Query("SELECT * FROM foodLog WHERE userId = :userId ORDER BY loggedAt DESC LIMIT :limit")
    fun recent(userId: Int, limit: Int): List<FoodLog>

    @Query("""
        SELECT * FROM foodLog
        WHERE userId = :uid AND dayKey = :dayKey
        ORDER BY loggedAt ASC
    """)
    suspend fun entriesForDay(uid: Int, dayKey: Int): List<FoodLog>
}

data class FoodLogRow(
    @Embedded val log: FoodLog,
    @ColumnInfo(name = "foodName") val foodName: String?
)

data class DayTotals(
    val calories: Double,
    val protein: Double,
    val carbohydrate: Double,
    val fat: Double
)

@Dao
interface PortionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(portions: List<Portion>)

    @Query("SELECT * FROM portion WHERE foodId = :foodId ORDER BY portionId ASC")
    fun getForFood(foodId: Long): List<Portion>
}

@Dao
interface MacroPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: MacroPlan): Long

    @Query("""SELECT * FROM macroPlan WHERE userId = :userId ORDER BY updatedAt DESC LIMIT 1""")
    suspend fun getLatestForUser(userId: Int): MacroPlan?
}
