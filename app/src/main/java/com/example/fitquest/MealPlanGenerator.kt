package com.example.fitquest

import android.content.Context
import java.io.Serializable

// ✅ Data class matches your CSV fields
data class MealItem(
    val mealId: Int,
    val mealName: String,
    val mealType: String,   // Breakfast, Lunch, Dinner
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val goal: String,       // Weight Loss, Muscle Gain, etc.
    val dietType: String    // Regular, Vegetarian, etc.
) : Serializable

// ✅ Reads all meals from meal_dataset.csv
fun loadMeals(context: Context): List<MealItem> {
    val meals = mutableListOf<MealItem>()
    val reader = context.assets.open("meal_dataset.csv").bufferedReader()
    reader.readLine() // skip header row

    reader.forEachLine { line ->
        val parts = line.split(",")
        if (parts.size >= 9) {
            meals.add(
                MealItem(
                    mealId = parts[0].toInt(),
                    mealName = parts[1],
                    mealType = parts[2],
                    calories = parts[3].toInt(),
                    protein = parts[4].toInt(),
                    carbs = parts[5].toInt(),
                    fat = parts[6].toInt(),
                    goal = parts[7],
                    dietType = parts[8]
                )
            )
        }
    }
    return meals
}

// ✅ Filters meals and generates one plan per day
fun generateMealPlan(context: Context, goal: String, dietType: String): List<MealItem> {
    val allMeals = loadMeals(context)

    val breakfast = allMeals.filter { it.mealType == "Breakfast" && it.goal == goal && it.dietType == dietType }
    val lunch = allMeals.filter { it.mealType == "Lunch" && it.goal == goal && it.dietType == dietType }
    val dinner = allMeals.filter { it.mealType == "Dinner" && it.goal == goal && it.dietType == dietType }

    // Pick one random meal for each type (if available)
    return listOfNotNull(
        breakfast.randomOrNull(),
        lunch.randomOrNull(),
        dinner.randomOrNull()
    )
}
