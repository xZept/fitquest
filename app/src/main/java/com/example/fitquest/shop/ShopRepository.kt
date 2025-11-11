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

    /* ---------------- MONSTERS ---------------- */

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

    /* ---------------- ITEMS  ---------------- */

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

    /**
     * Purchase logic:
     *  - If consumable → old behavior (stackable quantity)
     *  - If background (code starts with bg_) → non-consumable + tier prereq gate
     */
    suspend fun purchaseItem(userId: Int, code: String): PurchaseResult = db.withTransaction {
        val wdao = db.userWalletDao()
        val idao = db.itemDao()

        val item = idao.getByCode(code) ?: return@withTransaction PurchaseResult.NotFound

        val isBackground = item.code.startsWith("bg_")
        val alreadyQty = idao.getQuantity(userId, code) ?: 0

        if (isBackground) {
            if (alreadyQty > 0) return@withTransaction PurchaseResult.AlreadyOwned

            // Enforce tier prerequisite
            val tier = parseTier(item.code)
            val prevCode = if (tier > 1) previousTierCode(item.code, tier) else null
            if (prevCode != null) {
                val havePrev = (idao.getQuantity(userId, prevCode) ?: 0) > 0
                if (!havePrev) return@withTransaction PurchaseResult.LockedByProgress
            }

            // Coins check
            wdao.ensure(userId)
            val balance = wdao.getCoins(userId) ?: 0
            if (balance < item.price) return@withTransaction PurchaseResult.Insufficient(balance, item.price)


            idao.insertUserItem(UserItem(userId = userId, itemCode = item.code, quantity = 0))
            wdao.add(userId, -item.price)
            idao.addQuantity(userId, item.code, +1)

            return@withTransaction PurchaseResult.Success(wdao.getCoins(userId) ?: 0)
        } else {
            // Consumables
            wdao.ensure(userId)
            val balance = wdao.getCoins(userId) ?: 0
            if (balance < item.price) return@withTransaction PurchaseResult.Insufficient(balance, item.price)

            idao.insertUserItem(UserItem(userId = userId, itemCode = item.code, quantity = 0))
            wdao.add(userId, -item.price)
            idao.addQuantity(userId, item.code, +1)

            return@withTransaction PurchaseResult.Success(wdao.getCoins(userId) ?: 0)
        }
    }

    /* ---------------- Item usage helpers (for Profile, Shop UI) ---------------- */

    suspend fun getItemQuantity(userId: Int, code: String): Int {
        return db.itemDao().getQuantity(userId, code) ?: 0
    }

    suspend fun consumeItem(userId: Int, code: String, qty: Int = 1): Boolean = db.withTransaction {
        val have = db.itemDao().getQuantity(userId, code) ?: 0
        if (have < qty) return@withTransaction false
        db.itemDao().addQuantity(userId, code, -qty)
        true
    }

    /* ---------------- Background helpers ---------------- */

    /**
     * Highest owned tier for a page ("profile" | "shop" | "quest"). 0 if none.
     */
    suspend fun getHighestBackgroundTier(userId: Int, page: String): Int {
        val pageKey = pageKey(page)
        // scan from highest tier downward (6..1)
        for (tier in 6 downTo 1) {
            val code = "bg_${pageKey}_tier_${tier}"
            if ((db.itemDao().getQuantity(userId, code) ?: 0) > 0) return tier
        }
        return 0
    }

    /**
     * True if background item is locked for this user (missing previous tier).
     */
    suspend fun isBackgroundLocked(userId: Int, code: String): Boolean {
        if (!code.startsWith("bg_")) return false
        val tier = parseTier(code)
        val prev = if (tier > 1) previousTierCode(code, tier) else null
        return prev != null && (db.itemDao().getQuantity(userId, prev) ?: 0) == 0
    }

    /* ----- utils ----- */

    private fun parseTier(code: String): Int {
        // bg_{page}_tier_{N}
        val idx = code.lastIndexOf("_tier_")
        return if (idx >= 0) code.substring(idx + 6).toIntOrNull() ?: 1 else 1
    }

    private fun previousTierCode(code: String, tier: Int): String {
        return code.replace("_tier_${tier}", "_tier_${tier - 1}")
    }

    private fun pageKey(page: String): String {
        val x = page.trim().lowercase()
        return when {
            x.contains("profile") -> "profile"
            x.contains("quest") || x.contains("dashboard") -> "quest"
            x.contains("shop") -> "shop"
            else -> x
        }
    }
}
