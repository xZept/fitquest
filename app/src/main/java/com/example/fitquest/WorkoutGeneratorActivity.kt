package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class WorkoutGeneratorActivity : AppCompatActivity() {

    private lateinit var explanationContainer: LinearLayout
    private lateinit var explanationText: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        explanationContainer.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_generator)


        setupInputFocusEffects()

        // ✅ Hide navigation bar
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


        // Inputs
        val inputHeight = findViewById<EditText>(R.id.input_height)
        val inputWeight = findViewById<EditText>(R.id.input_weight)
        val spinnerGoal = findViewById<Spinner>(R.id.spinner_goal)
        val spinnerActivity = findViewById<Spinner>(R.id.spinner_activity)
        val inputHealthCondition = findViewById<EditText>(R.id.input_health_condition)
        val spinnerEquipment = findViewById<Spinner>(R.id.spinner_equipment)
        val seekbarSplit = findViewById<SeekBar>(R.id.seekbar_split)
        val tvSplitLabel = findViewById<TextView>(R.id.tv_split_label)
        val btnSubmit = findViewById<Button>(R.id.btn_generate_plan)

        explanationContainer = findViewById(R.id.explanation_container)
        explanationText = findViewById(R.id.explanation_text)
        explanationContainer.visibility = View.GONE

        // Input filters
        val filter = InputFilter { source, start, end, _, _, _ ->
            val regex = Regex("^[a-zA-Z\\s-]+$")
            for (i in start until end) {
                if (!source[i].toString().matches(regex)) {
                    return@InputFilter ""
                }
            }
            null
        }

        inputHeight.filters = arrayOf(InputFilter.LengthFilter(3))
        inputWeight.filters = arrayOf(InputFilter.LengthFilter(3))
        inputHealthCondition.filters = arrayOf(filter)

        // Split seekbar
        seekbarSplit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val days = if (progress < 1) 1 else progress
                tvSplitLabel.text = "Workout Split: $days days/week"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Submit button
        btnSubmit.setOnClickListener {
            val height = inputHeight.text.toString().trim().toFloatOrNull()
            val weight = inputWeight.text.toString().trim().toFloatOrNull()
            val goal = spinnerGoal.selectedItem?.toString() ?: ""
            val activity = spinnerActivity.selectedItem?.toString() ?: ""
            val dietType = spinnerEquipment.selectedItem?.toString() ?: ""
            val splitDays = seekbarSplit.progress.coerceAtLeast(1)

            if (height == null || weight == null) {
                Toast.makeText(this, "Enter valid height & weight", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Send goal + dietType to MacroActivity
            val macroIntent = Intent(this, MacroActivity::class.java)
            macroIntent.putExtra("GOAL", goal)
            macroIntent.putExtra("DIET_TYPE", dietType)
            macroIntent.putExtra("HEALTH_CONDITION", inputHealthCondition.text.toString())
            macroIntent.putExtra("SPLIT", "${splitDays}_days") // optional

            startActivity(macroIntent)

            // ✅ Send workout data to WorkoutActivity
            val workoutIntent = Intent(this, WorkoutActivity::class.java)
            workoutIntent.putExtra("HEIGHT_CM", height)
            workoutIntent.putExtra("WEIGHT_KG", weight)
            workoutIntent.putExtra("GOAL", goal)
            workoutIntent.putExtra("ACTIVITY_LEVEL", activity)
            workoutIntent.putExtra("HEALTH_CONDITION", inputHealthCondition.text.toString())
            workoutIntent.putExtra("EQUIPMENT_PREF", dietType)
            workoutIntent.putExtra("SPLIT_DAYS", splitDays)
            startActivity(workoutIntent)
        }



    }

    private fun showExplanation(message: String) {
        explanationText.text = message
        explanationContainer.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 8000)
    }

    private fun setupInputFocusEffects() {
        val editTextIds = listOf(R.id.input_height, R.id.input_weight, R.id.input_health_condition)
        for (id in editTextIds) {
            val edit = findViewById<EditText?>(id)
            edit?.setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(
                    if (hasFocus) R.drawable.user_input_bg_selected
                    else R.drawable.user_input_bg
                )
            }
        }
    }




    // ---------------- Meal Plan Section ----------------

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
    ) : java.io.Serializable

    private fun addMealToContainer(meal: MealItem, container: LinearLayout) {
        val textView = TextView(this)
        textView.text = "${meal.mealName} - ${meal.calories} kcal"
        textView.textSize = 16f
        textView.setPadding(8, 8, 8, 8)
        container.addView(textView)
    }

    private fun generateMealPlan(context: Context, goal: String, dietType: String): List<MealItem> {
        val allMeals = loadMealsFromCSV(context)
        return allMeals.filter {
            (goal.isEmpty() || it.goal.equals(goal, ignoreCase = true)) &&
                    (dietType.isEmpty() || it.dietType.equals(dietType, ignoreCase = true))
        }
    }

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
