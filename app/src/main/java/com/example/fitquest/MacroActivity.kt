package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MacroActivity : AppCompatActivity() {

    data class MealItem(
        val mealId: Int,
        val mealName: String,
        val mealType: String,
        val calories: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int,
        val goal: String,
        val dietType: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro)

        // hides the system navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        // Find the nav icons
        val navDashboard = findViewById<ImageView>(R.id.nav_icon_dashboard)
        val navShop = findViewById<ImageView>(R.id.nav_icon_shop)
        val navProfile = findViewById<ImageView>(R.id.nav_icon_profile)
        val navWorkout = findViewById<ImageView>(R.id.nav_icon_workout)
        val navMacro = findViewById<ImageView>(R.id.nav_icon_macro)

        // Set click listeners
        navDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navShop.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navWorkout.setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navMacro.setOnClickListener {
            // You're already on dashboard, optionally do nothing
        }

//        val startWorkoutBtn: Button = findViewById(R.id.btn_start_workout)
//        startWorkoutBtn.setOnClickListener {
//            val intent = Intent(this, WorkoutGeneratorActivity::class.java)
//            startActivity(intent)
//        }

        // ✅ Get the goal and diet type passed from WorkoutGeneratorActivity
        val goal = intent.getStringExtra("GOAL") ?: ""
        val dietType = intent.getStringExtra("DIET_TYPE") ?: ""

        // ✅ Get references to meal containers
        val breakfastContainer = findViewById<LinearLayout>(R.id.breakfastContainer)
        val snackContainer = findViewById<LinearLayout>(R.id.snackContainer)
        val lunchContainer = findViewById<LinearLayout>(R.id.lunchContainer)
        val dinnerContainer = findViewById<LinearLayout>(R.id.dinnerContainer)

        // ✅ Load meals filtered by user’s goal + diet type
        val meals = loadMealsFromCSV(this).filter {
            (goal.isEmpty() || it.goal.equals(goal, ignoreCase = true)) &&
                    (dietType.isEmpty() || it.dietType.equals(dietType, ignoreCase = true))
        }

        // Helper to pick 4–7 random meals for each type
        fun pickRandomMeals(list: List<MealItem>): List<MealItem> {
            if (list.isEmpty()) return emptyList()
            val count = (4..7).random().coerceAtMost(list.size)
            return list.shuffled().take(count)
        }

        // ✅ Populate containers by type
        pickRandomMeals(meals.filter { it.mealType.equals("Breakfast", true) })
            .forEach { addMealToContainer(it, breakfastContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Snack", true) })
            .forEach { addMealToContainer(it, snackContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Lunch", true) })
            .forEach { addMealToContainer(it, lunchContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Dinner", true) })
            .forEach { addMealToContainer(it, dinnerContainer) }
    }

    // Adds a meal row into the container
    // Adds a meal row into the container
    // Adds a meal row into the container
    private fun addMealToContainer(meal: MealItem, container: LinearLayout) {
        val mealView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame) // card-style
        }

        val nameText = TextView(this).apply {
            text = meal.mealName
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val calorieText = TextView(this).apply {
            text = "${meal.calories} kcal"
            textSize = 14f
        }

        mealView.addView(nameText)
        mealView.addView(calorieText)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)

        // ✅ Long press to show dialog
        mealView.setOnLongClickListener {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle(meal.mealName)
            builder.setMessage("What do you want to do?")

            // Delete button
            builder.setPositiveButton("Delete") { dialog, _ ->
                container.removeView(mealView) // removes the meal row
                dialog.dismiss()
            }

            // Edit button (no functionality yet)
            builder.setNegativeButton("Edit") { dialog, _ ->
                dialog.dismiss()
            }

            // Cancel button
            builder.setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            builder.show()
            true
        }

        container.addView(mealView, params)
    }



    // Loads meals from CSV in assets
    private fun loadMealsFromCSV(context: Context): List<MealItem> {
        val meals = mutableListOf<MealItem>()
        try {
            val inputStream = context.assets.open("meal_dataset.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split(",")
                if (tokens.size >= 9) {
                    val meal = MealItem(
                        mealId = tokens[0].toInt(),
                        mealName = tokens[1],
                        mealType = tokens[2],
                        calories = tokens[3].toInt(),
                        protein = tokens[4].toInt(),
                        carbs = tokens[5].toInt(),
                        fat = tokens[6].toInt(),
                        goal = tokens[7],
                        dietType = tokens[8]
                    )
                    meals.add(meal)
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return meals
    }
}
