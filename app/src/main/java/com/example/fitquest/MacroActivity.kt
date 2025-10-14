package com.example.fitquest

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.data.repository.ApiPortion
import com.example.fitquest.data.repository.FoodRepository
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.MacroPlan
import com.example.fitquest.database.MeasurementType
import com.example.fitquest.database.User
import com.example.fitquest.databinding.DialogEditServingBinding
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.repository.FitquestRepository
import com.example.fitquest.utils.TipsHelper
import com.example.fitquest.utils.TipsLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MacroActivity : AppCompatActivity() {

    private var currentUserId: Int = -1
    private lateinit var db: AppDatabase

    private lateinit var breakfastContainer: LinearLayout
    private lateinit var snackContainer: LinearLayout
    private lateinit var lunchContainer: LinearLayout
    private lateinit var dinnerContainer: LinearLayout
    private lateinit var repository: FitquestRepository
    private var macroPlan: MacroPlan? = null
    private lateinit var capybaraView: ImageView

    private val foodRepo by lazy { (application as FitQuestApp).foodRepository }
    private val TAG = "MacroActivity"


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

            val todayKey = (java.time.LocalDate.now(zone).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth })
            val yesterdayKey = (java.time.LocalDate.now(zone).minusDays(1).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth })

            val existing = db.macroDiaryDao().get(userId, yesterdayKey)
            if (existing == null) {
                val totals = db.foodLogDao().totalsForDay(userId, yesterdayKey)
                val hasIntakeYesterday = (totals.calories > 0.0 || totals.protein > 0.0 || totals.carbohydrate > 0.0 || totals.fat > 0.0)
                if (hasIntakeYesterday) {
                    val plan = db.macroPlanDao().getLatestForUser(userId)
                    db.macroDiaryDao().upsert(
                        com.example.fitquest.database.MacroDiary(
                            userId = userId,
                            dayKey = yesterdayKey,
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
                // else: no intake yesterday → skip making a row
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capybara
        capybaraView = findViewById(R.id.iv_capybara)

        // Initialize repo
        repository = FitquestRepository(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro)

        // INIT FIRST so any early clicks won’t crash
        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        breakfastContainer = findViewById(R.id.breakfastContainer)
        snackContainer = findViewById(R.id.snackContainer)
        lunchContainer = findViewById(R.id.lunchContainer)
        dinnerContainer = findViewById(R.id.dinnerContainer)

        val searchBtn: ImageButton = findViewById(R.id.btn_buy_food)
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

        // (Removed duplicate listener) — single settings listener
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
                        refreshTodayMeals()
                        refreshTodayTotals()
                    }
                }
            }.show(supportFragmentManager, "food_search")
        }

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
        val goalRaw = intent.getStringExtra("GOAL")
        val activityRaw = intent.getStringExtra("ACTIVITY_LEVEL")

        val goalNorm = TipsHelper.mapGoalToCsv(goalRaw)
        val actNorm = TipsHelper.mapActivityToCsv(activityRaw)
        Log.d("MacroDebug", "Goal raw='$goalRaw' norm='$goalNorm' | Activity raw='$activityRaw' norm='$actNorm'")

        val tips = TipsLoader.loadTips(this)
        Log.d("MacroDebug", "Loaded tips count=${tips.size}")

        val macroTips = TipsHelper.getMacroTips(tips, goalRaw, activityRaw)
        Log.d("MacroDebug", "Filtered macro tips count=${macroTips.size} goal='$goalNorm' activity='$actNorm'")

        findViewById<TextView>(R.id.macroTip).text =
            macroTips.randomOrNull()?.tip ?: "No macro tips available for your plan yet."

        setupNavigationBar()
    }

    private fun displayMacroTip() {
        val tips = TipsLoader.loadTips(this)
        val goalRaw = intent.getStringExtra("GOAL")
        val actRaw = intent.getStringExtra("ACTIVITY_LEVEL")

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
                // Goals (limits)
                val goalCalories = (plan?.calories ?: 3010).toInt()
                val goalProtein = (plan?.protein ?: 151).toInt()
                val goalCarbs = (plan?.carbs ?: 376).toInt()
                val goalFat = (plan?.fat ?: 100).toInt()

                // Done values (Int)
                val doneCalories = totals.calories.roundToInt()
                val doneProtein = totals.protein.roundToInt()
                val doneCarbs = totals.carbohydrate.roundToInt()
                val doneFat = totals.fat.roundToInt()

                // Labels show remaining/over
                findViewById<TextView>(R.id.protein_total).text = calculateRemaining(goalProtein, doneProtein)
                findViewById<TextView>(R.id.carbs_total).text = calculateRemaining(goalCarbs, doneCarbs)
                findViewById<TextView>(R.id.fat_total).text = calculateRemaining(goalFat, doneFat)
                findViewById<TextView>(R.id.calories_total).text = calculateRemaining(goalCalories, doneCalories)

                val ok = ContextCompat.getColor(this@MacroActivity, R.color.progress_ok)
                val over = ContextCompat.getColor(this@MacroActivity, R.color.progress_over)

                // Calories (Material progress)
                val caloriesPI = findViewById<CircularProgressIndicator>(R.id.calories_progress)
                caloriesPI.max = goalCalories.coerceAtLeast(1)
                caloriesPI.setProgressCompat(doneCalories.coerceIn(0, caloriesPI.max), true)
                caloriesPI.setIndicatorColor(if (doneCalories > goalCalories) over else ok)

                // Protein/Carbs/Fat (classic ProgressBar)
                fun setLimited(pbId: Int, done: Int, goal: Int) {
                    val pb = findViewById<ProgressBar>(pbId)
                    val limit = goal.coerceAtLeast(1)
                    pb.max = limit
                    pb.progress = done.coerceIn(0, limit)
                    val tint = if (done > goal) over else ok
                    pb.progressTintList = ColorStateList.valueOf(tint)
                }

                setLimited(R.id.protein_progress, doneProtein, goalProtein)
                setLimited(R.id.carbs_progress, doneCarbs, goalCarbs)
                setLimited(R.id.fat_progress, doneFat, goalFat)

                updateCapybaraMood(
                    doneCalories = doneCalories,
                    doneProtein  = doneProtein,
                    doneCarbs    = doneCarbs,
                    doneFat      = doneFat,
                    goalCalories = goalCalories,
                    goalProtein  = goalProtein,
                    goalCarbs    = goalCarbs,
                    goalFat      = goalFat
                )

            }
        }
    }

    private fun upsertTodayDiarySnapshot() {
        if (currentUserId <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            val zone = java.time.ZoneId.of("Asia/Manila")
            val todayKey = java.time.LocalDate.now(zone).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }

            val totals = foodRepo.getTodayTotals(currentUserId)
            val plan = macroPlan ?: db.macroPlanDao().getLatestForUser(currentUserId)

            db.macroDiaryDao().upsert(
                com.example.fitquest.database.MacroDiary(
                    userId = currentUserId,
                    dayKey = todayKey,
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
                renderMealPlate(snackContainer, byMeal["SNACK"].orEmpty())
                renderMealPlate(lunchContainer, byMeal["LUNCH"].orEmpty())
                renderMealPlate(dinnerContainer, byMeal["DINNER"].orEmpty())
            }
        }
        refreshTodayTotals()
    }

    private suspend fun ensureUserExists(userId: Int) {
        withContext(Dispatchers.IO) {
            val userDao = db.userDAO()
            val existing = userDao.getUserById(userId)
            if (existing == null) {
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
                setBackgroundResource(R.drawable.container_general)
                isClickable = true
                isFocusable = true

                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val typed = obtainStyledAttributes(attrs)
                foreground = typed.getDrawable(0)
                typed.recycle()
            }

            val qtyText = when {
                row.log.inputQuantity != null && !row.log.inputLabel.isNullOrBlank() ->
                    "${formatQty(row.log.inputQuantity!!)}" + " servings of " + "${row.log.inputLabel}"
                row.log.inputQuantity != null && row.log.inputUnit != null ->
                    "${formatQty(row.log.inputQuantity!!)} ${row.log.inputUnit.displayName}"
                else ->
                    "${row.log.grams.toInt()} g"
            }

            val name = TextView(this).apply {
                text = (row.foodName ?: "Food #${row.log.foodId}") + " • " + qtyText
//                text = (row.foodName ?: "Food #${row.log.foodId}") + " • ${row.log.grams.toInt()}g"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val kcal = TextView(this).apply {
                text = "${row.log.calories.toInt()} kcal"
                textSize = 14f
            }

            v.addView(name)
            v.addView(kcal)

            v.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showFoodItemActionsDialog(row)
                true
            }

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

        val previewQty = when {
            row.log.inputQuantity != null && !row.log.inputLabel.isNullOrBlank() ->
                "${formatQty(row.log.inputQuantity!!)} ${row.log.inputLabel}"
            row.log.inputQuantity != null && row.log.inputUnit != null ->
                "${formatQty(row.log.inputQuantity!!)} ${row.log.inputUnit.displayName}"
            else ->
                "${row.log.grams.roundToInt()} g"
        }
        view.findViewById<TextView>(R.id.tv_message).text =
            "${row.log.mealType} • $previewQty • ${row.log.calories.roundToInt()} kcal"


        view.findViewById<TextView>(R.id.tv_message).text =
            "${row.log.mealType} • $previewQty • ${row.log.calories.roundToInt()} kcal"
//            "${row.log.mealType} • ${row.log.grams.roundToInt()} g • ${row.log.calories.roundToInt()} kcal"

        // Optional skinning
        val editRes = resources.getIdentifier("button_edit", "drawable", packageName)
        if (editRes != 0) {
            view.findViewById<ImageButton>(R.id.btn_edit_img).setBackgroundResource(editRes)
        }

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            view.findViewById<ImageButton>(R.id.btn_cancel_img)?.setOnClickListener {
                dlg.dismiss()
            }



            view.findViewById<ImageButton>(R.id.btn_edit_img)?.setOnClickListener {
                dlg.dismiss()
                lifecycleScope.launch {
                    val fdcId: Long? = foodRepo.fdcIdForFoodId(row.log.foodId)

                    showEditServingDialog(
                        repo = foodRepo,
                        userId = currentUserId,
                        logId = row.log.logId,                                // ← NEW
                        foodId = fdcId,
                        defaultAmount = (row.log.inputQuantity ?: row.log.grams).toDouble(),
                        defaultUnit = (row.log.inputUnit ?: MeasurementType.GRAM),
                        defaultMeal = row.log.mealType,
                        defaultApiLabel = row.log.inputLabel
                    )
                }
            }
            view.findViewById<ImageButton>(R.id.btn_delete_img)?.setOnClickListener {
                dlg.dismiss()
                confirmDeleteRow(row)
            }
        }

        dlg.show()
    }


    private fun confirmDeleteRow(row: com.example.fitquest.database.FoodLogRow) {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)

        view.findViewById<TextView>(R.id.tv_title).text = "Remove item?"
        view.findViewById<TextView>(R.id.tv_message).text =
            "This will remove it from ${row.log.mealType}."

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            view.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener {
                dlg.dismiss()
            }
            view.findViewById<ImageButton>(R.id.btn_delete_img).setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    foodRepo.deleteLog(row.log.logId)
                    withContext(Dispatchers.Main) {
                        refreshTodayMeals()
                        refreshTodayTotals()
                        dlg.dismiss()
                    }
                }
            }
        }

        dlg.show()
    }

    private fun showEditServingDialog(
        repo: FoodRepository,
        userId: Int,
        logId: Long,
        foodId: Long?,
        defaultAmount: Double = 100.0,
        defaultUnit: MeasurementType = MeasurementType.GRAM,
        defaultMeal: String = "Lunch",
        defaultApiLabel: String? = null,
        onLogged: (logId: Long) -> Unit = {}
    ) {
        // Use a Material base theme to avoid InflateException for M3 widgets
        val materialCtx = ContextThemeWrapper(
            this,
            com.google.android.material.R.style.Theme_Material3_DayNight
        )

        val inflater = LayoutInflater.from(materialCtx)
        val binding = DialogEditServingBinding.inflate(inflater)
        val view = binding.root

        // Pre-fill amount/unit if you have fields for them in the layout
        binding.etAmount?.setText(
            if (defaultAmount % 1.0 == 0.0) defaultAmount.toInt().toString() else defaultAmount.toString()
        )

        var apiPortions: List<ApiPortion> = emptyList()

        // Units: load only what the API actually provides for this food
        binding.actvUnit.isEnabled = false
        binding.actvUnit.setText("Loading units…", false)

        // Load available units for the chosen FDC food (or fallback to grams)
        lifecycleScope.launch {
            try {
                apiPortions = if (foodId != null) {
                    repo.availableApiPortions(foodId)          // requires non-null FDC id
                } else {
                    listOf(ApiPortion("grams (g)", 1.0))       // no FDC mapping → grams only
                }
//                val labels = apiPortions.map { it.label }
//                val adapter = ArrayAdapter(materialCtx, android.R.layout.simple_list_item_1, labels)
//                binding.actvUnit.setAdapter(adapter)

//                val defaultLabel = labels.firstOrNull().orEmpty()
//                if (defaultLabel.isNotEmpty()) binding.actvUnit.setText(defaultLabel, false)

                val labels = apiPortions.map { it.label }
                binding.actvUnit.setAdapter(ArrayAdapter(materialCtx, android.R.layout.simple_list_item_1, labels))

                val preferred = defaultApiLabel?.let { want ->
                    labels.firstOrNull { it.equals(want, ignoreCase = true) }
                } ?: labels.firstOrNull()

                if (!preferred.isNullOrEmpty()) binding.actvUnit.setText(preferred, false)


                // Make sure dropdown shows ALL items (not just those matching current text)
                fun showAll() {
                    (binding.actvUnit.adapter as? ArrayAdapter<*>)?.filter?.filter(null) // clear constraint
                    binding.actvUnit.showDropDown()
                }

                binding.actvUnit.setOnClickListener { showAll() }
                binding.actvUnit.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showAll() }

//                binding.actvUnit.inputType = android.text.InputType.TYPE_NULL
//                binding.actvUnit.keyListener = null
//                binding.actvUnit.isCursorVisible = false
//                binding.actvUnit.setOnClickListener { binding.actvUnit.showDropDown() }
//                binding.actvUnit.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvUnit.showDropDown() }

//                val defaultLabel = labels.firstOrNull().orEmpty()
//                if (defaultLabel.isNotEmpty()) binding.actvUnit.setText(defaultLabel, false)

                binding.tilUnit.error = if (labels.isEmpty()) "No available units for this food." else null
                binding.actvUnit.isEnabled = labels.isNotEmpty()
            } catch (t: Throwable) {
                Log.e("DialogEditServing", "Failed to load units", t)
                binding.tilUnit.error = "Failed to load units."
                binding.actvUnit.isEnabled = false
            }
        }

        val dlg = MaterialAlertDialogBuilder(materialCtx)
            .setView(view)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            (view.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

            view.findViewById<ImageButton>(R.id.btn_cancel_img)?.setOnClickListener {
                dlg.dismiss()
            }

            view.findViewById<ImageButton>(R.id.btn_save_img)?.setOnClickListener {
                val amount = binding.etAmount?.text?.toString()?.toDoubleOrNull() ?: defaultAmount
                val selectedLabel = binding.actvUnit.text?.toString()?.trim().orEmpty()
                val portion = apiPortions.firstOrNull { it.label.equals(selectedLabel, true) }
                val grams = (portion?.gramsPerUnit?.let { amount * it } ?: amount)

                // Still try to map to enum if you like keeping it populated
                val enumUnit = MeasurementType.tryParse(selectedLabel)
                val inputLabel = portion?.label.takeUnless { it.isNullOrBlank() } ?: selectedLabel

                lifecycleScope.launch(Dispatchers.IO) {
                    repo.updateLogServing(
                        logId         = logId,
                        newGrams      = grams,
                        inputUnit     = enumUnit,       // can be null for API-only labels
                        inputQuantity = amount,
                        inputLabel    = inputLabel      // ← persist API label
                    )
                    withContext(Dispatchers.Main) {
                        refreshTodayMeals()
                        refreshTodayTotals()
                        dlg.dismiss()
                    }
                }

//                val amountText   = binding.etAmount?.text?.toString()?.trim().orEmpty()
//                val amount       = amountText.toDoubleOrNull() ?: defaultAmount
//                val selectedText = binding.actvUnit.text?.toString()?.trim().orEmpty()
//
//                // 1) Resolve grams for macros using the API portion list you already loaded
//                val pickedPortion = apiPortions.firstOrNull { it.label.equals(selectedText, true) }
//                val grams = when {
//                    pickedPortion != null -> amount * pickedPortion.gramsPerUnit
//                    else -> amount // fallback; your list usually includes "grams (g)" → gramsPerUnit=1.0
//                }
//
//                // 2) Try to map the selected text to your enum so we can store the *chosen* unit
//                val enumUnit = MeasurementType.tryParse(selectedText)
//
//                lifecycleScope.launch(Dispatchers.IO) {
//                    try {
//                        repo.updateLogServing(
//                            logId         = logId,
//                            newGrams      = grams,          // keep macros consistent
//                            inputUnit     = enumUnit,       // ← store what the user picked (if parseable)
//                            inputQuantity = amount          // ← and the amount they typed
//                        )
//                        withContext(Dispatchers.Main) {
//                            refreshTodayMeals()
//                            refreshTodayTotals()
//                            dlg.dismiss()
//                        }
//                    } catch (t: Throwable) {
//                        Log.e("DialogEditServing", "Failed to update log", t)
//                        withContext(Dispatchers.Main) {
//                            binding.tilUnit.error = "Failed to save. Try again."
//                        }
//                    }
//                }
            }

        }

        dlg.show()
    }



    private fun showMacroSettingsDialog() {
        if (currentUserId <= 0) {
            Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // Snapshot today's totals into diary
            upsertTodayDiarySnapshot()

            val plan = withContext(Dispatchers.IO) {
                macroPlan ?: db.macroPlanDao().getLatestForUser(currentUserId)
            }

            val view = layoutInflater.inflate(R.layout.dialog_macro_settings, null)
            val range = view.findViewById<com.google.android.material.slider.RangeSlider>(R.id.rs_macro)
            val tvSummary = view.findViewById<TextView>(R.id.tv_summary)
            val tvCaloriesHint = view.findViewById<TextView>(R.id.tv_calories_hint)
            val btnCancel = view.findViewById<ImageButton>(R.id.btn_cancel)
            val btnSave = view.findViewById<ImageButton>(R.id.btn_save)

            val btnAddDeficit = view.findViewById<View>(R.id.btn_add_deficit)
            val btnReduceDeficit = view.findViewById<View>(R.id.btn_reduce_deficit)

            range.valueFrom = 0f
            range.valueTo = 100f
            range.stepSize = 1f


            // Make calories mutable so buttons can adjust it
            var goalCalories = (plan?.calories ?: 2000.0).toInt().coerceAtLeast(1)

            // Current grams → initial percentages
            val curP = (plan?.protein ?: 150.0).toInt()
            val curF = (plan?.fat ?: 60.0).toInt()
            val curC = (plan?.carbs ?: 250.0).toInt()

            var pPct = ((curP * 4f / goalCalories) * 100f).roundToInt().coerceIn(0, 100)
            var fPct = ((curF * 9f / goalCalories) * 100f).roundToInt().coerceIn(0, 100)
            var cPct = 100 - pPct - fPct
            if (cPct < 0) { fPct = (fPct + cPct).coerceAtLeast(0); cPct = 0 }

            var left = pPct.coerceIn(0, 100)
            var right = (pPct + fPct).coerceIn(left, 100)

            // Initialize UI
            tvCaloriesHint.text = "Calories: $goalCalories kcal"
            range.values = mutableListOf(left.toFloat(), right.toFloat())

            fun updateSummary() {
                left = range.values[0].toInt()
                right = range.values[1].toInt()

                val proteinPct = left
                val fatPct = (right - left).coerceAtLeast(0)
                var carbsPct = (100 - right).coerceAtLeast(0)
                val adjust = 100 - (proteinPct + fatPct + carbsPct)
                carbsPct += adjust

                val proteinG = ((goalCalories * proteinPct) / 100f / 4f).roundToInt()
                val fatG     = ((goalCalories * fatPct) / 100f / 9f).roundToInt()
                val carbsG   = ((goalCalories * carbsPct) / 100f / 4f).roundToInt()

                tvSummary.text = "Protein $proteinPct% (${proteinG}g) • " +
                        "Fat $fatPct% (${fatG}g) • " +
                        "Carbs $carbsPct% (${carbsG}g)"

                btnSave.isEnabled = (proteinPct + fatPct + carbsPct == 100)
                btnSave.alpha = if (btnSave.isEnabled) 1f else 0.5f
                btnSave.setTag(R.id.btn_save, Triple(proteinG, fatG, carbsG))
            }

            // Recalculate when slider moves
            range.addOnChangeListener { _, _, _ -> updateSummary() }
            updateSummary()

            // NEW: helper to change calories by ±50 and refresh UI
            fun adjustCalories(delta: Int) {
                val minCal = 1000 // tweak if needed
                val maxCal = 5000 // tweak if needed
                val newVal = (goalCalories + delta).coerceIn(minCal, maxCal)
                if (newVal != goalCalories) {
                    goalCalories = newVal
                    tvCaloriesHint.text = "Calories: $goalCalories kcal"
                    updateSummary()
                }
            }

            // NEW: wire buttons
            btnAddDeficit.setOnClickListener {
                it.startAnimation(pressAnim)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                adjustCalories(-50) // subtract 50 = higher deficit
            }
            btnReduceDeficit.setOnClickListener {
                it.startAnimation(pressAnim)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                adjustCalories(+50) // add 50 = lower deficit
            }

            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
                android.view.ContextThemeWrapper(
                    this@MacroActivity,
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                )
            ).setView(view).create()

            dialog.setOnShowListener {
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                (view.parent as? ViewGroup)?.apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(0, 0, 0, 0)
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
                    calories = goalCalories              // <- save adjusted calories
                ) ?: com.example.fitquest.database.MacroPlan(
                    userId = currentUserId,
                    calories = goalCalories,             // <- save adjusted calories
                    protein = proteinG,
                    carbs = carbsG,
                    fat = fatG
                )

                macroPlan = updated

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        db.macroPlanDao().upsert(updated)
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
        return if (remaining >= 0) {
            "${remaining} left"
        } else {
            "${-remaining} over"
        }
    }

    private fun formatQty(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

    private fun updateCapybaraMood(
        doneCalories: Int, doneProtein: Int, doneCarbs: Int, doneFat: Int,
        goalCalories: Int, goalProtein: Int, goalCarbs: Int, goalFat: Int
    ) {

        // Capybara
        capybaraView = findViewById(R.id.iv_capybara)

        // Nothing logged = all zeros
        val nothingLogged = (doneCalories == 0 && doneProtein == 0 && doneCarbs == 0 && doneFat == 0)

        // Bloated if ANY macro OR calories is over the plan
        val anyOver = (doneCalories > goalCalories) ||
                (doneProtein > goalProtein) ||
                (doneCarbs > goalCarbs) ||
                (doneFat > goalFat)

        val (drawableRes, desc) = when {
            nothingLogged -> R.drawable.capybara_hungry to "Hungry capybara"
            anyOver      -> R.drawable.capybara_bloated to "Bloated capybara"
            else         -> R.drawable.capybara_fulfilled to "Fulfilled capybara"
        }

        // Simple fade to make the change feel alive
        capybaraView.animate().alpha(0f).setDuration(120).withEndAction {
            capybaraView.setImageResource(drawableRes)
            capybaraView.contentDescription = desc
            capybaraView.animate().alpha(1f).setDuration(120).start()
        }.start()
    }


}
