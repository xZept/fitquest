package com.example.fitquest

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.Window
import android.widget.ImageView
import com.bumptech.glide.Glide

class ExerciseActivity : AppCompatActivity() {

    private lateinit var exerciseContainer: FrameLayout
    private lateinit var containers: List<Pair<String, String>> // Pair<exerciseName, reps>
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        // 🎬 Load your single GIF into the top ImageView
        val gifView = findViewById<ImageView>(R.id.exercise_gif)
        Glide.with(this)
            .asGif()
            .load(R.raw.test) // your single GIF in res/raw/workout.gif
            .into(gifView)

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

        exerciseContainer = findViewById(R.id.exercise_container)

        // Get the exercises passed from WorkoutActivity (must be String ArrayList)
        val originalExercises = intent.getStringArrayListExtra("EXERCISES") ?: arrayListOf()

        // Multiply each exercise by 3 → each container shows "x8"
        containers = originalExercises.flatMap { ex ->
            List(3) { Pair(ex, "x8") } // produces [ (ex,x8), (ex,x8), (ex,x8) ]
        }

        supportActionBar?.title = intent.getStringExtra("DAY_NAME") ?: "Workout"

        if (containers.isNotEmpty()) {
            showContainer(currentIndex)
        } else {
            Toast.makeText(this, "No exercises found", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showContainer(index: Int) {
        if (index !in containers.indices) return

        exerciseContainer.removeAllViews()
        val itemView = layoutInflater.inflate(R.layout.exercise_item, exerciseContainer, false)

        val tvName = itemView.findViewById<TextView>(R.id.exercise_name)
        val tvReps = itemView.findViewById<TextView>(R.id.exercise_sets_reps)
        val btnPrev = itemView.findViewById<Button>(R.id.btn_previous)
        val btnDone = itemView.findViewById<Button>(R.id.btn_done)
        val btnSkip = itemView.findViewById<Button>(R.id.btn_skip)

        val (name, reps) = containers[index]
        val setNumber = (index % 3) + 1
        tvName.text = "$name (Set $setNumber/3)"
        tvReps.text = reps

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showContainer(currentIndex)
            }
        }

        btnSkip.setOnClickListener {
            goToNext()
        }

        btnDone.setOnClickListener {
            // Instead of jumping immediately → open timer popup
            showTimerDialog(name)
        }

        exerciseContainer.addView(itemView)
    }

    private fun goToNext() {
        if (currentIndex < containers.size - 1) {
            currentIndex++
            showContainer(currentIndex)
        } else {
            Toast.makeText(this, "Workout complete 🎉", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTimerDialog(exerciseName: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.popup_timer)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val tvName = dialog.findViewById<TextView>(R.id.timer_exercise_name)
        val tvTimer = dialog.findViewById<TextView>(R.id.timer_text)
        val btnSkip = dialog.findViewById<Button>(R.id.btn_skip_timer)
        val animationView = dialog.findViewById<ImageView>(R.id.timer_animation)

        // Always show "Rest" instead of the exercise name
        tvName.text = "Rest"
        tvTimer.text = "04:00"

        // Load fantasy/pixel style GIF into ImageView
        Glide.with(this)
            .asGif()
            .load(R.drawable.rest_timer_animation) // put your GIF in res/drawable/
            .into(animationView)

        val totalTime = 4 * 60 * 1000L
        var countDownTimer: CountDownTimer? = object : CountDownTimer(totalTime, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                dialog.dismiss()
                goToNext()
            }
        }

        countDownTimer?.start()

        btnSkip.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
            goToNext()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
            countDownTimer = null
        }

        dialog.show()
    }

}
