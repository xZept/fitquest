package com.example.fitquest.cosmetics

import android.content.Context
import android.graphics.BitmapFactory
import com.example.fitquest.shop.ShopRepository
import com.example.fitquest.ui.widgets.SpriteSheetDrawable

/**
 * Small helper to resolve and build background sprites by page & tier.
 * We always display tier 0 by default, and upgrade to the highest owned tier.
 */
object BgCosmetics {

    enum class Page { PROFILE, SHOP, QUEST } // QUEST covers QuestGenerator + QuestPreview

    private const val MAX_TIER = 6
    private const val ROWS = 1
    private const val COLS = 12
    private const val FPS  = 12

    fun itemCode(page: Page, tier: Int): String = when (page) {
        Page.PROFILE -> "bg_profile_tier_$tier"
        Page.SHOP    -> "bg_shop_tier_$tier"
        Page.QUEST   -> "bg_quest_tier_$tier"
    }

    private fun spriteName(page: Page, tier: Int): String = when (page) {
        Page.PROFILE -> "bg_page_profile_spritesheet$tier"
        Page.SHOP    -> "bg_page_shop_spritesheet$tier"
        // your quest pages use the "dashboard" spritesheets
        Page.QUEST   -> "bg_page_dashboard_spritesheet$tier"
    }

    fun spriteResId(context: Context, page: Page, tier: Int): Int {
        val r = context.resources
        val pkg = context.packageName
        val id = r.getIdentifier(spriteName(page, tier), "drawable", pkg)
        return if (id != 0) id
        else r.getIdentifier(spriteName(page, 0), "drawable", pkg) // safe fallback
    }

    /** Highest tier the user owns for this page (0 if none). */
    suspend fun highestOwnedTier(userId: Int, repo: ShopRepository, page: Page): Int {
        for (t in MAX_TIER downTo 1) {
            if (repo.getItemQuantity(userId, itemCode(page, t)) > 0) return t
        }
        return 0
    }

    /** Build a looping SpriteSheetDrawable for the requested page/tier. */
    fun buildDrawable(context: Context, page: Page, tier: Int): SpriteSheetDrawable {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(context.resources, spriteResId(context, page, tier), opts)
        return SpriteSheetDrawable(
            sheet = bmp,
            rows = ROWS,
            cols = COLS,
            fps  = FPS,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )
    }
}
