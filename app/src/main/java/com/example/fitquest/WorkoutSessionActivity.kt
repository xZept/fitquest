package com.example.fitquest

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.*
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.QuestExercise
import com.example.fitquest.tips.WarmupTipsDialogFragment
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class WorkoutSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var userId: Int = -1

    // UI (top)
    private lateinit var ivMonster: ImageView
    private lateinit var hpBar: ProgressBar
    private lateinit var tvHp: TextView

    // UI (bottom)
    private lateinit var tvDay: TextView
    private lateinit var tvExercise: TextView
    private lateinit var tvSetInfo: TextView
    private lateinit var tvRepRange: TextView
    private lateinit var btnComplete: ImageButton
    private lateinit var btnEnd: ImageButton

    private lateinit var ivHpBg: ImageView
    private lateinit var cardBg: ImageView

    // Session state
    private var questTitle: String = "Your Quest"
    private lateinit var activeQuest: ActiveQuest
    private var items: List<QuestExercise> = emptyList()
    private var exerciseIndex = 0
    private var setIndex = 0
    private var restSeconds = 60
    private var isResting = false

    // Sprite sheet animations
    private data class SpriteAnim(
        val drawable: com.example.fitquest.ui.widgets.SpriteSheetDrawable,
        val frames: Int,
        val fps: Int,
        val loop: Boolean
    )
    private var idleAnim: SpriteAnim? = null
    private var fightAnim: SpriteAnim? = null
    private var currentAnim: SpriteAnim? = null

    private var userSex: String = "male"

    // HP
    private var totalSets = 1
    private var setsDone = 0
    private var hpMax = 100
    private var hpLeft = 100

    // Coins
    private var coinsEarned = 0
    private var monsterMultiplier = 1   // x1 by default

    // timers
    private var restTimer: CountDownTimer? = null

    // animation timing
    private val ATTACK_ANIM_MS = 700L

    // DB session row id
    private var sessionRowId: Long = -1L
    private var startedAtMs: Long = 0L

    // current monster identifiers
    private var currentMonsterSprite: String = "monster_slime"
    private var currentMonsterCode: String = "slime"

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_session)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        // bind (top)
        ivMonster = findViewById(R.id.iv_monster)
        hpBar = findViewById(R.id.hp_bar)
        tvHp = findViewById(R.id.tv_hp)
        ivHpBg = findViewById(R.id.iv_hp_bg)

        // bind (bottom)
        tvDay = findViewById(R.id.tv_day_title)
        tvExercise = findViewById(R.id.tv_exercise)
        tvSetInfo = findViewById(R.id.tv_set_info)
        tvRepRange = findViewById(R.id.tv_rep_range)
        btnComplete = findViewById(R.id.btn_complete_set)
        btnEnd = findViewById(R.id.btn_end_session)

        cardBg = findViewById(R.id.card_bg)
        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@WorkoutSessionActivity).first()

            val loaded = withContext(Dispatchers.IO) { db.activeQuestDao().getActiveForUser(userId) }
            if (loaded == null) {
                Toast.makeText(this@WorkoutSessionActivity, "No active quest.", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            activeQuest = loaded

            // ensure wallet row exists
            withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }

            // load user settings (rest timer) from Room
            restSeconds = withContext(Dispatchers.IO) {
                db.userSettingsDao().getByUserId(userId)?.restTimerSec ?: 180
            }

            // ----- Load the monster to display + set multiplier -----
            val latestMonster = withContext(Dispatchers.IO) {
                db.monsterDao().getLatestOwnedForUser(userId)
            }
            currentMonsterSprite = latestMonster?.spriteRes ?: "monster_slime"
            currentMonsterCode = latestMonster?.code ?: "slime"
            updateCardBgForCode(currentMonsterCode)
            updateHpBackgroundForCode(currentMonsterCode)
            // --------------------------------------------------------

            userSex = fetchUserSexSafely()
            refreshLatestMonsterAndAnimations(restartIdle = true)
            initAnimations()
            showIdle()

            monsterMultiplier = monsterMultiplierFor(currentMonsterCode)

            questTitle = listOfNotNull(activeQuest.split, activeQuest.modifier)
                .joinToString(" • ").ifBlank { "Your Quest" }
            items = activeQuest.exercises.sortedBy { it.order }

            totalSets = items.sumOf { it.sets }.coerceAtLeast(1)
            setsDone = 0
            hpLeft = hpMax
            updateHpFromSets(animate = false)

            // create session row
            startedAtMs = System.currentTimeMillis()
            sessionRowId = withContext(Dispatchers.IO) {
                db.workoutSessionDao().insert(
                    WorkoutSession(
                        userId = userId,
                        title = questTitle,
                        startedAt = startedAtMs,
                        endedAt = 0L,
                        totalSets = totalSets,
                        completedSets = 0,
                        coinsEarned = 0
                    )
                )
            }

            tvDay.text = questTitle
            showIdle()
            renderCurrent()
            wireButtons()
            setResting(false)

            // 🔒 Only show once and only if preference allows
            if (savedInstanceState == null &&
                supportFragmentManager.findFragmentByTag("warmup_tips") == null
            ) {
                withContext(Dispatchers.IO) {
                    maybeShowWarmupTips() // calls per-user DataStore & shows dialog
                }
            }
        }
    }

    /** Try to infer a useful focus string for the tips dialog if caller didn't pass SESSION_FOCUS. */
    private fun inferSessionFocus(): String {
        val split = (activeQuest.split ?: "").lowercase(Locale.getDefault())
        return when {
            "upper" in split || "push" in split || "pull" in split -> "upper"
            "lower" in split || "legs" in split -> "legs"
            else -> "full"
        }
    }

    /** Check DataStore and show WarmupTipsDialogFragment if enabled. */
    private suspend fun maybeShowWarmupTips() {
        // 🔑 per-user read
        val shouldShow = DataStoreManager.shouldShowWarmupTips(
            this@WorkoutSessionActivity,
            userId
        )
        if (!shouldShow) return

        val focus = intent.getStringExtra("SESSION_FOCUS") ?: inferSessionFocus()
        withContext(Dispatchers.Main) {
            // 🔑 pass userId into the dialog so it saves per-user
            WarmupTipsDialogFragment
                .newInstance(focus, userId)
                .show(supportFragmentManager, "warmup_tips")
        }
    }

    private fun buildAnim(
        @DrawableRes resId: Int,
        rows: Int,
        cols: Int,
        fps: Int,
        loop: Boolean,
        scale: com.example.fitquest.ui.widgets.SpriteSheetDrawable.ScaleMode =
            com.example.fitquest.ui.widgets.SpriteSheetDrawable.ScaleMode.CENTER_CROP
    ): SpriteAnim {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(resources, resId, opts)
        val dr = com.example.fitquest.ui.widgets.SpriteSheetDrawable(bmp, rows, cols, fps, loop, scale)
        return SpriteAnim(dr, frames = rows * cols, fps = fps, loop = loop)
    }

    private fun startAnim(target: SpriteAnim) {
        currentAnim?.drawable?.stop()
        ivMonster.setImageDrawable(target.drawable)
        target.drawable.start()
        currentAnim = target
    }

    private fun stopAnim() {
        currentAnim?.drawable?.stop()
        ivMonster.setImageDrawable(null)
        currentAnim = null
    }

    private fun initAnimations() { initAnimationsForSex(userSex) }

    override fun onPause() {
        super.onPause()
        currentAnim?.drawable?.stop()
    }

    override fun onResume() {
        super.onResume()
        if (!isResting) showIdle()
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) { db.monsterDao().getLatestOwnedForUser(userId) }
            currentMonsterCode = latest?.code ?: "slime"
            updateHpBackgroundForCode(currentMonsterCode)
            updateCardBgForCode(currentMonsterCode)
            initAnimations()
            if (!isResting) showIdle()
        }
    }

    override fun onDestroy() {
        restTimer?.cancel()
        stopAnim()
        super.onDestroy()
    }

    /* ---------- Rendering ---------- */

    private fun renderCurrent() {
        if (exerciseIndex >= items.size) {
            finishSession(success = true); return
        }
        val ex = items[exerciseIndex]
        tvExercise.text = ex.name
        tvSetInfo.text = "Set ${setIndex + 1} of ${ex.sets}"
        tvRepRange.text = "${ex.repsMin} – ${ex.repsMax} reps"
        showIdle()
    }

    private fun wireButtons() {
        btnComplete.setOnClickListener {
            it.startAnimation(pressAnim)
            btnComplete.isEnabled = false
            // Play attack, THEN open log dialog (cleanest UX)
            playAttackThen {
                promptLogThenCompleteSet()
            }
        }
        btnEnd.setOnClickListener {
            it.startAnimation(pressAnim)
            showAbandonDialog()
        }
    }

    private fun showAbandonDialog() {
        val panel = FrameLayout(this)

        // Background image (keeps aspect; no distortion)
        val ivBg = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.container_handler))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        panel.addView(
            ivBg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val d = resources.displayMetrics.density

        // Overlay spans full image so we can center text and anchor buttons
        val overlay = FrameLayout(this)
        panel.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Centered title + message (BLACK text)
        val centerColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding((24 * d).toInt(), (28 * d).toInt(), (24 * d).toInt(), (96 * d).toInt())
        }
        val tvTitle = TextView(this).apply {
            text = "Abandon Quest?"
            setTextColor(Color.BLACK)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        val tvMsg = TextView(this).apply {
            text = "Are you sure you want to end this session now?\n\nYou will NOT receive any rewards."
            setTextColor(Color.BLACK)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, 0)
        }
        centerColumn.addView(tvTitle)
        centerColumn.addView(tvMsg)
        overlay.addView(
            centerColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        )

        // Bottom row with ImageButtons (inside the image)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (16 * d).toInt())
        }

        val btnKeep = ImageButton(this).apply {
            contentDescription = "Keep Going"
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.button_keep_going))
            background = null // transparent (no ripple)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(0, (56 * d).toInt(), 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }

        val btnAbandon = ImageButton(this).apply {
            contentDescription = "Abandon"
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.button_abandon))
            background = null // transparent
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(0, (56 * d).toInt(), 1f)
        }

        btnRow.addView(btnKeep)
        btnRow.addView(btnAbandon)

        overlay.addView(
            btnRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        )

        val dlg = AlertDialog.Builder(this)
            .setView(panel)
            .setCancelable(true)
            .create()

        dlg.setOnShowListener {
            // Transparent window so PNG edges show
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Width ~92% of screen; height wraps to image (no distortion)
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            dlg.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Actions
        btnKeep.setOnClickListener { dlg.dismiss() }
        btnAbandon.setOnClickListener {
            dlg.dismiss()
            finishSession(success = false)
        }

        dlg.show()
    }

    private fun setResting(resting: Boolean) {
        isResting = resting
        btnComplete.isEnabled = !resting
        btnComplete.alpha = if (resting) 0.5f else 1f
    }

    /* ------------ logging dialog ------------ */

    private fun promptLogThenCompleteSet() {
        val types = listOf("Bodyweight", "External load (kg)", "Assisted (-kg)", "Band level")

        // ---------- BG image (keeps aspect) + centered form on top ----------
        val panel = FrameLayout(this)

        val ivBg = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.container_handler))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        panel.addView(
            ivBg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val d = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // top padding requested
            setPadding((24 * d).toInt(), (32 * d).toInt(), (24 * d).toInt(), (16 * d).toInt())
        }
        panel.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        )

        // ---------- Form contents (text in BLACK) ----------
        val lblHeader = TextView(this).apply {
            text = "Log Set"
            setTextColor(Color.BLACK)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (6 * d).toInt(), 0, (10 * d).toInt())
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val lblType = TextView(this).apply {
            text = "Load type"
            setTextColor(Color.BLACK)
        }

        val lightGray = Color.parseColor("#f0f0f0")

        val spinnerAdapter = object : ArrayAdapter<String>(
            this@WorkoutSessionActivity,
            android.R.layout.simple_spinner_item,
            types
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.BLACK)
                v.setBackgroundColor(lightGray)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.BLACK)
                v.setBackgroundColor(lightGray)
                return v
            }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val spType = AppCompatSpinner(this).apply {
            adapter = spinnerAdapter
            background = ColorDrawable(lightGray)
            setPopupBackgroundDrawable(ColorDrawable(lightGray))
        }

        val tvHelp = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 12f
            text = ""
        }

        val etNumber = EditText(this).apply {
            hint = ""
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#66000000"))
        }
        val etBandKg = EditText(this).apply {
            hint = "Optional: estimated resistance (kg)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
            visibility = View.GONE
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#66000000"))
        }
        val etText = EditText(this).apply {
            hint = ""
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            visibility = View.GONE
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#66000000"))
        }

        fun refreshUiForType() {
            when (spType.selectedItem.toString()) {
                "Bodyweight" -> {
                    etNumber.visibility = View.VISIBLE
                    etNumber.hint = "Enter your body weight (kg) — optional"
                    etText.visibility = View.GONE
                    etBandKg.visibility = View.GONE
                    tvHelp.text = "You can leave this blank if you don’t want to track bodyweight."
                }
                "External load (kg)" -> {
                    etNumber.visibility = View.VISIBLE
                    etNumber.hint = "How much did you lift (kg)"
                    etText.visibility = View.GONE
                    etBandKg.visibility = View.GONE
                    tvHelp.text = "Enter the external load only (bar/dumbbell/etc.)."
                }
                "Assisted (-kg)" -> {
                    etNumber.visibility = View.VISIBLE
                    etNumber.hint = "Assistance amount (kg), e.g., 20 for -20 kg"
                    etText.visibility = View.GONE
                    etBandKg.visibility = View.GONE
                    tvHelp.text = "Enter how much the machine/band helped. We’ll save it as a negative (e.g., -20 kg)."
                }
                "Band level" -> {
                    etNumber.visibility = View.GONE
                    etText.visibility = View.VISIBLE
                    etText.hint = "Band color or level (e.g., Green, Level 3)"
                    etBandKg.visibility = View.VISIBLE
                    tvHelp.text = "Band resistance varies by brand. Enter a label (color/level). Optional: add an estimated kg."
                }
            }
        }
        spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshUiForType()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ---------- Buttons INSIDE the container — as ImageButtons ----------
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (16 * d).toInt(), 0, 0)
        }

        val btnSave = ImageButton(this).apply {
            contentDescription = "Save"
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.button_save))
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(0, (56 * d).toInt(), 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }

        val btnSkip = ImageButton(this).apply {
            contentDescription = "Skip"
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.button_skip))
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(0, (56 * d).toInt(), 1f)
        }

        btnRow.addView(btnSave)
        btnRow.addView(btnSkip)

        // Assemble content
        content.addView(lblHeader)
        content.addView(lblType)
        content.addView(spType)
        content.addView(tvHelp)
        content.addView(etNumber)
        content.addView(etText)
        content.addView(etBandKg)
        content.addView(btnRow)

        refreshUiForType()

        // ---------- Dialog ----------
        val dlg = AlertDialog.Builder(this)
            .setView(panel)
            .setCancelable(true)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            dlg.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Handlers
        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)
            val type = spType.selectedItem.toString()
            val valueText: String = when (type) {
                "Bodyweight" -> {
                    val n = etNumber.text.toString().trim()
                    if (n.isEmpty()) "Bodyweight" else "Bodyweight $n kg"
                }
                "External load (kg)" -> {
                    val n = etNumber.text.toString().trim()
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Please enter the load in kg.", Toast.LENGTH_SHORT).show()
                        btnComplete.isEnabled = true
                        return@setOnClickListener
                    }
                    "$n kg"
                }
                "Assisted (-kg)" -> {
                    val n = etNumber.text.toString().trim()
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Please enter the assistance in kg.", Toast.LENGTH_SHORT).show()
                        btnComplete.isEnabled = true
                        return@setOnClickListener
                    }
                    "-$n kg"
                }
                "Band level" -> {
                    val label = etText.text.toString().trim()
                    if (label.isEmpty()) {
                        Toast.makeText(this, "Please enter the band color/level.", Toast.LENGTH_SHORT).show()
                        btnComplete.isEnabled = true
                        return@setOnClickListener
                    }
                    val est = etBandKg.text.toString().trim()
                    if (est.isEmpty()) label else "$label (~$est kg)"
                }
                else -> ""
            }

            // Persist set log
            lifecycleScope.launch(Dispatchers.IO) {
                val ex = items[exerciseIndex]
                val setNum = setIndex + 1
                db.workoutSetLogDao().insert(
                    WorkoutSetLog(
                        sessionId = sessionRowId,
                        exerciseName = ex.name,
                        setNumber = setNum,
                        repsMin = ex.repsMin,
                        repsMax = ex.repsMax,
                        loadType = type,
                        loadValueText = valueText,
                        loggedAt = System.currentTimeMillis()
                    )
                )
            }

            dlg.dismiss()
            onSetLogged()
        }

        btnSkip.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch(Dispatchers.IO) {
                val ex = items[exerciseIndex]
                val setNum = setIndex + 1
                db.workoutSetLogDao().insert(
                    WorkoutSetLog(
                        sessionId = sessionRowId,
                        exerciseName = ex.name,
                        setNumber = setNum,
                        repsMin = ex.repsMin,
                        repsMax = ex.repsMax,
                        loadType = "Skipped log",
                        loadValueText = "",
                        loggedAt = System.currentTimeMillis()
                    )
                )
            }
            dlg.dismiss()
            onSetLogged()
        }

        dlg.setOnDismissListener {
            if (!isResting) btnComplete.isEnabled = true
        }

        dlg.show()
    }

    private fun onSetLogged() {
        setsDone += 1
        updateHpFromSets(animate = true)

        // apply monster-based multiplier
        coinsEarned += monsterMultiplier

        if (setsDone >= totalSets) {
            finishSession(success = true)
        } else {
            showRestDialog(restSeconds)
        }
    }

    private fun nextSetOrExercise() {
        if (exerciseIndex >= items.size) { finishSession(success = true); return }
        val ex = items[exerciseIndex]
        setIndex++
        if (setIndex >= ex.sets) {
            exerciseIndex++
            setIndex = 0
        }
        renderCurrent()
    }

    private fun finishSession(success: Boolean) {
        restTimer?.cancel()
        setResting(false)
        showIdle()

        lifecycleScope.launch(Dispatchers.IO) {
            val ended = System.currentTimeMillis()
            val coins = if (success) coinsEarned else 0

            if (success) {
                db.workoutSessionDao().finishSession(
                    sessionId = sessionRowId,
                    endedAt = ended,
                    completedSets = setsDone,
                    coinsEarned = coins
                )
                if (userId != -1) {
                    db.userWalletDao().ensure(userId)
                    db.userWalletDao().add(userId, coins)
                }
                recordQuestToHistory(activeQuest)
            } else {
                db.workoutSetLogDao().deleteForSession(sessionRowId)
                db.workoutSessionDao().deleteById(sessionRowId)
            }

            db.activeQuestDao().clearForUser(userId)

            withContext(Dispatchers.Main) {
                if (success) {
                    showResultImageDialog(
                        imageRes = R.drawable.container_session_complete,
                        title = "Session Complete",
                        message = "Nice work!\nCoins earned: $coinsEarned"
                    ) { finish() }
                } else {
                    showResultImageDialog(
                        imageRes = R.drawable.container_session_abandoned,
                        title = "Session Ended",
                        message = "You ended the session early.\nNo rewards were given."
                    ) { finish() }
                }
            }
        }
    }

    private fun showResultImageDialog(
        @DrawableRes imageRes: Int,
        title: String,
        message: String,
        onOk: () -> Unit
    ) {
        val panel = FrameLayout(this)

        // Background image (no distortion)
        val ivBg = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, imageRes))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        panel.addView(
            ivBg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val d = resources.displayMetrics.density

        // Overlay spans full image area so we can anchor children
        val overlay = FrameLayout(this)
        panel.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Centered column for title/message (extra bottom padding to avoid the button)
        val centerColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding((24 * d).toInt(), (28 * d).toInt(), (24 * d).toInt(), (72 * d).toInt())
            background = null
        }

        val tvTitle = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        val tvMsg = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, 0)
        }

        centerColumn.addView(tvTitle)
        centerColumn.addView(tvMsg)

        overlay.addView(
            centerColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        )

        // Bottom-right "Continue" image button
        val btnOk = ImageButton(this).apply {
            contentDescription = "Continue"
            setImageDrawable(ContextCompat.getDrawable(this@WorkoutSessionActivity, R.drawable.button_continue))
            background = null // transparent
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        overlay.addView(
            btnOk,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (56 * d).toInt(),
                android.view.Gravity.END or android.view.Gravity.BOTTOM
            ).apply {
                marginEnd = (16 * d).toInt()
                bottomMargin = (16 * d).toInt()
            }
        )

        val dlg = AlertDialog.Builder(this)
            .setView(panel)
            .setCancelable(true)
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            dlg.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        btnOk.setOnClickListener {
            dlg.dismiss()
            onOk()
        }

        dlg.show()
    }

    /* -------- Quest History helpers -------- */

    private fun questKeyOf(q: ActiveQuest): String {
        val json = q.exercises.joinToString("|") {
            "${it.name}:${it.sets}:${it.repsMin}-${it.repsMax}:${it.order}"
        }
        val hash = json.hashCode()
        return "${q.split}|${q.modifier}|$hash"
    }

    private suspend fun recordQuestToHistory(q: ActiveQuest) {
        val key = questKeyOf(q)
        val title = listOfNotNull(q.split, q.modifier).joinToString(" • ").ifBlank { "Your Quest" }
        val row = QuestHistory(
            userId = q.userId,
            key = key,
            title = title,
            split = q.split,
            modifier = q.modifier,
            exercises = q.exercises
        )
        val dao = db.questHistoryDao()
        val inserted = dao.insertIgnore(row)
        if (inserted == -1L) dao.touch(q.userId, key)
        dao.pruneUnpinned(q.userId)
    }

    /* -------- HP animation -------- */

    private fun updateHpFromSets(animate: Boolean) {
        val remainingSets = (totalSets - setsDone).coerceAtLeast(0)
        val target = if (remainingSets == 0) 0
        else ceil(remainingSets.toDouble() * hpMax / totalSets).toInt()

        if (!animate) {
            hpLeft = target
            hpBar.progress = target
            tvHp.text = "HP $hpLeft/$hpMax"
            return
        }
        val start = hpLeft
        val end = target
        ValueAnimator.ofInt(start, end).apply {
            duration = 500
            addUpdateListener { a ->
                val v = a.animatedValue as Int
                hpBar.progress = v
                tvHp.text = "HP $v/$hpMax"
            }
            start()
        }
        hpLeft = end
    }

    private fun updateHpBackgroundForCode(monsterCode: String?) {
        val code = (monsterCode ?: "slime").lowercase()

        // Try old pattern, then new pattern
        val pickedId = resolveFirstDrawable(
            "container_${code}_hp",   // old: container_slime_hp
            "container_hp_${code}"    // new: container_hp_slime
        )

        // Try both fallbacks too
        val fallbackId = resolveFirstDrawable(
            "container_slime_hp",
            "container_hp_slime"
        )

        ivHpBg.setImageResource(if (pickedId != 0) pickedId else fallbackId)
    }

    /* --------- Monster frame helpers (single ImageView) --------- */
    /** Resolve first existing drawable id from a list of names; returns 0 if none found */
    private fun resolveFirstDrawable(vararg names: String): Int {
        for (n in names) {
            val id = resources.getIdentifier(n, "drawable", packageName)
            if (id != 0) return id
        }
        return 0
    }

    /** Hook to load user sex from your own storage. Replace the body when you have a source. */
    private suspend fun fetchUserSexSafely(): String = withContext(Dispatchers.IO) {
        try {
            val sexRaw = db.userDAO().getUserById(userId)?.sex ?: "male"
            if (sexRaw.equals("female", ignoreCase = true)) "female" else "male"
        } catch (_: Throwable) {
            "male"
        }
    }

    private fun initAnimationsForSex(
        sexRaw: String,
        fightSexOverride: String? = null
    ) {
        val idleSex = if (sexRaw.equals("female", true)) "female" else "male"
        val fightSex = when (fightSexOverride?.lowercase()) {
            "female" -> "female"
            "male" -> "male"
            else -> idleSex
        }
        val code = currentMonsterCode.ifBlank { "slime" }

        // Try your naming FIRST: session_<sex>_<monster>_<state>
        val idleRes = resolveFirstDrawable(
            "session_${idleSex}_${code}_idle",
            "idle_${code}_${idleSex}",
            "idle_${code}",
            "idle_${code}_male"
        )

        val fightRes = resolveFirstDrawable(
            "session_${fightSex}_${code}_fight",
            "fight_${code}_${fightSex}",
            "fight_${code}",
            "fight_${code}_male"
        )

        Log.d("AnimPick", "code=$code idleSex=$idleSex fightSex=$fightSex idleRes=$idleRes fightRes=$fightRes")

        // Adjust these if some monsters have different frame counts
        val idleCols = 13
        val fightCols = 24

        idleAnim  = if (idleRes  != 0) buildAnim(idleRes,  rows = 1, cols = idleCols,  fps = 13, loop = true)  else null
        fightAnim = if (fightRes != 0) buildAnim(fightRes, rows = 1, cols = fightCols, fps = 12, loop = false) else null

        // After rebuilding, make sure the currently shown anim is idle for the new monster
        showIdle()
    }

    private suspend fun refreshLatestMonsterAndAnimations(
        restartIdle: Boolean = true,
        fightSexOverride: String? = null
    ) {
        val latest = withContext(Dispatchers.IO) { db.monsterDao().getLatestOwnedForUser(userId) }
        val newCode = latest?.code ?: "slime"

        if (newCode != currentMonsterCode) {
            currentMonsterCode = newCode
            withContext(Dispatchers.Main) {
                initAnimationsForSex(userSex, fightSexOverride)
                if (restartIdle) showIdle()
            }
        } else if (restartIdle) {
            withContext(Dispatchers.Main) { showIdle() }
        }
    }

    private fun resolveDrawableId(name: String): Int {
        val id = resources.getIdentifier(name, "drawable", packageName)
        return if (id != 0) id else 0
    }

    /**
     * stateSuffix can be "idle" or "attack". We try:
     * 1) "<sprite>_<state>" (e.g., monster_goblin_attack)
     * 2) "<sprite>" fallback
     * 3) "monster_slime" ultimate fallback
     */
    private fun setMonsterFrame(stateSuffix: String) {
        val candidate = "${currentMonsterSprite}_${stateSuffix}"
        val id1 = resolveDrawableId(candidate)
        val id2 = if (id1 != 0) id1 else resolveDrawableId(currentMonsterSprite)
        val finalId = if (id2 != 0) id2 else resolveDrawableId("monster_slime")
        if (finalId != 0) ivMonster.setImageResource(finalId)
    }

    private fun showIdle() {
        val idle = idleAnim ?: return
        // If we're already on the idle animation, ensure it's running; else start it.
        if (currentAnim?.drawable === idle.drawable) {
            if (!idle.drawable.isRunning()) idle.drawable.start()
        } else {
            startAnim(idle)
        }
    }

    /**
     * Plays the non-loop 'fight' animation once, then returns to idle and calls onDone().
     */
    private fun playAttackThen(onDone: () -> Unit) {
        val fight = fightAnim
        if (fight == null) {
            onDone(); return
        }

        // ✅ ensure the one-shot anim starts from frame 0 every time
        fight.drawable.resetToStart()
        startAnim(fight)

        val durationMs = (fight.frames * (1000L / fight.fps.coerceAtLeast(1))).toLong()

        ivMonster.postDelayed({
            showIdle()
            onDone()
        }, durationMs)
    }

    private fun showSleep() { /* optional later */ }

    private fun updateCardBgForCode(monsterCode: String?) {
        if (!::cardBg.isInitialized) return  // safety guard

        val code = (monsterCode ?: "slime").lowercase()
        val pickedId = resolveFirstDrawable(
            "container_split_plan_${code}",
            "container_split_plan",
            "container_split_plan_slime"
        )
        if (pickedId != 0) cardBg.setImageResource(pickedId)
    }

    /* --------- Rest dialog --------- */

    private fun showRestDialog(totalSec: Int) {
        setResting(true)

        val view = layoutInflater.inflate(R.layout.dialog_rest_timer, null)
        val ivBg = view.findViewById<ImageView>(R.id.iv_rest_bg)
        val tvCountdown = view.findViewById<TextView>(R.id.tv_rest_countdown)
        val btnSkip = view.findViewById<ImageButton>(R.id.btn_skip_rest)

        // Build the looping spritesheet background (tweak rows/cols/fps to your sheet)
        val restBg = buildRestBgAnim(rows = 1, cols = 20, fps = 12)
        ivBg.setImageDrawable(restBg)

        tvCountdown.text = format(totalSec)

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dlg.setOnShowListener {
            restBg.resetToStart()
            restBg.start()

            // Size and make window bg transparent so edges of your art show
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.92f).toInt()
            dlg.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dlg.setOnDismissListener { restBg.stop() }

        var remaining = totalSec
        restTimer?.cancel()
        restTimer = object : CountDownTimer(totalSec * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                remaining -= 1
                tvCountdown.text = format(remaining)
                val pct = ((remaining / totalSec.toFloat()) * 100f).roundToInt()
                val end = pct.coerceIn(0, 100)
            }
            override fun onFinish() {
                dlg.dismiss()
                setResting(false)
                showIdle()
                nextSetOrExercise()
            }
        }.start()

        btnSkip.setOnClickListener {
            it.startAnimation(pressAnim)
            restTimer?.cancel()
            dlg.dismiss()
            setResting(false)
            showIdle()
            nextSetOrExercise()
        }

        showSleep()
        dlg.show()
    }

    private fun buildRestBgAnim(
        rows: Int = 1,
        cols: Int = 24,
        fps: Int = 18
    ): SpriteSheetDrawable {
        // Use current userSex; default to male if unknown
        val sex = if (userSex.equals("female", ignoreCase = true)) "female" else "male"

        // Try sex-specific sheet first, then fallbacks
        val resId = resolveFirstDrawable(
            "bg_${sex}_rest_spritesheet",
            "bg_rest_spritesheet",
            "bg_male_rest_spritesheet"
        ).let { id ->
            if (id != 0) id else R.drawable.bg_male_rest_spritesheet
        }

        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(resources, resId, opts)
        return SpriteSheetDrawable(
            bmp,
            rows,
            cols,
            fps,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )
    }

    /* --------- Utils --------- */

    private fun format(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun monsterMultiplierFor(code: String): Int = when (code) {
        "mushroom" -> 2   // starter
        "goblin"   -> 3   // tier 2
        "ogre"     -> 4   // tier 3
        "eye"      -> 5   // tier 4 (Giant Eye)
        else       -> 1   // default/slime
    }
}
