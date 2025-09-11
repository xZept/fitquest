package com.example.fitquest

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fitquest.models.Exercise
import com.example.fitquest.models.ExerciseVideo
import com.example.fitquest.models.Tips
import com.example.fitquest.utils.ExerciseRepository
import com.example.fitquest.utils.TipsHelper
import com.example.fitquest.utils.TipsLoader
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlin.random.Random
import com.example.fitquest.utils.ExerciseRepository.filterByEquipment
import com.example.fitquest.utils.ExerciseRepository.filterByDifficulty
import com.example.fitquest.utils.ExerciseRepository.prioritizeByGoal


class WorkoutActivity : AppCompatActivity() {

    private val exerciseVideos by lazy { ExerciseRepository.loadExerciseVideos(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        hideSystemNavigation()
        setupNavigationBar()
        setupStartButton()

        val container = findViewById<LinearLayout>(R.id.workout_container)

        val splitDays     = intent.getIntExtra("SPLIT_DAYS", 0)
        val goal          = intent.getStringExtra("GOAL") ?: ""
        val activityLevel = intent.getStringExtra("ACTIVITY_LEVEL") ?: ""
        val healthCond    = intent.getStringExtra("HEALTH_CONDITION") ?: ""
        val equipmentPref = intent.getStringExtra("EQUIPMENT_PREF") ?: ""

        if (splitDays <= 0) return

        // Load exercises
        val allExercises = ExerciseRepository.loadExercises(this)

        // Filter + prioritize
        val userConditionsList = healthCond.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val filtered = allExercises
            .filterByEquipment(equipmentPref)
            .filter { ex -> ExerciseRepository.isSafeForUser(ex, userConditionsList) }
            .filterByDifficulty(activityLevel)
            .prioritizeByGoal(goal)

        // Allocate by split & render
        val dayBuckets = ExerciseRepository.splitMap(splitDays)
        val pool = filtered.shuffled(Random(System.currentTimeMillis()))
        val bucketed = ExerciseRepository.allocateToBuckets(pool, dayBuckets)

        container.removeAllViews()
        bucketed.forEach { (dayName, exercisesForDay) ->
            container.addView(buildDayCard(dayName, exercisesForDay))
        }

        // Tips
        displayTips()
    }

    private fun displayTips() {
        val rawGoal = intent.getStringExtra("GOAL") ?: "any"
        val userGoal = mapGoalToCsv(rawGoal)
        val userSplitDays = intent.getIntExtra("SPLIT_DAYS", 3)
        val userCondition = intent.getStringExtra("HEALTH_CONDITION") ?: "any"
        val splitKey = "${userSplitDays}_days"

        val tips: List<Tips> = TipsLoader.loadTips(this)
        val workoutTips = TipsHelper.getWorkoutTips(tips, userGoal, splitKey, userCondition)
        val workoutTipText = findViewById<TextView>(R.id.workoutTip)
        workoutTipText.text = workoutTips.randomOrNull()?.tip ?: "No workout tips available for your plan yet."

        val recoveryTips = tips.filter {
            it.category.equals("recovery", true) &&
                    (it.goal.equals(userGoal, true) || it.goal.equals("any", true)) &&
                    (it.split.equals(splitKey, true) || it.split.equals("any", true)) &&
                    (it.condition.equals(userCondition, true) || it.condition.equals("any", true))
        }
        val recoveryTipText = findViewById<TextView>(R.id.recoveryTip)
        recoveryTipText.text = recoveryTips.randomOrNull()?.tip ?: "No recovery tips available for your plan yet."
    }

    private fun mapGoalToCsv(userGoal: String): String = when (userGoal.lowercase()) {
        "lose fat" -> "weight_loss"
        "build muscle" -> "muscle_gain"
        "maintain" -> "endurance"
        else -> "any"
    }

    // small UI helpers
    private fun hideSystemNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_dashboard).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_shop).setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
        // nav_icon_workout: no-op (already here)
    }

    private fun setupStartButton() {
        findViewById<Button>(R.id.btn_start_workout).setOnClickListener {
            startActivity(Intent(this, WorkoutGeneratorActivity::class.java))
        }
    }

    // Build the day card UI (kept small for readability)
    private var currentlyExpanded: View? = null
    private var currentlyExpandedBtn: ImageButton? = null

    private fun buildDayCard(dayName: String, exercises: List<Exercise>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.active_quest_goblin)
            val p = dp(16)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), dp(8), dp(4), dp(8))
            layoutParams = lp
        }

        // Day title
        val title = TextView(this).apply {
            text = dayName
            setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
            textSize = 18f
        }
        card.addView(title)

        // Scrollable list of exercises
        val scrollArea = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            )
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        exercises.forEach { ex ->
            val exerciseRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.item_container)
                val p = dp(8)
                setPadding(p, p, p, p)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(dp(8), dp(6), dp(8), dp(6))
                layoutParams = lp
            }

            val name = TextView(this).apply {
                text = ex.name
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val muscle = TextView(this).apply {
                text = ex.mainMuscle
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.darker_gray))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            exerciseRow.addView(name)
            exerciseRow.addView(muscle)

            // long press delete (still works)
            exerciseRow.setOnLongClickListener {
                showDeleteDialog(inner, exerciseRow)
                true
            }

            inner.addView(exerciseRow)
        }

        card.setOnClickListener {
            val intent = Intent(this, ExerciseActivity::class.java).apply {
                putStringArrayListExtra("EXERCISES", ArrayList(exercises.map { it.name }))
                putExtra("DAY_NAME", dayName)
            }
            startActivity(intent)
        }


        scrollArea.addView(inner)
        card.addView(scrollArea)
        return card
    }


    private fun showDeleteDialog(parent: ViewGroup, item: View) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_delete_only)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.6f)
        }
        val btnDelete = dialog.findViewById<Button>(R.id.btn_delete)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        btnDelete.setOnClickListener {
            parent.removeView(item)
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showVideoDialog(youtubeUrl: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_youtube)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.6f)
        }
        val closeBtn = dialog.findViewById<ImageButton>(R.id.btn_close)
        val youTubePlayerView = dialog.findViewById<YouTubePlayerView>(R.id.youtube_player_view)
        lifecycle.addObserver(youTubePlayerView)
        youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                // try to extract id from url
                val id = extractYoutubeId(youtubeUrl)
                if (id != null) player.loadVideo(id, 0f)
            }
        })
        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { youTubePlayerView.release() }
        dialog.show()
    }

    private fun extractYoutubeId(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            if (host.contains("youtu.be")) return uri.lastPathSegment
            if (host.contains("youtube.com")) {
                uri.getQueryParameter("v") ?: uri.pathSegments.windowed(2,1, true).firstOrNull { it.size==2 && it[0].equals("embed", true) }?.get(1)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
