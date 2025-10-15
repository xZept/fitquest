package com.example.fitquest

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.ActiveQuest
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.QuestExercise
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import com.example.fitquest.workout.gitWorkoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import com.example.fitquest.cosmetics.BgCosmetics         // <-- NEW
import com.example.fitquest.shop.ShopRepository          // <-- NEW
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.getkeepsafe.taptargetview.TapTargetView
import androidx.core.content.ContextCompat

class QuestGeneratorActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private lateinit var spSplit: Spinner
    private lateinit var spFocus: Spinner

    private var lastSplit = ""
    private var lastFocus = ""
    private var lastInitialNames: List<String> = emptyList()
    private var lastAddableNames: List<String> = emptyList()
    private var bgSprite: SpriteSheetDrawable? = null
    private var bgBitmap: Bitmap? = null

    private var bubbleJob: Job? = null

    private val welcomeMsg = "Choose your quest, adventurer! Pick a split and a focus, then forge your path."
    private val splitHelp  = "How your training days are grouped.\n• Push/Pull/Legs → muscles by movement\n• Upper/Legs →  body regions."
    private val focusHelp  = "• General → balanced\n• Hypertrophy → ~8–12 reps for size\n• Strength → lower reps, heavier loads"

    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode != RESULT_OK || res.data == null) return@registerForActivityResult
        val names = res.data!!.getStringArrayListExtra("RESULT_NAMES") ?: arrayListOf()
        val split = res.data!!.getStringExtra("SPLIT") ?: "Push"
        val focus = res.data!!.getStringExtra("FOCUS") ?: "General"

        if (names.isEmpty()) {
            Toast.makeText(this, "Nothing selected.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestGeneratorActivity).first()
            if (uid == -1) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuestGeneratorActivity, "No user session.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val (minRep, maxRep, sets) = gitWorkoutEngine.defaultScheme(focus)

            val finalList: List<QuestExercise> = names
                .distinctBy { norm(it) }
                .mapIndexed { index, n ->
                    QuestExercise(name = n, sets = sets, repsMin = minRep, repsMax = maxRep, order = index)
                }

            db.activeQuestDao().upsert(
                ActiveQuest(userId = uid, split = split, modifier = focus, exercises = finalList, startedAt = null)
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(this@QuestGeneratorActivity, "Quest saved!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@QuestGeneratorActivity, WorkoutActivity::class.java).apply {
                    putExtra("SPLIT", split); putExtra("FOCUS", focus)
                })
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_generator)

        val root = findViewById<View>(R.id.root)

        // --- BACKGROUND COSMETIC: default tier 0, then upgrade once we know user
        bgSprite = BgCosmetics.buildDrawable(this, BgCosmetics.Page.QUEST, 0)
        root.background = bgSprite
        bgSprite?.start()

        // after userId is known, upgrade if possible
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestGeneratorActivity).first()
            val tier = withContext(Dispatchers.IO) {
                val repo = ShopRepository(AppDatabase.getInstance(applicationContext))
                BgCosmetics.highestOwnedTier(uid, repo, BgCosmetics.Page.QUEST)
            }
            if (tier > 0) {
                val up = BgCosmetics.buildDrawable(this@QuestGeneratorActivity, BgCosmetics.Page.QUEST, tier)
                root.background = up
                bgSprite?.stop()
                bgSprite = up
                up.start()
            }
        }

        showBubble(welcomeMsg)

        val welcome = findViewById<View>(R.id.welcome_bubble)
        welcome.alpha = 0f
        welcome.visibility = View.VISIBLE
        welcome.animate().alpha(1f).setDuration(250).start()

        lifecycleScope.launch {
            delay(6000)
            welcome.animate().alpha(0f).setDuration(250).withEndAction { welcome.visibility = View.GONE }.start()
        }

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        spSplit = findViewById(R.id.sp_split)
        spFocus = findViewById(R.id.sp_focus)

        var splitTouched = false
        spSplit.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) { splitTouched = true; showBubble(splitHelp) }
            false
        }

        var focusTouched = false
        spFocus.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) { focusTouched = true; showBubble(focusHelp) }
            false
        }

        val splits: Array<String> = try { resources.getStringArray(R.array.splits) }
        catch (_: Exception) { arrayOf("Push", "Pull", "Legs", "Upper", "Lower", "Full Body") }

        val focuses: Array<String> = try { resources.getStringArray(R.array.focuses) }
        catch (_: Exception) { arrayOf("General", "Hypertrophy", "Strength") }

        spSplit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, splits)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spFocus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, focuses)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<ImageButton>(R.id.btn_generate).setOnClickListener {
            it.startAnimation(pressAnim); generateAndOpenPreview()
        }
        findViewById<ImageButton?>(R.id.btn_cancel)?.setOnClickListener {
            it.startAnimation(pressAnim); finish(); overridePendingTransition(0, 0)
        }

    }

    override fun onStart() { super.onStart(); bgSprite?.start() }
    override fun onStop() { bgSprite?.stop(); super.onStop() }
    override fun onDestroy() { bgSprite?.stop(); bgBitmap = null; bgSprite = null; super.onDestroy() }

    private fun showBubble(text: String, autoHideMs: Long = 6000L) {
        val bubble = findViewById<View>(R.id.welcome_bubble)
        val tv = findViewById<TextView>(R.id.tv_welcome)
        bubbleJob?.cancel(); bubble.animate().cancel()
        tv.text = text; bubble.alpha = 0f; bubble.visibility = View.VISIBLE
        bubble.animate().alpha(1f).setDuration(200).start()
        bubbleJob = lifecycleScope.launch {
            delay(autoHideMs)
            bubble.animate().alpha(0f).setDuration(200).withEndAction { bubble.visibility = View.GONE }.start()
        }
    }

    /** Build one structured plan and go straight to preview (no Basic/Advanced dialog). */
    private fun generateAndOpenPreview() {
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestGeneratorActivity).first()
            if (uid == -1) {
                Toast.makeText(this@QuestGeneratorActivity, "No user session.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val split = spSplit.selectedItem?.toString()
                ?: (spSplit.adapter?.getItem(0)?.toString() ?: "Push")
            val focus = spFocus.selectedItem?.toString()
                ?: (spFocus.adapter?.getItem(0)?.toString() ?: "General")

            val settings = db.userSettingsDao().getByUserId(uid)
            val ownedEquip: Set<String> = settings?.equipmentCsv
                ?.split('|')?.mapNotNull { it.trim().lowercase().ifEmpty { null } }?.toSet()
                ?: emptySet()

            val targetItems = computeTargetItems(split)

            val planResult = gitWorkoutEngine.buildStructuredPlan(
                context = this@QuestGeneratorActivity,
                split = split,
                focus = focus,
                ownedEquipCanonical = ownedEquip,
                targetItems = targetItems
            )
            val initialList = planResult.first
            val addablePool = planResult.second

            if (initialList.isEmpty() && addablePool.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@QuestGeneratorActivity,
                        "No matching exercises. Check your equipment in Profile > Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            lastSplit = split
            lastFocus = focus
            lastInitialNames = initialList.map { it.name }.distinctBy { norm(it) }
            lastAddableNames = addablePool.distinctBy { norm(it) }

            val intent = Intent(this@QuestGeneratorActivity, QuestPreviewActivity::class.java).apply {
                putExtra("MODE", "advanced")
                putExtra("SPLIT", split)
                putExtra("FOCUS", focus)
                putStringArrayListExtra("START_NAMES", ArrayList(lastInitialNames))
                putStringArrayListExtra("ADDABLE_NAMES", ArrayList(lastAddableNames))
            }
            withContext(Dispatchers.Main) { previewLauncher.launch(intent); overridePendingTransition(0, 0) }
        }
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
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    override fun onResume() {
        super.onResume()
        bgSprite?.start()

        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestGeneratorActivity).first()
            if (uid > 0) showQuestTourIfNeeded(uid)
        }
    }


    private fun norm(name: String) =
        name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Split-aware target list length to align with 1-per-pattern (+ finisher). */
    private fun computeTargetItems(split: String): Int {
        val s = split.trim().lowercase()
        return when {
            s.contains("push") -> 5
            s.contains("pull") -> 5
            s.contains("leg") || s.contains("lower") -> 4
            s.contains("upper") -> 5
            s.contains("full") && s.contains("body") -> 5
            else -> 5
        }
    }
    companion object {
        private const val TOUR_PREFS = "onboarding"
        private const val QUEST_TOUR_DONE_KEY_PREFIX = "quest_tour_done_v1_u_" // per-user key
        private const val FORCE_TOUR = false                                   // set true to test a user
        private val questTourShownUsersThisProcess = mutableSetOf<Int>()        // per-process guard (per user)
    }


    private fun TapTarget.applyQuestTourStyle(): TapTarget = apply {
        // Scrim/background color — pass a *resource*, not an ARGB int here
        dimColor(R.color.tour_white_80)      // 80% white in colors.xml (#CCFFFFFF)

        // Make BOTH texts the same bright color
        titleTextColor(R.color.tour_orange)   // or android.R.color.black
        descriptionTextColor(R.color.tour_orange)

        // Ring/target styling
        outerCircleColor(R.color.white) // subtle ring, then use alpha below
        outerCircleAlpha(0.12f)              // keep ring faint over light scrim
        targetCircleColor(R.color.white)

        tintTarget(true)
        transparentTarget(true)
        cancelable(true)
        drawShadow(false)
    }

    private fun showQuestTourIfNeeded(userId: Int) {
        if (userId <= 0) return

        val prefs = getSharedPreferences(TOUR_PREFS, MODE_PRIVATE)
        val userDoneKey = "$QUEST_TOUR_DONE_KEY_PREFIX$userId"

        // DEV ONLY: force-show while testing a specific user
        if (FORCE_TOUR && BuildConfig.DEBUG) {
            prefs.edit().remove(userDoneKey).apply()
            questTourShownUsersThisProcess.remove(userId)
        }

        // Guard: once per process (per user) + once per install (per user)
        if (questTourShownUsersThisProcess.contains(userId) || prefs.getBoolean(userDoneKey, false)) return
        questTourShownUsersThisProcess.add(userId)

        val split  = findViewById<View>(R.id.sp_split)
        val focus  = findViewById<View>(R.id.sp_focus)
        val forge  = findViewById<View>(R.id.btn_generate)
        val cancel = findViewById<View>(R.id.btn_cancel)
        val root   = findViewById<View>(R.id.root)

        // Start after layout to avoid zero-size targets
        root.post {
            val targets = buildList {
                split?.let  { add(TapTarget.forView(it, "Choose a Split: Group days by movement (Push/Pull/Legs), region (Upper/Lower), or Full Body.", "").applyQuestTourStyle()) }
                focus?.let  { add(TapTarget.forView(it, "Pick a Focus: General = balanced • Hypertrophy = ~8–12 reps • Strength = lower reps/heavier loads.", "").applyQuestTourStyle()) }
                forge?.let  { add(TapTarget.forView(it, "Build a plan and review exercises before starting.", "").applyQuestTourStyle()) }
                cancel?.let { add(TapTarget.forView(it, "Back out without creating a quest.", "").applyQuestTourStyle()) }
            }

            if (targets.isEmpty()) {
                prefs.edit().putBoolean(userDoneKey, true).apply()
                return@post
            }

            TapTargetSequence(this@QuestGeneratorActivity)
                .targets(targets)
                .listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        prefs.edit().putBoolean(userDoneKey, true).apply()
                    }
                    override fun onSequenceCanceled(lastTarget: TapTarget) {
                        prefs.edit().putBoolean(userDoneKey, true).apply()
                    }
                    override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                })
                .start()
        }
    }

}
