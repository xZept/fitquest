package com.example.fitquest

import android.app.AlertDialog
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
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.VideoView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.fitquest.R
import com.example.fitquest.utils.ExerciseRepository
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class ExerciseActivity : AppCompatActivity() {

    private lateinit var exerciseContainer: FrameLayout
    private lateinit var containers: List<Pair<String, String>>
    private var currentIndex = 0
    private lateinit var currentExerciseName: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        currentExerciseName = intent.getStringExtra("EXERCISE_NAME") ?: ""

        val toolbar = findViewById<Toolbar>(R.id.exercise_toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val gifView = findViewById<ImageView>(R.id.exercise_gif)
        Glide.with(this)
            .asGif()
            .load(R.raw.test)
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

        val originalExercises = intent.getStringArrayListExtra("EXERCISES") ?: arrayListOf()

        containers = originalExercises.flatMap { ex ->
            List(3) { Pair(ex, "x8") }
        }

        supportActionBar?.title = intent.getStringExtra("DAY_NAME") ?: "Workout"

        if (containers.isNotEmpty()) {
            showContainer(currentIndex)
        } else {
            Toast.makeText(this, "No exercises found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.exercise_menu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                showInfoDialog(currentExerciseName)
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }





    private fun showInfoDialog(exerciseName: String) {
        val dialogView = layoutInflater.inflate(R.layout.popup_exercise_info, null)

        val tvMuscle = dialogView.findViewById<TextView>(R.id.tvMuscle)
        val tvEquipment = dialogView.findViewById<TextView>(R.id.tvEquipment)
        val tvMechanics = dialogView.findViewById<TextView>(R.id.tvMechanics)
        val tvForce = dialogView.findViewById<TextView>(R.id.tvForce)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)
        val videoContainer = dialogView.findViewById<LinearLayout>(R.id.videoContainer)

        val exercise = ExerciseRepository.getExerciseByName(this, exerciseName)
        val videos = ExerciseRepository.getVideosByExercise(this, exerciseName)

        if (exercise != null) {
            tvMuscle.text = "Target Muscle: ${exercise.targetMuscles}"
            tvEquipment.text = "Equipment: ${exercise.equipment}"
            tvMechanics.text = "Mechanics: ${exercise.mechanics}"
            tvForce.text = "Force: ${exercise.force}"
            tvDescription.text = exercise.description
        } else {
            tvMuscle.text = "No details found for $exerciseName"
        }

        videoContainer.removeAllViews()
        for (video in videos) {
            val videoId = extractVideoId(video.youtubeLink)

            val thumbnail = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(500, 300).apply {
                    setMargins(16, 8, 16, 8)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setBackgroundColor(Color.WHITE)
            }

            Glide.with(this)
                .load("https://img.youtube.com/vi/${extractVideoId(video.youtubeLink)}/0.jpg")
                .into(thumbnail)

            thumbnail.setOnClickListener {
                showYouTubeDialog(videoId)
            }

            videoContainer.addView(thumbnail)
        }


        AlertDialog.Builder(this)
            .setTitle(exerciseName)
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("embed/") -> url.substringAfter("embed/").substringBefore("?")
            else -> url
        }
    }

    private fun showYouTubeDialog(videoId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_youtube, null)

        val youtubePlayerView = dialogView.findViewById<YouTubePlayerView>(R.id.youtube_player_view)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btn_close)

        val dialog = AlertDialog.Builder(this, R.style.CustomDialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        lifecycle.addObserver(youtubePlayerView)
        youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                player.loadVideo(videoId, 0f)
            }
        })

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

        currentExerciseName = name

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

        tvName.text = "Rest"
        tvTimer.text = "04:00"



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
