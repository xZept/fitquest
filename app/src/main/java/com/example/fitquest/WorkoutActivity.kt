package com.example.fitquest

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.Exercise
import com.example.fitquest.models.QuestExercise
import com.example.fitquest.models.Tips
import com.example.fitquest.utils.ExerciseRepository
import com.example.fitquest.utils.ExerciseRepository.filterByDifficulty
import com.example.fitquest.utils.ExerciseRepository.filterByEquipment
import com.example.fitquest.utils.ExerciseRepository.prioritizeByGoal
import com.example.fitquest.utils.TipsHelper
import com.example.fitquest.utils.TipsLoader
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

class WorkoutActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase

    // overlay host
    private val overlayHost: View by lazy { findViewById(R.id.overlay_host) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        // make sure overlay (if visible) sits above
        findViewById<View>(R.id.overlay_host)?.bringToFront()

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        hideSystemNavigation()
        setupNavigationBar()

        db = AppDatabase.getInstance(applicationContext)

        // Render saved quest if one exists; otherwise show overlay
        lifecycleScope.launch { renderFromState() }

        displayTips()
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.overlay_host)?.bringToFront()
        lifecycleScope.launch { renderFromState() }
    }

    // -------------------- State & overlay --------------------

    private suspend fun renderFromState() {
        val container = findViewById<LinearLayout>(R.id.workout_container)
        val uid = DataStoreManager.getUserId(this@WorkoutActivity).first()
        val active = db.activeQuestDao().getActiveForUser(uid)

        withContext(Dispatchers.Main) {
            container.removeAllViews()
            if (active != null) {
                setOverlayVisible(false)
                val title =
                    if (!active.split.isNullOrBlank() && !active.modifier.isNullOrBlank())
                        "${active.split} • ${active.modifier}"
                    else
                        "Your Quest"
                container.addView(buildDayCardFromQuest(title, active.exercises))
                // Show action buttons when a quest exists
                updateActionButtons(visible = true, title, active.exercises)
            } else {
                setOverlayVisible(true)
                updateActionButtons(visible = false, null, emptyList())
            }
        }
    }

    private fun setOverlayVisible(visible: Boolean) {
        overlayHost.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) overlayHost.bringToFront()
    }

    // -------------------- Tips (repo) --------------------

    private fun displayTips() {
        val rawGoal = intent.getStringExtra("GOAL") ?: "any"
        val userGoal = mapGoalToCsv(rawGoal)
        val userSplitDays = intent.getIntExtra("SPLIT_DAYS", 3)
        val userCondition = intent.getStringExtra("HEALTH_CONDITION") ?: "any"
        val splitKey = "${userSplitDays}_days"

        val tips: List<Tips> = TipsLoader.loadTips(this)

        val workoutTips = TipsHelper.getWorkoutTips(tips, userGoal, splitKey, userCondition)
        findViewById<TextView>(R.id.workoutTip).text =
            workoutTips.randomOrNull()?.tip ?: "No workout tips available for your plan yet."

        val recoveryTips = tips.filter {
            it.category.equals("recovery", true) &&
                    (it.goal.equals(userGoal, true) || it.goal.equals("any", true)) &&
                    (it.split.equals(splitKey, true) || it.split.equals("any", true)) &&
                    (it.condition.equals(userCondition, true) || it.condition.equals("any", true))
        }
        findViewById<TextView>(R.id.recoveryTip).text =
            recoveryTips.randomOrNull()?.tip ?: "No recovery tips available for your plan yet."
    }

    private fun mapGoalToCsv(userGoal: String): String = when (userGoal.lowercase()) {
        "lose fat" -> "weight_loss"
        "build muscle" -> "muscle_gain"
        "maintain" -> "endurance"
        else -> "any"
    }

    // -------------------- Action buttons --------------------

    private fun updateActionButtons(
        visible: Boolean,
        dayTitle: String?,
        items: List<QuestExercise>
    ) {
        val bar = findViewById<LinearLayout>(R.id.quest_actions)
        val btnCancel = findViewById<ImageButton>(R.id.btn_cancel_quest)
        val btnStart = findViewById<ImageButton>(R.id.btn_start_quest)

        bar.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        btnCancel.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val uid = DataStoreManager.getUserId(this@WorkoutActivity).first()
                db.activeQuestDao().clearForUser(uid)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WorkoutActivity, "Quest canceled.", Toast.LENGTH_SHORT).show()
                    renderFromState()
                }
            }
        }

        btnStart.setOnClickListener {
            it.startAnimation(pressAnim)
            // >>> START SESSION HERE <<<
            startActivity(Intent(this, WorkoutSessionActivity::class.java))
            // If you ever want to pass extras (e.g., resume flags), add putExtra here.
        }
    }

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_dashboard).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DashboardActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_shop).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ShopActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
    }

    // -------------------- Card builders --------------------

    /** Repo-styled card for *saved* quests using your QuestExercise model (no poster bg). */
    private fun buildDayCardFromQuest(dayName: String, items: List<QuestExercise>): View {
        // Outer shell sizes itself to the PNG’s intrinsic size
        val shell = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(8), dp(4), dp(8)) }
            clipToPadding = false
            clipChildren = false
        }

        // Background image at ORIGINAL size (no stretch)
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.container_split_plan)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = null
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        shell.addView(bg)

        // Content layer overlays the image and matches its measured size
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(16), dp(12), dp(16), dp(12)) // keep text off the art edges
        }

        // Title (tap to open ExerciseActivity, not the whole card—so ScrollView keeps touch)
        val title = TextView(this).apply {
            text = dayName
            setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
            textSize = 35f

            // make it span the row so it can be centered
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 25, 0, dp(0)) // optional spacing below title
                gravity = Gravity.CENTER_HORIZONTAL // child alignment in LinearLayout (optional)
            }

            // center the text inside the TextView
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        title.setOnClickListener {
            val intent = Intent(this, ExerciseActivity::class.java).apply {
                putStringArrayListExtra("EXERCISES", ArrayList(items.map { it.name }))
                putExtra("DAY_NAME", dayName)
            }
            startActivity(intent)
        }
        content.addView(title)

        // Vertical scroll area (fills remaining height under the title)
        val scrollArea = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        // Ensure parent doesn’t intercept (restores scrolling)
        scrollArea.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Row background: use container_exercsise.png at ORIGINAL size
        val rowBgDrawable = ContextCompat.getDrawable(this, R.drawable.container_exercise)!!
        val rowW = rowBgDrawable.intrinsicWidth
        val rowH = rowBgDrawable.intrinsicHeight

        items.sortedBy { it.order }.forEach { ex ->
            // A row shell exactly the size of the PNG
            val rowShell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(rowW, rowH).apply {
                    setMargins(dp(8), dp(6), dp(8), dp(6))
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            // The PNG itself (fills the shell, but shell == intrinsic size ⇒ no stretch)
            val rowBg = ImageView(this).apply {
                setImageDrawable(rowBgDrawable)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            rowShell.addView(rowBg)

            // Foreground row content constrained to the PNG bounds
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(12), dp(8), dp(12), dp(8)) // adjust to your art’s safe area
            }

            val name = TextView(this).apply {
                text = ex.name
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
                textSize = 16f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val label = when {
                !ex.primaryMover.isNullOrBlank() -> ex.primaryMover
                !ex.movementPattern.isNullOrBlank() -> ex.movementPattern
                else -> null
            }
            val detailText = if (label != null) "$label • ${ex.sets}×${ex.repsMin}-${ex.repsMax}"
            else "${ex.sets}×${ex.repsMin}-${ex.repsMax}"

            val detail = TextView(this).apply {
                text = detailText
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.darker_gray))
                textSize = 14f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            row.addView(name)
            row.addView(detail)
            rowShell.addView(row)
            inner.addView(rowShell)
        }

        scrollArea.addView(inner)
        content.addView(scrollArea)
        shell.addView(content)
        return shell
    }


    /** Repo’s original card for intent-generated preview (kept; no poster bg). */
    private fun buildDayCard(dayName: String, exercises: List<Exercise>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(8)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), dp(8), dp(4), dp(8))
            }
        }

        val title = TextView(this).apply {
            text = dayName
            setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
            textSize = 18f
        }
        card.addView(title)

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
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) }
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
            }

            exerciseRow.addView(name)
            exerciseRow.addView(muscle)
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

    // -------------------- Misc utils --------------------

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    // Repo video dialog kept for parity (optional)
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
                extractYoutubeId(youtubeUrl)?.let { player.loadVideo(it, 0f) }
            }
        })
        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { youTubePlayerView.release() }
        dialog.show()
    }

    private fun extractYoutubeId(url: String): String? = try {
        val uri = Uri.parse(url)
        val host = uri.host ?: return null
        if (host.contains("youtu.be")) uri.lastPathSegment
        else if (host.contains("youtube.com")) {
            uri.getQueryParameter("v") ?: uri.pathSegments.windowed(2, 1, true)
                .firstOrNull { it.size == 2 && it[0].equals("embed", true) }?.get(1)
        } else null
    } catch (_: Exception) { null }
}
