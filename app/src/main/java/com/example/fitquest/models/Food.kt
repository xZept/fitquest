package com.example.fitquest.models

import com.example.fitquest.database.Food
import com.example.fitquest.database.Portion

class Food {
    enum class MeasurementType {
        GRAM, MILLILITER, CUP, TABLESPOON, TEASPOON, OUNCE, FL_OUNCE, POUND, PIECE, SANDOK
    }

    data class PortionMacros(
        val calories: Double, val protein: Double, val carbs: Double, val fat: Double
    )

    fun macrosForPortion(food: Food, portion: Portion): PortionMacros {
        val factor = portion.gramWeight / 100.0
        return PortionMacros(
            calories = food.kcalPer100g * factor,
            protein  = food.proteinPer100g * factor,
            carbs    = food.carbPer100g * factor,
            fat      = food.fatPer100g * factor
        )
    }
}