package com.example.fitquest.utils

import android.content.Context
import com.example.fitquest.models.Tips
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object TipsLoader {
    private val csvSplitRegex = Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")

    private fun String.unquote() = trim().removePrefix("\"").removeSuffix("\"").trim()
    private fun String.normOrAny() = trim().lowercase(Locale.ROOT).ifBlank { "any" }

    fun loadTips(context: Context, fileName: String = "tips_dataset.csv"): List<Tips> {
        val out = mutableListOf<Tips>()
        val input = context.assets.open(fileName)
        BufferedReader(InputStreamReader(input)).use { br ->
            val header = br.readLine() ?: return emptyList()
            val cols = header.split(',').map { it.trim().lowercase(Locale.ROOT) }
            fun idx(name: String) = cols.indexOf(name).takeIf { it >= 0 }

            val iId            = idx("tips_id") ?: 0
            val iCategory      = idx("category") ?: 1
            val iTip           = idx("tip") ?: 2
            val iSplit         = idx("split")
            val iFocus         = idx("focus")
            val iGoal          = idx("goal")
            val iActivityLevel = idx("activity_level")
            val iTags          = idx("tags")

            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val parts = csvSplitRegex.split(raw).map { it.unquote() }
                val id  = parts.getOrNull(iId)?.toIntOrNull() ?: return@forEach
                val cat = parts.getOrNull(iCategory)?.lowercase(Locale.ROOT) ?: return@forEach
                val tip = parts.getOrNull(iTip)?.ifBlank { null } ?: return@forEach
                val split = parts.getOrNull(iSplit ?: -1)?.normOrAny() ?: "any"
                val focus = parts.getOrNull(iFocus ?: -1)?.normOrAny() ?: "any"
                val goal  = parts.getOrNull(iGoal ?: -1)?.normOrAny() ?: "any"
                val act   = parts.getOrNull(iActivityLevel ?: -1)?.normOrAny() ?: "any"
                val tags  = parts.getOrNull(iTags ?: -1)?.trim().orEmpty()
                out.add(Tips(id = id, category = cat, tip = tip, split = split, focus = focus, goal = goal, activityLevel = act, tags = tags))
            }
        }
        return out
    }
}
