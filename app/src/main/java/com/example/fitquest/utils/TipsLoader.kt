package com.example.fitquest.utils

import android.content.Context
import com.example.fitquest.models.Tips
import java.io.BufferedReader
import java.io.InputStreamReader

object TipsLoader {
    fun loadTips(context: Context, fileName: String = "tips_dataset.csv"): List<Tips> {
        val out = mutableListOf<Tips>()
        try {
            val input = context.assets.open(fileName)
            BufferedReader(InputStreamReader(input)).use { br ->
                val header = br.readLine() ?: return emptyList() // skip header
                br.lineSequence().forEach { raw ->
                    if (raw.isBlank()) return@forEach
                    val cols = raw.parseCsvLine()  // ✅ use shared parser
                    if (cols.size < 6) return@forEach
                    // tip text might contain commas so join remaining columns
                    val id = cols[0].trim().toIntOrNull() ?: return@forEach
                    val category = cols[1].trim()
                    val goal = cols[2].trim()
                    val split = cols[3].trim()
                    val condition = cols[4].trim()
                    val tipText = cols.subList(5, cols.size).joinToString(",").trim().removeSurrounding("\"")
                    out.add(Tips(id, category, goal, split, condition, tipText))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }
}
