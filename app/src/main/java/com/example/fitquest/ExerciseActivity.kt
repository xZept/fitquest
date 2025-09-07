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

class ExerciseActivity : AppCompatActivity() {

    private lateinit var exerciseContainer: FrameLayout
    private lateinit var containers: List<Pair<String, String>> // Pair<exerciseName, reps>
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

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

        // Remove previous view and inflate a new one that fills the FrameLayout
        exerciseContainer.removeAllViews()
        val itemView = layoutInflater.inflate(R.layout.exercise_item, exerciseContainer, false)

        val tvName = itemView.findViewById<TextView>(R.id.exercise_name)
        val tvReps = itemView.findViewById<TextView>(R.id.exercise_sets_reps)
        val btnPrev = itemView.findViewById<Button>(R.id.btn_previous)
        val btnDone = itemView.findViewById<Button>(R.id.btn_done)
        val btnSkip = itemView.findViewById<Button>(R.id.btn_skip)

        val (name, reps) = containers[index]

        // Optional: show set number in the title (Set 1/3) for clarity
        val setNumber = (index % 3) + 1
        tvName.text = "$name  (Set $setNumber/3)"
        tvReps.text = reps

        // Prev
        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showContainer(currentIndex)
            } else {
                Toast.makeText(this, "This is the first container", Toast.LENGTH_SHORT).show()
            }
        }

        // Skip -> jump to next container
        btnSkip.setOnClickListener {
            if (currentIndex < containers.size - 1) {
                currentIndex++
                showContainer(currentIndex)
            } else {
                Toast.makeText(this, "This is the last container", Toast.LENGTH_SHORT).show()
            }
        }

        // Done -> mark as done (for now: just advance to next container)
        btnDone.setOnClickListener {
            // TODO: replace with timer popup or completion handling later
            if (currentIndex < containers.size - 1) {
                currentIndex++
                showContainer(currentIndex)
            } else {
                Toast.makeText(this, "Workout complete 🎉", Toast.LENGTH_LONG).show()
            }
        }

        exerciseContainer.addView(itemView)
    }
}
