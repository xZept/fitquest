package com.example.fitquest.shop

import androidx.room.withTransaction
import com.example.fitquest.database.*

sealed class PurchaseResult {
    data class Success(val newBalance: Int) : PurchaseResult()
    data class Insufficient(val balance: Int, val price: Int) : PurchaseResult()
    object AlreadyOwned : PurchaseResult()
    object NotFound : PurchaseResult()
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
        val monster = db.monsterDao().getByCode(code) ?: return@withTransaction PurchaseResult.NotFound
        if (db.monsterDao().isOwned(userId, code)) return@withTransaction PurchaseResult.AlreadyOwned

        db.userWalletDao().ensure(userId)
        val balance = db.userWalletDao().getCoins(userId) ?: 0
        if (balance < monster.price) return@withTransaction PurchaseResult.Insufficient(balance, monster.price)

        db.userWalletDao().add(userId, -monster.price)
        db.monsterDao().own(UserMonster(userId = userId, monsterCode = code))
        val newBal = db.userWalletDao().getCoins(userId) ?: 0
        PurchaseResult.Success(newBal)
    }
}
