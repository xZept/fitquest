package com.example.fitquest.utils

import android.content.Context
import com.example.fitquest.models.Tips
import kotlin.random.Random

object TipManager {
    private const val PREFS_NAME = "TipPrefs"
    private const val LAST_DATE = "last_date"
    private const val LAST_TIP_ID = "last_tip_id"

    fun getDailyTip(context: Context, tips: List<Tips>, category: String): Tips? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24) // day count
        val lastDate = prefs.getLong(LAST_DATE + category, -1)
        val lastTipId = prefs.getInt(LAST_TIP_ID + category, -1)

        return if (today == lastDate) {
            tips.find { it.id == lastTipId }
        } else {
            val newTip = tips.randomOrNull()
            newTip?.let {
                prefs.edit()
                    .putLong(LAST_DATE + category, today)
                    .putInt(LAST_TIP_ID + category, it.id)
                    .apply()
            }
            newTip
        }
    }

    fun getRandomTip(tips: List<Tips>): Tips? {
        return if (tips.isNotEmpty()) tips[Random.nextInt(tips.size)] else null
    }
}