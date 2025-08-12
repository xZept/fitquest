package com.example.fitquest

data class MealSlot(
    val type: MealType,
    val hasMeal: Boolean
)

enum class MealType {
    BREAKFAST, LUNCH, DINNER
}
