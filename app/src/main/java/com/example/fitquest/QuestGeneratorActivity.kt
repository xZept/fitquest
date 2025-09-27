package com.example.fitquest

import android.app.AlertDialog
import android.content.Intent
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
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.models.QuestExercise
import com.example.fitquest.workout.WorkoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.fitquest.database.ActiveQuest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.fitquest.ui.widgets.SpriteSheetDrawable


class QuestGeneratorActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private lateinit var spSplit: Spinner
    private lateinit var spFocus: Spinner

    // Cached so Back from preview can re-open immediately if needed in future
    private var lastSplit = ""
    private var lastFocus = ""
    private var lastInitialNames: List<String> = emptyList()
    private var lastAddableNames: List<String> = emptyList()
    private var bgSprite: SpriteSheetDrawable? = null
    private var bgBitmap: Bitmap? = null


    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        // Saving from preview
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

            val (minRep, maxRep, sets) = WorkoutEngine.defaultScheme(focus)

            val finalList: List<QuestExercise> = names
                .distinctBy { norm(it) }
                .mapIndexed { index, n ->
                    QuestExercise(name = n, sets = sets, repsMin = minRep, repsMax = maxRep, order = index)
                }

            db.activeQuestDao().upsert(
                ActiveQuest(
                    userId = uid,
                    split = split,
                    modifier = focus, // DB field name is 'modifier'
                    exercises = finalList,
                    startedAt = null
                )
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(this@QuestGeneratorActivity, "Quest saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_generator)

        val root = findViewById<View>(R.id.root)

        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        bgBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_dashboard_spritesheet, opts)

        val rows = 1
        val cols = 12
        val fps  = 12

        bgSprite = SpriteSheetDrawable(
            sheet = requireNotNull(bgBitmap),
            rows = rows,
            cols = cols,
            fps = fps,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP // fills screen nicely
        )

        root.background = bgSprite


        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        spSplit = findViewById(R.id.sp_split)
        spFocus = findViewById(R.id.sp_focus)

        val splits: Array<String> = try { resources.getStringArray(R.array.splits) }
        catch (_: Exception) { arrayOf("Push", "Pull", "Legs", "Upper", "Lower", "Full Body") }

        val focuses: Array<String> = try { resources.getStringArray(R.array.focuses) }
        catch (_: Exception) { arrayOf("General", "Hypertrophy", "Strength") }

        spSplit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, splits)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spFocus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, focuses)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<ImageButton>(R.id.btn_generate).setOnClickListener {
            it.startAnimation(pressAnim)
            generateAndOpenPreview()
        }
        findViewById<ImageButton?>(R.id.btn_cancel)?.setOnClickListener {
            it.startAnimation(pressAnim)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onStart() {
        super.onStart()
        bgSprite?.start()
    }

    override fun onStop() {
        bgSprite?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        bgSprite?.stop()
        // Optional: if you’re certain this bitmap isn’t reused elsewhere:
        // bgBitmap?.recycle()
        bgBitmap = null
        bgSprite = null
        super.onDestroy()
    }


    /** Build one structured plan and go straight to preview (no Basic/Advanced dialog). */
    private fun generateAndOpenPreview() {
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestGeneratorActivity).first()
            if (uid == -1) {
                Toast.makeText(this@QuestGeneratorActivity, "No user session.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Pull selections with safe fallbacks
            val split = spSplit.selectedItem?.toString()
                ?: (spSplit.adapter?.getItem(0)?.toString() ?: "Push")
            val focus = spFocus.selectedItem?.toString()
                ?: (spFocus.adapter?.getItem(0)?.toString() ?: "General")

            // User equipment
            val settings = db.userSettingsDao().getByUserId(uid)
            val ownedEquip: Set<String> = settings?.equipmentCsv
                ?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: emptySet()

            // Build structured plan
            val planResult = WorkoutEngine.buildStructuredPlan(
                context = this@QuestGeneratorActivity,
                split = split,
                focus = focus,
                ownedEquipCanonical = ownedEquip,
                targetItems = 8
            )
            val initialList = planResult.first     // <-- no destructuring – fixes your error
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

            // Jump straight to preview
            val intent = Intent(this@QuestGeneratorActivity, QuestPreviewActivity::class.java).apply {
                putExtra("MODE", "advanced") // naming kept for compatibility
                putExtra("SPLIT", split)
                putExtra("FOCUS", focus)
                putStringArrayListExtra("START_NAMES", ArrayList(lastInitialNames))
                putStringArrayListExtra("ADDABLE_NAMES", ArrayList(lastAddableNames))
            }
            withContext(Dispatchers.Main) {
                previewLauncher.launch(intent)
                overridePendingTransition(0, 0)
            }

        }
    }

    // --- utils ---

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

    private fun norm(name: String) =
        name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
}
