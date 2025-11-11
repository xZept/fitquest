package com.example.fitquest.utils

import com.example.fitquest.database.MacroPlan
import kotlin.math.round

object MacroCalculator {

    data class Input(
        val userId: Int,
        val age: Int,
        val sex: String,
        val weightKg: Double,
        val heightCm: Double,
        val activityLevel: String,
        val goal: String
    )

    fun calculatePlan(i: Input): MacroPlan {
        val isMale = i.sex.trim().lowercase().startsWith("m")

        val bmr = if (isMale)
            10.0 * i.weightKg + 6.25 * i.heightCm - 5.0 * i.age + 5.0
        else
            10.0 * i.weightKg + 6.25 * i.heightCm - 5.0 * i.age - 161.0

        val act = when (i.activityLevel.trim().lowercase()) {
            "sedentary" -> 1.20
            "lightly active" -> 1.375
            "moderately active" -> 1.55
            "very active" -> 1.725
            else -> 1.20
        }
        val tdee = bmr * act

        val goalAdj = when (i.goal.trim().lowercase()) {
            "build muscle" -> 1.10
            "lose fat" -> 0.80
            else -> 1.00
        }
        val targetCalories = roundToNearest10(tdee * goalAdj).toInt()

        val proteinPerKg = when (i.goal.trim().lowercase()) {
            "build muscle" -> 1.8
            "lose fat" -> 2.0
            else -> 1.6
        }
        val proteinG = round(i.weightKg * proteinPerKg).toInt()

        val fatPct = when (i.goal.trim().lowercase()) {
            "lose fat" -> 0.25
            else -> 0.30
        }
        val fatGFromPct = round((targetCalories * fatPct) / 9.0).toInt()
        val fatFloorG = round(i.weightKg * 0.6).toInt()
        val fatG = maxOf(fatFloorG, fatGFromPct)

        val proteinKcal = proteinG * 4
        val fatKcal = fatG * 9
        val carbsKcal = (targetCalories - (proteinKcal + fatKcal)).coerceAtLeast(0)
        val carbsG = round(carbsKcal / 4.0).toInt()

        return MacroPlan(
            userId = i.userId,
            calories = targetCalories,
            protein = proteinG,
            fat = fatG,
            carbs = carbsG
        )
    }

    private fun roundToNearest10(v: Double): Int =
        (round(v / 10.0) * 10.0).toInt()

}