package com.example.fitquest

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.*
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.QuestExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.ceil

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
    private lateinit var btnComplete: Button
    private lateinit var btnEnd: Button

    // Session state
    private var questTitle: String = "Your Quest"
    private lateinit var activeQuest: ActiveQuest
    private var items: List<QuestExercise> = emptyList()
    private var exerciseIndex = 0
    private var setIndex = 0
    private var restSeconds = 60
    private var isResting = false

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
    private var currentMonsterSprite: String = "monster_slime" // drawable name (e.g., monster_goblin)
    private var currentMonsterCode: String = "slime"           // code (e.g., goblin)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_session)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        // bind (top)
        ivMonster = findViewById(R.id.iv_monster)
        hpBar = findViewById(R.id.hp_bar)
        tvHp = findViewById(R.id.tv_hp)

        // bind (bottom)
        tvDay = findViewById(R.id.tv_day_title)
        tvExercise = findViewById(R.id.tv_exercise)
        tvSetInfo = findViewById(R.id.tv_set_info)
        tvRepRange = findViewById(R.id.tv_rep_range)
        btnComplete = findViewById(R.id.btn_complete_set)
        btnEnd = findViewById(R.id.btn_end_session)

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
            currentMonsterSprite = latestMonster?.spriteRes ?: "monster_slime" // default slime
            currentMonsterCode = latestMonster?.code ?: "slime"

            // apply idle frame now
            setMonsterFrame(stateSuffix = "idle")

            // coin multiplier based on monster
            monsterMultiplier = monsterMultiplierFor(currentMonsterCode)
            // --------------------------------------------------------

            questTitle = listOfNotNull(activeQuest.split, activeQuest.modifier).joinToString(" • ").ifBlank { "Your Quest" }
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
        }
    }

    override fun onDestroy() {
        restTimer?.cancel()
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
            btnComplete.isEnabled = false
            // Play attack, THEN open log dialog (cleanest UX)
            playAttackThen {
                promptLogThenCompleteSet()
            }
        }
        btnEnd.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Abandon Quest?")
                .setMessage("Are you sure you want to end this session now?\n\nYou will NOT receive any rewards.")
                .setNegativeButton("Keep Going", null)
                .setPositiveButton("Abandon") { _, _ -> finishSession(success = false) }
                .show()
        }
    }

    private fun setResting(resting: Boolean) {
        isResting = resting
        btnComplete.isEnabled = !resting
        btnComplete.alpha = if (resting) 0.5f else 1f
    }

    /* ------------ logging dialog ------------ */

    private fun promptLogThenCompleteSet() {
        val types = listOf("Bodyweight", "External load (kg)", "Assisted (-kg)", "Band level")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val lblType = TextView(this).apply { text = "Load type" }
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@WorkoutSessionActivity, android.R.layout.simple_spinner_dropdown_item, types)
        }
        val tvHelp = TextView(this).apply {
            setTextColor(0xFF9CA3AF.toInt()); textSize = 12f; text = ""
        }
        val etNumber = EditText(this).apply {
            hint = ""; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }
        val etBandKg = EditText(this).apply {
            hint = "Optional: estimated resistance (kg)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true; visibility = android.view.View.GONE
        }
        val etText = EditText(this).apply {
            hint = ""; inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true; visibility = android.view.View.GONE
        }

        fun refreshUiForType() {
            when (spType.selectedItem.toString()) {
                "Bodyweight" -> {
                    etNumber.visibility = android.view.View.VISIBLE
                    etNumber.hint = "Enter your body weight (kg) — optional"
                    etText.visibility = android.view.View.GONE
                    etBandKg.visibility = android.view.View.GONE
                    tvHelp.text = "You can leave this blank if you don’t want to track bodyweight."
                }
                "External load (kg)" -> {
                    etNumber.visibility = android.view.View.VISIBLE
                    etNumber.hint = "How much did you lift (kg)"
                    etText.visibility = android.view.View.GONE
                    etBandKg.visibility = android.view.View.GONE
                    tvHelp.text = "Enter the external load only (bar/dumbbell/kettlebell/etc.)."
                }
                "Assisted (-kg)" -> {
                    etNumber.visibility = android.view.View.VISIBLE
                    etNumber.hint = "Assistance amount (kg), e.g., 20 for -20 kg"
                    etText.visibility = android.view.View.GONE
                    etBandKg.visibility = android.view.View.GONE
                    tvHelp.text = "Enter how much the machine/band helped. We’ll save it as a negative (e.g., -20 kg)."
                }
                "Band level" -> {
                    etNumber.visibility = android.view.View.GONE
                    etText.visibility = android.view.View.VISIBLE
                    etText.hint = "Band color or level (e.g., Green, Level 3)"
                    etBandKg.visibility = android.view.View.VISIBLE
                    tvHelp.text = "Band resistance varies by brand. Enter a label (color/level). Optional: add an estimated kg."
                }
            }
        }
        spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = refreshUiForType()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        refreshUiForType()

        root.addView(lblType); root.addView(spType); root.addView(tvHelp)
        root.addView(etNumber); root.addView(etText); root.addView(etBandKg)

        AlertDialog.Builder(this)
            .setTitle("Log Set")
            .setView(root)
            .setPositiveButton("Save") { _, _ ->
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
                            btnComplete.isEnabled = true; return@setPositiveButton
                        }
                        "$n kg"
                    }
                    "Assisted (-kg)" -> {
                        val n = etNumber.text.toString().trim()
                        if (n.isEmpty()) {
                            Toast.makeText(this, "Please enter the assistance in kg.", Toast.LENGTH_SHORT).show()
                            btnComplete.isEnabled = true; return@setPositiveButton
                        }
                        "-$n kg"
                    }
                    "Band level" -> {
                        val label = etText.text.toString().trim()
                        if (label.isEmpty()) {
                            Toast.makeText(this, "Please enter the band color/level.", Toast.LENGTH_SHORT).show()
                            btnComplete.isEnabled = true; return@setPositiveButton
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

                onSetLogged()
            }
            .setNegativeButton("Skip") { _, _ ->
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
                onSetLogged()
            }
            .setOnDismissListener {
                if (!isResting) btnComplete.isEnabled = true
            }
            .show()
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
                    AlertDialog.Builder(this@WorkoutSessionActivity)
                        .setTitle("Session Complete")
                        .setMessage("Nice work!\nCoins earned: $coinsEarned")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .show()
                } else {
                    AlertDialog.Builder(this@WorkoutSessionActivity)
                        .setTitle("Session Ended")
                        .setMessage("You ended the session early.\nNo rewards were given.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .show()
                }
            }
        }
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

    /* --------- Monster frame helpers (single ImageView) --------- */

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
        setMonsterFrame("idle")
    }

    private fun playAttackThen(onDone: () -> Unit) {
        // swap to attack frame if you have it, otherwise it keeps current
        setMonsterFrame("attack")

        // quick tween on single ImageView to suggest impact
        val sx = ObjectAnimator.ofFloat(ivMonster, "scaleX", 1f, 1.1f, 1f)
        val sy = ObjectAnimator.ofFloat(ivMonster, "scaleY", 1f, 1.1f, 1f)
        val shake = ObjectAnimator.ofFloat(ivMonster, "translationX", 0f, -8f, 8f, -6f, 6f, 0f)
        AnimatorSet().apply {
            duration = ATTACK_ANIM_MS
            playTogether(sx, sy, shake)
            start()
        }

        ivMonster.postDelayed({
            showIdle()
            onDone()
        }, ATTACK_ANIM_MS)
    }

    private fun showSleep() { /* optional later */ }

    /* --------- Rest dialog --------- */

    private fun showRestDialog(totalSec: Int) {
        setResting(true)

        val view = layoutInflater.inflate(R.layout.dialog_rest_timer, null)
        val tvCountdown = view.findViewById<TextView>(R.id.tv_rest_countdown)
        val progress = view.findViewById<ProgressBar>(R.id.rest_progress)
        val btnSkip = view.findViewById<Button>(R.id.btn_skip_rest)

        tvCountdown.text = format(totalSec)
        progress.max = 100
        progress.progress = 100

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        var remaining = totalSec
        restTimer?.cancel()
        restTimer = object : CountDownTimer(totalSec * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remaining -= 1
                tvCountdown.text = format(remaining)
                val pct = ((remaining / totalSec.toFloat()) * 100f).roundToInt()
                val start = progress.progress
                val end = pct.coerceIn(0, 100)
                ValueAnimator.ofInt(start, end).apply {
                    duration = 300
                    addUpdateListener { a -> progress.progress = a.animatedValue as Int }
                    start()
                }
            }

            override fun onFinish() {
                dlg.dismiss()
                setResting(false)
                showIdle()
                nextSetOrExercise()
            }
        }.start()

        btnSkip.setOnClickListener {
            restTimer?.cancel()
            dlg.dismiss()
            setResting(false)
            showIdle()
            nextSetOrExercise()
        }

        showSleep()
        dlg.show()
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
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun monsterMultiplierFor(code: String): Int = when (code) {
        "goblin"   -> 3
        "mushroom" -> 2
        else       -> 1 // slime/default
    }
}
