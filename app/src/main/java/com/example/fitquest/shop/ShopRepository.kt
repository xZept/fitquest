package com.example.fitquest.shop

import androidx.room.withTransaction
import com.example.fitquest.database.*

sealed class PurchaseResult {
    data class Success(val newBalance: Int) : PurchaseResult()
    data class Insufficient(val balance: Int, val price: Int) : PurchaseResult()
    object AlreadyOwned : PurchaseResult()
    object NotFound : PurchaseResult()
    object LockedByProgress : PurchaseResult()
}

class ShopRepository(private val db: AppDatabase) {

    /* ---------------- MONSTERS (existing) ---------------- */

    suspend fun seedMonsters(vararg monsters: Monster) {
        val mdao = db.monsterDao()
        monsters.forEach { m ->
            val inserted = mdao.insertIgnore(m)
            if (inserted == -1L) {
                mdao.updatePrice(m.code, m.price)
                mdao.updateMeta(m.code, m.name, m.spriteRes)
            }
        }
        mdao.deleteAllExcept(monsters.map { it.code })
    }

    suspend fun purchase(userId: Int, code: String): PurchaseResult = db.withTransaction {
        val wdao = db.userWalletDao()
        val mdao = db.monsterDao()

        val monster = mdao.getByCode(code) ?: return@withTransaction PurchaseResult.NotFound

        // must own all cheaper monsters
        val missing = mdao.countMissingPrereqs(userId, code)
        if (missing > 0) return@withTransaction PurchaseResult.LockedByProgress

        wdao.ensure(userId)
        val balance = wdao.getCoins(userId) ?: 0
        if (balance < monster.price) return@withTransaction PurchaseResult.Insufficient(balance, monster.price)

        if (mdao.isOwned(userId, code)) return@withTransaction PurchaseResult.AlreadyOwned

        wdao.add(userId, -monster.price)
        mdao.own(UserMonster(userId = userId, monsterCode = code))
        val newBal = wdao.getCoins(userId) ?: 0
        PurchaseResult.Success(newBal)
    }

    suspend fun getBalance(userId: Int): Int = db.userWalletDao().getCoins(userId) ?: 0

    /* ---------------- ITEMS (new) ---------------- */

    suspend fun seedItems(vararg items: Item) {
        val idao = db.itemDao()
        items.forEach { it ->
            val inserted = idao.insertIgnore(it)
            if (inserted == -1L) {
                idao.updatePrice(it.code, it.price)
                idao.updateMeta(
                    code = it.code,
                    name = it.name,
                    spriteRes = it.spriteRes,
                    consumable = it.consumable,
                    category = it.category,
                    description = it.description
                )
            }
        }
        idao.deleteAllExcept(items.map { it.code })
    }

    suspend fun listItemsForUser(userId: Int) = db.itemDao().listForUser(userId)

    suspend fun purchaseItem(userId: Int, code: String): PurchaseResult = db.withTransaction {
        val wdao = db.userWalletDao()
        val idao = db.itemDao()

        val item = idao.getByCode(code) ?: return@withTransaction PurchaseResult.NotFound

        wdao.ensure(userId)
        val balance = wdao.getCoins(userId) ?: 0
        if (balance < item.price) return@withTransaction PurchaseResult.Insufficient(balance, item.price)

        // unlimited purchases allowed
        idao.insertUserItem(UserItem(userId = userId, itemCode = code, quantity = 0))
        wdao.add(userId, -item.price)
        idao.addQuantity(userId, code, +1)

        PurchaseResult.Success(wdao.getCoins(userId) ?: 0)
    }

    /* ---------------- Item usage helpers (for Profile) ---------------- */

    suspend fun getItemQuantity(userId: Int, code: String): Int {
        return db.itemDao().getQuantity(userId, code) ?: 0
    }

    suspend fun consumeItem(userId: Int, code: String, qty: Int = 1): Boolean = db.withTransaction {
        val have = db.itemDao().getQuantity(userId, code) ?: 0
        if (have < qty) return@withTransaction false
        db.itemDao().addQuantity(userId, code, -qty)
        true
    }
}
