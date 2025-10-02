package com.example.fitquest.shop

import androidx.room.withTransaction
import com.example.fitquest.database.*

sealed class PurchaseResult {
    data class Success(val newBalance: Int) : PurchaseResult()
    data class Insufficient(val balance: Int, val price: Int) : PurchaseResult()
    object AlreadyOwned : PurchaseResult()
    object NotFound : PurchaseResult()
    object LockedByProgress : PurchaseResult()   // ← NEW
}

class ShopRepository(private val db: AppDatabase) {

    suspend fun seedMonsters(vararg monsters: Monster) {
        db.monsterDao().insertAllIgnore(monsters.toList())
    }

    suspend fun list(userId: Int): List<MonsterListItem> =
        db.monsterDao().listForUser(userId)

    suspend fun getBalance(userId: Int): Int {
        db.userWalletDao().ensure(userId)
        return db.userWalletDao().getCoins(userId) ?: 0
    }

    suspend fun purchase(userId: Int, code: String): PurchaseResult = db.withTransaction {
        val mdao = db.monsterDao()
        val wdao = db.userWalletDao()

        val monster = mdao.getByCode(code) ?: return@withTransaction PurchaseResult.NotFound
        if (mdao.isOwned(userId, code)) return@withTransaction PurchaseResult.AlreadyOwned

        // Progression gate: must own all cheaper monsters
        val missing = mdao.countMissingPrereqs(userId, code)
        if (missing > 0) return@withTransaction PurchaseResult.LockedByProgress

        wdao.ensure(userId)
        val balance = wdao.getCoins(userId) ?: 0
        if (balance < monster.price) return@withTransaction PurchaseResult.Insufficient(balance, monster.price)

        wdao.add(userId, -monster.price)
        mdao.own(UserMonster(userId = userId, monsterCode = code))
        val newBal = wdao.getCoins(userId) ?: 0
        PurchaseResult.Success(newBal)
    }
}
