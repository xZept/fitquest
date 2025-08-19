package com.example.fitquest

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
import android.widget.SeekBar
import android.widget.LinearLayout

class WorkoutGeneratorActivity : AppCompatActivity() {

    private lateinit var explanationContainer: LinearLayout
    private lateinit var explanationText: TextView

    // handler for delayed hide
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        explanationContainer.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_generator)

        setupInputFocusEffects()

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

        val messages = mapOf(
            R.id.input_height to "Enter your height in centimeters (e.g., 170). Valid range: 130–300 cm. This is used to calculate your BMI and adjust workout intensity.",
            R.id.input_weight to "Enter your weight in kilograms (e.g., 65). Valid range: 10–500 kg. This is used to estimate calorie needs and track progress.",
            R.id.spinner_goal to "Select your primary fitness goal (e.g., Build Muscle, Lose Weight, Improve Endurance). This determines your workout structure.",
            R.id.spinner_activity to "Select your daily activity level (e.g., Beginner, Moderate, Active, Very Active). This helps estimate energy expenditure.",
            R.id.seekbar_split to "Choose how many days per week you plan to train (e.g., 3, 4, or 5 days). This affects workout intensity and recovery balance.",
            R.id.input_health_condition to "Type any health conditions you have (e.g., wrist injury, knee pain, asthma). Please use plain words only (no numbers or special characters).",
            R.id.spinner_equipment to "Select your available equipment (Gym Equipment, Home Gym, or Body Weight only). This ensures the program matches your setup."
        )


        val spinner: Spinner = findViewById(R.id.spinner_equipment,)


        val equipmentAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.equipment_choices,
            R.layout.spinner_item
        )
        equipmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_equipment).adapter = equipmentAdapter


        val activityAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.activity_levels,
            R.layout.spinner_item
        )
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_activity).adapter = activityAdapter

        val goalAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.fitness_goals,
            R.layout.spinner_item
        )
        goalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_goal).adapter = goalAdapter

//        adapter.setDropDownViewResource(R.layout.spinner_item)
//        spinner.adapter = adapter


        // explanation
        inputHeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showExplanation(messages[R.id.input_height]!!)
        }
        inputWeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showExplanation(messages[R.id.input_weight]!!)
        }
        inputHealthCondition.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showExplanation(messages[R.id.input_health_condition]!!)
        }
        spinnerGoal.setOnTouchListener { _, _ ->
            showExplanation(messages[R.id.spinner_goal]!!)
            false
        }
        spinnerActivity.setOnTouchListener { _, _ ->
            showExplanation(messages[R.id.spinner_activity]!!)
            false
        }
        spinnerEquipment.setOnTouchListener { _, _ ->
            showExplanation(messages[R.id.spinner_equipment]!!)
            false
        }

        // Custom InputFilter for letters, spaces, and hyphens only
        val filter = InputFilter { source, start, end, dest, dstart, dend ->
            val regex = Regex("^[a-zA-Z\\s-]+$")
            for (i in start until end) {
                if (!source[i].toString().matches(regex)) {
                    return@InputFilter ""
                }
            }
            null
        }

        inputHeight.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(3)) // max 3 digits
        inputWeight.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(3))


        fun validateHeightWeight(): Boolean {
            val heightText = inputHeight.text.toString()
            val weightText = inputWeight.text.toString()

            if (heightText.isEmpty() || weightText.isEmpty()) {
                Toast.makeText(this, "Please enter both height and weight", Toast.LENGTH_SHORT).show()
                return false
            }

            val height = heightText.toInt()
            val weight = weightText.toInt()

            // Validate height
            if (height < 130 || height > 300) {
                Toast.makeText(this, "Please enter a valid height (50–300 cm)", Toast.LENGTH_SHORT).show()
                return false
            }

            // Validate weight
            if (weight < 10 || weight > 500) {
                Toast.makeText(this, "Please enter a valid weight (10–500 kg)", Toast.LENGTH_SHORT).show()
                return false
            }

            return true
        }

        inputHealthCondition.filters = arrayOf(filter)

        seekbarSplit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val days = if (progress < 1) 1 else progress
                tvSplitLabel.text = "Workout Split: $days days/week"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                showExplanation(messages[R.id.seekbar_split]!!)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        btnSubmit.setOnClickListener {
            if (!validateHeightWeight()) {
                return@setOnClickListener
            }

            val heightStr = inputHeight.text.toString().trim()
            val weightStr = inputWeight.text.toString().trim()
            val height = heightStr.toFloatOrNull()
            val weight = weightStr.toFloatOrNull()
            val goal = spinnerGoal.selectedItem?.toString() ?: ""
            val activity = spinnerActivity.selectedItem?.toString() ?: ""
            val splitDays = seekbarSplit.progress.coerceAtLeast(1) // ensures min 1 day

            if (heightStr.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter both height and weight.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
                return@setOnClickListener
            }


            if (height == null || weight == null) {
                Toast.makeText(this, "Height and Weight must be valid numbers.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
                return@setOnClickListener
            }

            // In WorkoutGeneratorActivity.kt (inside your existing btnSubmit.setOnClickListener)
            val intent = Intent(this, WorkoutActivity::class.java)
            intent.putExtra("HEIGHT_CM", height)
            intent.putExtra("WEIGHT_KG", weight)
            intent.putExtra("GOAL", goal)                       // e.g., "Lose Fat", "Build Muscle", "Maintain"
            intent.putExtra("ACTIVITY_LEVEL", activity)         // e.g., "Sedentary", "Light", "Moderate", "Active"
            intent.putExtra("HEALTH_CONDITION", findViewById<EditText>(R.id.input_health_condition).text.toString())
            intent.putExtra("EQUIPMENT_PREF", findViewById<Spinner>(R.id.spinner_equipment).selectedItem?.toString() ?: "")
            intent.putExtra("SPLIT_DAYS", splitDays)
            startActivity(intent)

            Toast.makeText(
                this,
                "Height: $height\nWeight: $weight\nGoal: $goal\nActivity: $activity\nSplit: $splitDays days/week",
                Toast.LENGTH_LONG
            ).show()
        }

    }

    private fun showExplanation(message: String) {
        explanationText.text = message
        explanationContainer.visibility = View.VISIBLE

        // cancel previous hide timer, start new 8-second countdown
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

        val spinnerIds = listOf(R.id.spinner_goal, R.id.spinner_activity,R.id.spinner_equipment)
        for (id in spinnerIds) {
            val s = findViewById<Spinner?>(id)
            s?.setOnTouchListener { view, _ ->
                view.setBackgroundResource(R.drawable.user_input_bg_selected)
                false
            }
        }

        val splitLayout = findViewById<LinearLayout?>(R.id.split_activity)
        splitLayout?.setOnTouchListener { view, _ ->
            view.setBackgroundResource(R.drawable.user_input_bg_selected)
            false
        }
    }
}
