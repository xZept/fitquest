package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.wear.compose.material.Button
import com.example.fitquest.utils.TipsHelper
import com.example.fitquest.utils.TipsLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Button
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.Food
import com.example.fitquest.showLogFoodDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator

class MacroActivity : AppCompatActivity() {

    private var currentUserId: Int = -1
    private lateinit var db: AppDatabase

    private lateinit var breakfastContainer: LinearLayout
    private lateinit var snackContainer: LinearLayout
    private lateinit var lunchContainer: LinearLayout
    private lateinit var dinnerContainer: LinearLayout

    private val foodRepo by lazy { (application as FitQuestApp).foodRepository }


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

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onResume() {
        super.onResume()
        if (currentUserId > 0) {
            refreshTodayMeals()
            refreshTodayTotals()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro)   // ← set the layout first

        breakfastContainer = findViewById(R.id.breakfastContainer)
        snackContainer     = findViewById(R.id.snackContainer)
        lunchContainer     = findViewById(R.id.lunchContainer)
        dinnerContainer    = findViewById(R.id.dinnerContainer)

        val searchBtn: ImageButton = findViewById(R.id.btn_buy_food) // ← now it's non-null
        searchBtn.isEnabled = false

        db = AppDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            currentUserId = DataStoreManager.getUserId(this@MacroActivity).first()
            if (currentUserId > 0) {
                ensureUserExists(currentUserId)
                searchBtn.isEnabled = true
                refreshTodayMeals()
                refreshTodayTotals()
            } else {
                // show message or navigate to sign-in
            }
        }

        searchBtn.setOnClickListener {
            val repo = (application as FitQuestApp).foodRepository
            FoodSearchBottomSheet(repo) { item ->
                showLogFoodDialog(
                    repo = repo,
                    userId = currentUserId,
                    fdcId = item.fdcId,
                    defaultAmount = 100.0,
                    defaultUnit = com.example.fitquest.database.MeasurementType.GRAM,
                    defaultMeal = "Lunch"
                ) {
                    lifecycleScope.launch {
                        refreshTodayMeals()      // redraw plates
                        refreshTodayTotals()     // update numbers + progress
                    }
                }
            }.show(supportFragmentManager, "food_search")
        }

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

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

        // ✅ Get extras (coming from Workout/Dashboard)
        val rawGoal = intent.getStringExtra("GOAL") ?: "any"
        val userGoal = TipsHelper.mapGoalToCsv(rawGoal) // normalize to match CSV ("weight_loss", "muscle_gain", "endurance")
        val rawSplit = intent.getStringExtra("SPLIT") ?: "any"
        val splitKey = TipsHelper.mapSplitToCsv(rawSplit)
        val userCondition = intent.getStringExtra("HEALTH_CONDITION") ?: "any"
        val dietType = intent.getStringExtra("DIET_TYPE") ?: ""

        Log.d("MacroDebug", "Raw Goal: $rawGoal | Mapped Goal: $userGoal | Split: $splitKey | Condition: $userCondition")

/*      // ✅ Load meals filtered by user’s goal + diet type
        val meals = loadMealsFromCSV(this).filter {
            (rawGoal == "any" || it.goal.equals(userGoal, ignoreCase = true)) &&
                    (dietType.isEmpty() || it.dietType.equals(dietType, ignoreCase = true))
        }

        fun pickRandomMeals(list: List<MealItem>): List<MealItem> {
            if (list.isEmpty()) return emptyList()
            val count = (4..7).random().coerceAtMost(list.size)
            return list.shuffled().take(count)
        }

        pickRandomMeals(meals.filter { it.mealType.equals("Breakfast", true) })
            .forEach { addMealToContainer(it, breakfastContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Snack", true) })
            .forEach { addMealToContainer(it, snackContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Lunch", true) })
            .forEach { addMealToContainer(it, lunchContainer) }

        pickRandomMeals(meals.filter { it.mealType.equals("Dinner", true) })
            .forEach { addMealToContainer(it, dinnerContainer) }*/

        // Load tips and debug
        val tips = TipsLoader.loadTips(this)
        Log.d("MacroDebug", "Loaded tips count=${tips.size}")
        tips.take(10).forEach { Log.d("MacroDump", "id=${it.id} cat='${it.category}' goal='${it.goal}' split='${it.split}' cond='${it.condition}' tip='${it.tip}'") }

// get macro tips using the helper (with fallback)
        val macroTips = TipsHelper.getMacroTips(tips, userGoal, userCondition)
        Log.d("MacroDebug", "Filtered macro tips count=${macroTips.size} for mappedGoal='$userGoal' condition='$userCondition'")

        val macroTipText = findViewById<TextView>(R.id.macroTip)
        if (macroTips.isNotEmpty()) {
            val tipOfMacro = macroTips.random()
            macroTipText.text = tipOfMacro.tip
            Log.d("MacroDebug", "Showing macro tip: ${tipOfMacro.tip}")
        } else {
            macroTipText.text = "No macro tips available for your plan yet."
            Log.d("MacroDebug", "No macro tips found for Goal='$userGoal', Condition='$userCondition'")
        }
        setupNavigationBar()
    }

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_workout).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, WorkoutActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_shop).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ShopActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_dashboard).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DashboardActivity::class.java)); overridePendingTransition(0, 0)
        }
    }

    // Adds a meal row into the container
    private fun addMealToContainer(meal: MealItem, container: LinearLayout) {
        val mealView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
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

        mealView.setOnLongClickListener {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle(meal.mealName)
            builder.setMessage("What do you want to do?")
            builder.setPositiveButton("Delete") { dialog, _ ->
                container.removeView(mealView)
                dialog.dismiss()
            }
            builder.setNegativeButton("Edit") { dialog, _ -> dialog.dismiss() }
            builder.setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            builder.show()
            true
        }

        container.addView(mealView, params)
    }

    private fun refreshTodayTotals() {
        if (currentUserId <= 0) return

        lifecycleScope.launch(Dispatchers.IO) {
            val totals = foodRepo.getTodayTotals(currentUserId)  // DayTotals from repo
            withContext(Dispatchers.Main) {
                // 1) Show numbers
                findViewById<TextView>(R.id.calories_total).text = totals.calories.roundToInt().toString()
                findViewById<TextView>(R.id.protein_total).text  = totals.protein.roundToInt().toString()
                findViewById<TextView>(R.id.carbs_total).text    = totals.carbohydrate.roundToInt().toString()
                findViewById<TextView>(R.id.fat_total).text      = totals.fat.roundToInt().toString()

                // 2) Map to progress bars (0..100)
                val goalCalories = 3010.0
                val goalProtein  = 151.0   // g
                val goalCarbs    = 376.0   // g
                val goalFat      = 100.0    // g

                fun pct(done: Double, goal: Double) =
                    if (goal <= 0.0) 0 else ((done / goal) * 100.0).roundToInt().coerceIn(0, 100)

                findViewById<ProgressBar>(R.id.calories_progress).apply{ max = 100; progress = pct(totals.calories,goalCalories) }
                findViewById<ProgressBar>(R.id.protein_progress).apply{ max = 100; progress = pct(totals.protein,goalProtein) }
                findViewById<ProgressBar>(R.id.carbs_progress).apply{ max = 100; progress = pct(totals.carbohydrate,goalCarbs) }
                findViewById<ProgressBar>(R.id.fat_progress).apply{ max = 100; progress = pct(totals.fat,goalFat) }
            }
        }
    }

    private fun refreshTodayMeals() {
        if (currentUserId <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = foodRepo.getTodayLogs(currentUserId)
            withContext(Dispatchers.Main) {
                breakfastContainer.removeAllViews()
                snackContainer.removeAllViews()
                lunchContainer.removeAllViews()
                dinnerContainer.removeAllViews()

                val byMeal = logs.groupBy { it.log.mealType } // "BREAKFAST", "SNACK", "LUNCH", "DINNER"
                renderMealPlate(breakfastContainer, byMeal["BREAKFAST"].orEmpty())
                renderMealPlate(snackContainer,     byMeal["SNACK"].orEmpty())
                renderMealPlate(lunchContainer,     byMeal["LUNCH"].orEmpty())
                renderMealPlate(dinnerContainer,    byMeal["DINNER"].orEmpty())


                // Example: update header totals if you have TextViews
                // findViewById<TextView>(R.id.tvTotalCalories).text = totals.calories.roundToInt().toString()
                // findViewById<TextView>(R.id.tvTotalProtein).text  = totals.protein.roundToInt().toString()
                // findViewById<TextView>(R.id.tvTotalCarbs).text    = totals.carbohydrate.roundToInt().toString()
                // findViewById<TextView>(R.id.tvTotalFat).text      = totals.fat.roundToInt().toString()
            }
        }
        refreshTodayTotals()
    }

    private suspend fun ensureUserExists(userId: Int) {
        withContext(Dispatchers.IO) {
            val userDao = db.userDAO()
            val existing = userDao.getUserById(userId)
            if (existing == null) {
                // Insert a minimal user record or redirect to a real sign-up flow
                userDao.insert(
                    User(
                        userId = userId,
                        firstName = "User",
                        lastName = "",
                        birthday = "",
                        age = 0,
                        sex = "",
                        username = "user$userId",
                        email = "",
                        password = ""
                    )
                )
            }
        }
    }

    private fun renderMealPlate(container: LinearLayout, rows: List<com.example.fitquest.database.FoodLogRow>) {
        for (row in rows) {
            val v = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)

                // ripple on press
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val typed = obtainStyledAttributes(attrs)
                foreground = typed.getDrawable(0)
                typed.recycle()
            }
            val name = TextView(this).apply {
                text = (row.foodName ?: "Food #${row.log.foodId}") + " • ${row.log.grams.toInt()}g"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val kcal = TextView(this).apply {
                text = "${row.log.calories.toInt()} kcal"
                textSize = 14f
            }
            v.addView(name)
            v.addView(kcal)

            //  Long press
            v.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showFoodItemActionsDialog(row)
                true
            }

            container.addView(v)
        }
    }

    private fun showFoodItemActionsDialog(row: com.example.fitquest.database.FoodLogRow) {
        val title   = row.foodName ?: "Food #${row.log.foodId}"
        val message = "${row.log.mealType} • ${row.log.grams.roundToInt()} g • ${row.log.calories.roundToInt()} kcal"

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            ContextThemeWrapper(
                this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            )
        )
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Edit serving") { _, _ -> showEditServingDialog(row) }
            .setNeutralButton("Delete") { _, _ -> confirmDeleteRow(row) }
            .setNegativeButton("Cancel", null)

        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
        dlg.show()
    }

    private fun confirmDeleteRow(row: com.example.fitquest.database.FoodLogRow) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove item?")
            .setMessage("This will remove it from ${row.log.mealType}.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    foodRepo.deleteLog(row.log.logId)    // use your primary key
                    withContext(Dispatchers.Main) {
                        refreshTodayMeals()
                        refreshTodayTotals()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditServingDialog(row: com.example.fitquest.database.FoodLogRow) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(row.log.grams.toString())
            setSelection(text.length)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Edit serving (grams)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newGrams = input.text.toString().toDoubleOrNull()
                if (newGrams != null && newGrams > 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val updated = foodRepo.updateLogServing(row.log.logId, newGrams)
                        withContext(Dispatchers.Main) {
                            if (updated == 0) {
                                android.widget.Toast.makeText(this@MacroActivity, "Update failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            refreshTodayMeals()
                            refreshTodayTotals()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }




//    private fun loadMealsFromCSV(context: Context): List<MealItem> {
//        val meals = mutableListOf<MealItem>()
//        try {
//            val inputStream = context.assets.open("meal_dataset.csv")
//            val reader = BufferedReader(InputStreamReader(inputStream))
//            reader.readLine() // skip header
//            var line: String?
//            while (reader.readLine().also { line = it } != null) {
//                val tokens = line!!.split(",")
//                if (tokens.size >= 9) {
//                    val meal = MealItem(
//                        mealId = tokens[0].toInt(),
//                        mealName = tokens[1],
//                        mealType = tokens[2],
//                        calories = tokens[3].toInt(),
//                        protein = tokens[4].toInt(),
//                        carbs = tokens[5].toInt(),
//                        fat = tokens[6].toInt(),
//                        goal = tokens[7],
//                        dietType = tokens[8]
//                    )
//                    meals.add(meal)
//                }
//            }
//            reader.close()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//        return meals
//    }
}

