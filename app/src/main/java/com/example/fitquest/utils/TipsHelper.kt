package com.example.fitquest.utils

import android.util.Log
import com.example.fitquest.models.Tips


object TipsHelper {
    fun getGeneralTips(tips: List<Tips>): List<Tips> {
        return tips.filter { it.category == "general" }
    }

    fun getWorkoutTips(tips: List<Tips>, userGoal: String, userSplit: String, condition: String): List<Tips> {
        return tips.filter {
            it.category.lowercase() == "workout" &&
                    (it.goal.lowercase() == userGoal.lowercase() || it.goal.lowercase() == "any") &&
                    (it.split.lowercase() == userSplit.lowercase() || it.split.lowercase() == "any") &&
                    (it.condition.lowercase() == condition.lowercase() || it.condition.lowercase() == "any")
        }
    }


    fun getMacroTips(tips: List<Tips>, goal: String, condition: String): List<Tips> {
        val safeGoal = if (goal.isBlank()) "any" else goal
        val safeCondition = if (condition.isBlank()) "any" else condition

        // 1) exact match (goal + condition), allowing 'any'
        val exact = tips.filter {
            it.category.equals("macro", ignoreCase = true) &&
                    (it.goal.equals(safeGoal, ignoreCase = true) || it.goal.equals("any", ignoreCase = true)) &&
                    (it.condition.equals(safeCondition, ignoreCase = true) || it.condition.equals("any", ignoreCase = true))
        }
        if (exact.isNotEmpty()) return exact

        // 2) fallback: ignore condition (match by goal or any)
        val byGoal = tips.filter {
            it.category.equals("macro", ignoreCase = true) &&
                    (it.goal.equals(safeGoal, ignoreCase = true) || it.goal.equals("any", ignoreCase = true))
        }
        if (byGoal.isNotEmpty()) return byGoal

        // 3) fallback: ignore goal, prefer condition or any
        val byCondition = tips.filter {
            it.category.equals("macro", ignoreCase = true) &&
                    (it.condition.equals(safeCondition, ignoreCase = true) || it.condition.equals("any", ignoreCase = true))
        }
        if (byCondition.isNotEmpty()) return byCondition

        // 4) final fallback: any macro tips
        return tips.filter { it.category.equals("macro", ignoreCase = true) }
    }




    fun mapGoalToCsv(userGoal: String): String {
        return when (userGoal.lowercase()) {
            "lose fat" -> "weight_loss"
            "build muscle" -> "muscle_gain"
            "maintain" -> "endurance"
            else -> "any"
        }
    }
    fun mapSplitToCsv(split: String): String {
        return when {
            split.contains("1 day") -> "any"
            split.contains("2 days") -> "any"
            split.contains("3 days") -> "any"
            split.contains("4 days") -> "any"
            split.contains("5 days") -> "any"
            split.contains("6 days") -> "any"
            else -> "any"
        }
    }



    fun getRecoveryTips(tips: List<Tips>, condition: String): List<Tips> {
        return tips.filter {
            it.category == "recovery" &&
                    (it.condition == condition || it.condition == "any")
        }
    }
}