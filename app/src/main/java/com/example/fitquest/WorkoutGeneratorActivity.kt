package com.example.fitquest

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        val seekbarSplit = findViewById<SeekBar>(R.id.seekbar_split)
        val tvSplitLabel = findViewById<TextView>(R.id.tv_split_label)
        val btnSubmit = findViewById<Button>(R.id.btn_generate_plan)

        explanationContainer = findViewById(R.id.explanation_container)
        explanationText = findViewById(R.id.explanation_text)
        explanationContainer.visibility = View.GONE

        val messages = mapOf(
            R.id.input_height to "Height is used to calculate your BMI and tailor your workout intensity.",
            R.id.input_weight to "Weight is used to calculate calorie needs and track progress.",
            R.id.spinner_goal to "Your fitness goal determines the type of workout plan you'll get.",
            R.id.spinner_activity to "Activity level helps estimate your daily calorie burn.",
            R.id.seekbar_split to "Workout split is the number of workout days per week."
        )

        inputHeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showExplanation(messages[R.id.input_height]!!)
        }
        inputWeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showExplanation(messages[R.id.input_weight]!!)
        }

        spinnerGoal.setOnTouchListener { _, _ ->
            showExplanation(messages[R.id.spinner_goal]!!)
            false
        }
        spinnerActivity.setOnTouchListener { _, _ ->
            showExplanation(messages[R.id.spinner_activity]!!)
            false
        }

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
            val heightStr = inputHeight.text.toString().trim()
            val weightStr = inputWeight.text.toString().trim()

            if (heightStr.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter both height and weight.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val height = heightStr.toFloatOrNull()
            val weight = weightStr.toFloatOrNull()
            if (height == null || weight == null) {
                Toast.makeText(this, "Height and Weight must be valid numbers.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goal = spinnerGoal.selectedItem?.toString() ?: ""
            val activity = spinnerActivity.selectedItem?.toString() ?: ""
            val splitDays = if (seekbarSplit.progress < 1) 1 else seekbarSplit.progress

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
        val editTextIds = listOf(R.id.input_height, R.id.input_weight)
        for (id in editTextIds) {
            val edit = findViewById<EditText?>(id)
            edit?.setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(
                    if (hasFocus) R.drawable.user_input_bg_selected
                    else R.drawable.user_input_bg
                )
            }
        }

        val spinnerIds = listOf(R.id.spinner_goal, R.id.spinner_activity)
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
