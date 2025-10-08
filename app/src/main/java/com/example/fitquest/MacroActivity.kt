package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.fitquest.utils.TipsHelper
import com.example.fitquest.utils.TipsLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Button
import android.widget.CheckBox
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.gridlayout.widget.GridLayout
import com.example.fitquest.database.MacroPlan
import com.example.fitquest.repository.FitquestRepository
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.content.res.AppCompatResources
import android.widget.EditText
import com.example.fitquest.R
class MacroActivity : AppCompatActivity() {

    private var currentUserId: Int = -1
    private lateinit var db: AppDatabase

    private lateinit var breakfastContainer: LinearLayout
    private lateinit var snackContainer: LinearLayout
    private lateinit var lunchContainer: LinearLayout
    private lateinit var dinnerContainer: LinearLayout
    private lateinit var repository: FitquestRepository
    private var macroPlan: MacroPlan? = null

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
        macroPlan = null
        if (currentUserId > 0) {
            refreshTodayMeals()
            refreshTodayTotals()
        }

        // Catch up remaining days if the device was off for days
        lifecycleScope.launch(Dispatchers.IO) {
            val zone = java.time.ZoneId.of("Asia/Manila")
            val userId = currentUserId
            if (userId <= 0) return@launch

            val todayKey = (java.time.LocalDate.now(zone).let { it.year*10000 + it.monthValue*100 + it.dayOfMonth })
            val yesterdayKey = (java.time.LocalDate.now(zone).minusDays(1).let { it.year*10000 + it.monthValue*100 + it.dayOfMonth })

            val existing = db.macroDiaryDao().get(userId, yesterdayKey)
            if (existing == null) {
                val totals = db.foodLogDao().totalsForDay(userId, yesterdayKey)
                val plan = db.macroPlanDao().getLatestForUser(userId)
                db.macroDiaryDao().upsert(
                    com.example.fitquest.database.MacroDiary(
                        userId = userId,
                        dayKey = yesterdayKey,
                        calories = totals.calories.roundToInt(),
                        protein = totals.protein.roundToInt(),
                        carbs = totals.carbohydrate.roundToInt(),
                        fat = totals.fat.roundToInt(),
                        planCalories = plan?.calories ?: 0,
                        planProtein = plan?.protein ?: 0,
                        planCarbs = plan?.carbs ?: 0,
                        planFat = plan?.fat ?: 0
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Initialize repo
        repository = FitquestRepository(this)

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

                macroPlan = withContext(Dispatchers.IO) {
                    repository.getMacroPlan(currentUserId)
                }

                refreshTodayMeals()
                refreshTodayTotals()
            } else {
                // Navigate to sign-in if user is not found
                Toast.makeText(this@MacroActivity, "User not found, navigating back to sign-in page.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@MacroActivity, LoginActivity::class.java))
                finish()
            }


        }

        findViewById<ImageButton>(R.id.btn_diary)?.setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, FoodHistory::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showMacroSettingsDialog()
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showMacroSettingsDialog()
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

        // --- Tips inputs for MACRO category ---
        val goalRaw = intent.getStringExtra("GOAL")               // may be null; helper maps null → "any"
        val activityRaw = intent.getStringExtra("ACTIVITY_LEVEL") // may be null; helper maps null → "any"

// For log clarity, also show normalized forms:
        val goalNorm = TipsHelper.mapGoalToCsv(goalRaw)
        val actNorm  = TipsHelper.mapActivityToCsv(activityRaw)
        Log.d("MacroDebug", "Goal raw='$goalRaw' norm='$goalNorm' | Activity raw='$activityRaw' norm='$actNorm'")

// Load tips and filter
        val tips = TipsLoader.loadTips(this)
        Log.d("MacroDebug", "Loaded tips count=${tips.size}")

        val macroTips = TipsHelper.getMacroTips(tips, goalRaw, activityRaw)
        Log.d("MacroDebug", "Filtered macro tips count=${macroTips.size} goal='$goalNorm' activity='$actNorm'")

// Show one macro tip (or a friendly fallback)
        findViewById<TextView>(R.id.macroTip).text =
            macroTips.randomOrNull()?.tip ?: "No macro tips available for your plan yet."

        setupNavigationBar()
    }


    private fun displayMacroTip() {
        val tips = TipsLoader.loadTips(this)
        val goalRaw = intent.getStringExtra("GOAL")
        val actRaw  = intent.getStringExtra("ACTIVITY_LEVEL")

        val macro = TipsHelper.getMacroTips(tips, goalRaw, actRaw)
        val fallback = "No macro tips available."
        findViewById<TextView>(R.id.macroTip).text = (macro.randomOrNull()?.tip ?: fallback)
    }


    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundResource(R.drawable.container_general)
            isClickable = true
            isFocusable = true

            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typed = obtainStyledAttributes(attrs)
            foreground = typed.getDrawable(0)
            typed.recycle()
        }

        val nameText = TextView(this).apply {
            text = meal.mealName
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val calorieText = TextView(this).apply {
            text = "${meal.calories} kcal"
            textSize = 15f
        }

        mealView.addView(nameText)
        mealView.addView(calorieText)

        mealView.setOnLongClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle(meal.mealName)
                .setMessage("What do you want to do?")
                .setPositiveButton("Delete") { d, _ -> container.removeView(mealView); d.dismiss() }
                .setNegativeButton("Edit") { d, _ -> d.dismiss() }
                .setNeutralButton("Cancel") { d, _ -> d.dismiss() }
                .show()
            true
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }

        container.addView(mealView, params)
    }


    private fun refreshTodayTotals() {
        if (currentUserId <= 0) return


        lifecycleScope.launch(Dispatchers.IO) {
            val totals = foodRepo.getTodayTotals(currentUserId)
            val plan = macroPlan ?: db.macroPlanDao().getLatestForUser(currentUserId)

            withContext(Dispatchers.Main) {
                // Numbers
                findViewById<TextView>(R.id.protein_total).text = totals.protein.roundToInt().toString()
                findViewById<TextView>(R.id.carbs_total).text   = totals.carbohydrate.roundToInt().toString()
                findViewById<TextView>(R.id.fat_total).text     = totals.fat.roundToInt().toString()
                findViewById<TextView>(R.id.calories_total).text= totals.calories.roundToInt().toString()

                // Goals (limits)
                val goalCalories = (plan?.calories ?: 3010).toInt()
                val goalProtein  = (plan?.protein  ?: 151 ).toInt()
                val goalCarbs    = (plan?.carbs    ?: 376 ).toInt()
                val goalFat      = (plan?.fat      ?: 100 ).toInt()

                // Done values (Int)
                val doneCalories = totals.calories.roundToInt()
                val doneProtein  = totals.protein.roundToInt()
                val doneCarbs    = totals.carbohydrate.roundToInt()
                val doneFat      = totals.fat.roundToInt()

                // Set values for each label
                val proteinValue = findViewById<TextView>(R.id.protein_total)
                val carbsValue = findViewById<TextView>(R.id.carbs_total)
                val fatsValue = findViewById<TextView>(R.id.fat_total)
                val caloriesValue = findViewById<TextView>(R.id.calories_total)

                proteinValue.text = calculateRemaining(goalProtein,doneProtein)
                carbsValue.text = calculateRemaining(goalCarbs, doneCarbs)
                fatsValue.text = calculateRemaining(goalFat, doneFat)
                caloriesValue.text = calculateRemaining(goalCalories, doneCalories)


                val ok   = ContextCompat.getColor(this@MacroActivity, R.color.progress_ok)
                val over = ContextCompat.getColor(this@MacroActivity, R.color.progress_over)

                // --- Calories
                val caloriesPI = findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(
                    R.id.calories_progress
                )

                caloriesPI.max = goalCalories.coerceAtLeast(1) // avoid 0 max
                caloriesPI.setProgressCompat(
                    doneCalories.coerceIn(0, caloriesPI.max),
                    /*animated=*/true
                )
                caloriesPI.setIndicatorColor(if (doneCalories > goalCalories) over else ok)

                // --- Protein/Carbs/Fat
                fun setLimited(pbId: Int, done: Int, goal: Int) {
                    val pb = findViewById<ProgressBar>(pbId)
                    val limit = goal.coerceAtLeast(1)
                    pb.max = limit
                    pb.progress = done.coerceIn(0, limit)
                    val tint = if (done > goal) over else ok
                    pb.progressTintList = ColorStateList.valueOf(tint)
                }

                setLimited(R.id.protein_progress, doneProtein, goalProtein)
                setLimited(R.id.carbs_progress,   doneCarbs,   goalCarbs)
                setLimited(R.id.fat_progress,     doneFat,     goalFat)
            }
        }
    }

    private fun upsertTodayDiarySnapshot() {
        if (currentUserId <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            val zone = java.time.ZoneId.of("Asia/Manila")
            val todayKey = java.time.LocalDate.now(zone).let { it.year*10000 + it.monthValue*100 + it.dayOfMonth }

            val totals = foodRepo.getTodayTotals(currentUserId)
            val plan = macroPlan ?: db.macroPlanDao().getLatestForUser(currentUserId)

            db.macroDiaryDao().upsert(
                com.example.fitquest.database.MacroDiary(
                    userId = currentUserId,
                    dayKey = todayKey,
                    calories = totals.calories.roundToInt(),
                    protein  = totals.protein.roundToInt(),
                    carbs    = totals.carbohydrate.roundToInt(),
                    fat      = totals.fat.roundToInt(),
                    planCalories = plan?.calories ?: 0,
                    planProtein  = plan?.protein  ?: 0,
                    planCarbs    = plan?.carbs    ?: 0,
                    planFat      = plan?.fat      ?: 0
                )
            )
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
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.container_general)   // 👈 your image
                isClickable = true
                isFocusable = true

                // ripple on press (keeps your image as background)
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

            // Long press actions
            v.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showFoodItemActionsDialog(row)
                true
            }

            // margin between items
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            container.addView(v, lp)
        }
    }


    private fun showFoodItemActionsDialog(row: com.example.fitquest.database.FoodLogRow) {
        val view = layoutInflater.inflate(R.layout.dialog_food_item_actions, null)

        view.findViewById<TextView>(R.id.tv_title).text =
            row.foodName ?: "Food #${row.log.foodId}"
        view.findViewById<TextView>(R.id.tv_message).text =
            "${row.log.mealType} • ${row.log.grams.roundToInt()} g • ${row.log.calories.roundToInt()} kcal"

        // Optional: swap to button_edit.png if you have it
        val editRes = resources.getIdentifier("button_edit", "drawable", packageName)
        if (editRes != 0) {
            view.findViewById<ImageButton>(R.id.btn_edit_img).setBackgroundResource(editRes)
        }

        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            view.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener { dlg.dismiss() }
            view.findViewById<ImageButton>(R.id.btn_edit_img).setOnClickListener {
                dlg.dismiss()
                showEditServingDialog(row)
            }
            view.findViewById<ImageButton>(R.id.btn_delete_img).setOnClickListener {
                dlg.dismiss()
                confirmDeleteRow(row)
            }
        }

        dlg.show()
    }




    private fun confirmDeleteRow(row: com.example.fitquest.database.FoodLogRow) {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)

        // Set black title + message text
        view.findViewById<TextView>(R.id.tv_title).text = "Remove item?"
        view.findViewById<TextView>(R.id.tv_message).text =
            "This will remove it from ${row.log.mealType}."

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            // Make the dialog window itself transparent; we use our PNG as the content background
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Remove default insets/padding the Builder adds around your custom view
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            // Wire the image buttons
            view.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener {
                dlg.dismiss()
            }
            view.findViewById<ImageButton>(R.id.btn_delete_img).setOnClickListener {
                // Do the delete
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    foodRepo.deleteLog(row.log.logId)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        refreshTodayMeals()
                        refreshTodayTotals()
                        dlg.dismiss()
                    }
                }
            }
        }

        dlg.show()
    }

    private fun showEditServingDialog(row: com.example.fitquest.database.FoodLogRow) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_serving, null)
        val titleTv = view.findViewById<TextView>(R.id.tv_title)
        val et = view.findViewById<EditText>(R.id.et_grams)

        titleTv.text = "Edit serving (grams)"
        et.setText(row.log.grams.toString())
        et.setSelection(et.text?.length ?: 0)

        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            view.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener { dlg.dismiss() }
            view.findViewById<ImageButton>(R.id.btn_save_img).setOnClickListener {
                val newGrams = et.text.toString().toDoubleOrNull()
                if (newGrams == null || newGrams <= 0) {
                    android.widget.Toast.makeText(this, "Enter a valid grams value", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val updated = foodRepo.updateLogServing(row.log.logId, newGrams)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (updated == 0) {
                            android.widget.Toast.makeText(this@MacroActivity, "Update failed", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            refreshTodayMeals()
                            refreshTodayTotals()
                        }
                        dlg.dismiss()
                    }
                }
            }

            // Optional: focus & show keyboard
            et.requestFocus()
            dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        dlg.show()
    }


    private fun showMacroSettingsDialog() {
        if (currentUserId <= 0) {
            Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // Log yesterday's food entries
            upsertTodayDiarySnapshot()

            val plan = withContext(Dispatchers.IO) {
                macroPlan ?: db.macroPlanDao().getLatestForUser(currentUserId)
            }

            // Inflate dialog view
            val view = layoutInflater.inflate(R.layout.dialog_macro_settings, null)
            val range = view.findViewById<RangeSlider>(R.id.rs_macro)
            val tvSummary = view.findViewById<TextView>(R.id.tv_summary)
            val tvCaloriesHint = view.findViewById<TextView>(R.id.tv_calories_hint)
            val btnCancel = view.findViewById<ImageButton>(R.id.btn_cancel)
            val btnSave = view.findViewById<ImageButton>(R.id.btn_save)

            val goalCalories = (plan?.calories ?: 2000.0).toInt().coerceAtLeast(1)
            val curP = (plan?.protein ?: 150.0).toInt()
            val curF = (plan?.fat     ?: 60.0 ).toInt()
            val curC = (plan?.carbs   ?: 250.0).toInt()

            // derive starting % from grams + kcal
            var pPct = ((curP * 4f / goalCalories) * 100f).roundToInt().coerceIn(0, 100)
            var fPct = ((curF * 9f / goalCalories) * 100f).roundToInt().coerceIn(0, 100)
            var cPct = 100 - pPct - fPct
            if (cPct < 0) { fPct = (fPct + cPct).coerceAtLeast(0); cPct = 0 }

            var left = pPct.coerceIn(0, 100)
            var right = (pPct + fPct).coerceIn(left, 100)
            range.values = mutableListOf(left.toFloat(), right.toFloat())

            tvCaloriesHint.text = "Calories: $goalCalories kcal"

            fun updateSummary() {
                left = range.values[0].toInt()
                right = range.values[1].toInt()

                val proteinPct = left
                val fatPct = (right - left).coerceAtLeast(0)
                var carbsPct = (100 - right).coerceAtLeast(0)
                val adjust = 100 - (proteinPct + fatPct + carbsPct) // rounding fix
                carbsPct += adjust

                val proteinG = ((goalCalories * proteinPct) / 100f / 4f).roundToInt()
                val fatG     = ((goalCalories * fatPct)     / 100f / 9f).roundToInt()
                val carbsG   = ((goalCalories * carbsPct)   / 100f / 4f).roundToInt()

                tvSummary.text = "Protein $proteinPct% (${proteinG}g) • " +
                        "Fat $fatPct% (${fatG}g) • " +
                        "Carbs $carbsPct% (${carbsG}g)"

                btnSave.isEnabled = (proteinPct + fatPct + carbsPct == 100)
                btnSave.alpha = if (btnSave.isEnabled) 1f else 0.5f
                btnSave.setTag(R.id.btn_save, Triple(proteinG, fatG, carbsG))
            }

            range.addOnChangeListener { _, _, _ -> updateSummary() }
            updateSummary()

            val dialog = MaterialAlertDialogBuilder(
                ContextThemeWrapper(
                    this@MacroActivity,
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                )
            ).setView(view).create()


            dialog.setOnShowListener {
                // Make the dialog window fully transparent
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                // The builder wraps your view in a FrameLayout with its own bg + paddings.
                (view.parent as? ViewGroup)?.apply {
                    setBackgroundColor(Color.TRANSPARENT)   // remove gray surface
                    setPadding(0, 0, 0, 0)                  // remove default insets
                }
            }

            btnCancel.setOnClickListener { it.startAnimation(pressAnim); dialog.dismiss() }
            btnSave.setOnClickListener {
                it.startAnimation(pressAnim)
                val (proteinG, fatG, carbsG) =
                    (btnSave.getTag(R.id.btn_save) as? Triple<Int, Int, Int>) ?: Triple(0, 0, 0)

                val updated = (macroPlan ?: plan)?.copy(
                    protein = proteinG,
                    fat = fatG,
                    carbs = carbsG,
                    calories = goalCalories
                ) ?: MacroPlan(
                    userId = currentUserId,
                    calories = goalCalories,
                    protein = proteinG,
                    carbs = carbsG,
                    fat = fatG
                )

                macroPlan = updated

                // persist off the main thread
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        db.macroPlanDao().upsert(updated) // replace with your DAO method if different
                    } catch (t: Throwable) {
                        Log.e("Macro", "Failed saving macro plan", t)
                    }
                    withContext(Dispatchers.Main) {
                        refreshTodayTotals()
                        dialog.dismiss()
                        Toast.makeText(this@MacroActivity, "Macro plan saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialog.show()
        }
    }

    private fun calculateRemaining(goal: Int, done: Int): String {
        val remaining = goal - done
        if (remaining >= 0) {
            return "${remaining} left"
        }
        else {
            return "${-remaining} over"
        }
    }

}

