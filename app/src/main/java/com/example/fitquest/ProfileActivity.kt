package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.datastore.DataStoreManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.fitquest.database.UserSettings
import androidx.gridlayout.widget.GridLayout
import android.view.ViewGroup
import android.text.TextUtils
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
import com.example.fitquest.ui.widgets.SpriteSheetDrawable

/**
 * Merged ProfileActivity
 * - Keeps the repo UI/UX (sprites, animations, input validation, ImageButton save)
 * - Replaces SharedPreferences SettingsStore with Room-backed UserSettings
 * - Loads equipment choices from CSV in /assets (canonicalized), not hardcoded
 * - Adds fallbackToDestructiveMigration() for development safety
 *
 * NOTE: AppDatabase must expose userSettingsDao() and include UserSettings entity.
 */
class ProfileActivity : AppCompatActivity() {

    private var userId: Int = -1
    private lateinit var tvName: TextView
    private lateinit var tvAge: TextView
    private lateinit var tvSex: TextView
    private lateinit var spActivityLevel: Spinner
    private lateinit var spFitnessGoal: Spinner
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var btnSave: ImageButton
    private lateinit var db: AppDatabase
    private var loggedInUser: User? = null

    // sprites/animations from repo version
    private lateinit var spriteView: ImageView
    private lateinit var ladySpriteView: ImageView
    private var iconAnim: AnimationDrawable? = null
    private var ladyAnim: AnimationDrawable? = null

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var bgView: ImageView
    private var bgSprite: SpriteSheetDrawable? = null

    // --- VALIDATION CONSTANTS (repo) ---
    private val MIN_HEIGHT_CM = 120
    private val MAX_HEIGHT_CM = 230
    private val MIN_WEIGHT_KG = 30.0
    private val MAX_WEIGHT_KG = 300.0

    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bgView = findViewById(R.id.bg_anim)

        // Load your sheet without density scaling so frame math stays exact.
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_page_profile_spritesheet0, opts)


        bgSprite = SpriteSheetDrawable(
            sheet = sheet,
            rows = 1,
            cols = 12,
            fps = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        ).also { drawable ->
            bgView.setImageDrawable(drawable)
            drawable.start()
        }


        enterImmersive()

        pressAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.press)

        db = AppDatabase.getInstance(applicationContext)

        spriteView = findViewById(R.id.iv_profile_photo)
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvSex = findViewById(R.id.tv_sex)
        spActivityLevel = findViewById(R.id.sp_activity_level)
        spFitnessGoal = findViewById(R.id.sp_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        btnSave = findViewById(R.id.btn_save_profile)

        // NEW: open diary from Profile
        findViewById<ImageButton>(R.id.btn_diary)?.setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DiaryActivity::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showSettingsDialog()
        }

        // input constraints & live validation (repo)
        etHeight.filters = arrayOf(android.text.InputFilter.LengthFilter(3))
        etWeight.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        etHeight.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789")
        etWeight.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        etHeight.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) validateHeight() }
        etWeight.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) validateWeight() }
        etHeight.addTextChangedListener { validateHeight() }
        etWeight.addTextChangedListener { validateWeight() }

        val activityOptions = resources.getStringArray(R.array.activity_levels)
        val goalOptions = resources.getStringArray(R.array.fitness_goals)
        spActivityLevel.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, activityOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        spFitnessGoal.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, goalOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        // Default sprite while loading
        setSpriteForSex(null)

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ProfileActivity).first()
            if (userId != -1) {
                loggedInUser = db.userDAO().getUserById(userId)
                loggedInUser?.let { user ->
                    val profile = db.userProfileDAO().getProfileByUserId(userId)
                    withContext(Dispatchers.Main) {
                        tvName.text = "${user.firstName} ${user.lastName}"
                        tvAge.text = "Age: ${user.age}"
                        tvSex.text = "Sex: ${user.sex}"
                        setSpriteForSex(user.sex)
                        profile?.let {
                            etHeight.setText(it.height.toString())
                            etWeight.setText(it.weight.toString())
                            setSpinnerToValue(spActivityLevel, it.activityLevel)
                            setSpinnerToValue(spFitnessGoal, it.goal)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "No user found", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }

        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)

            // validate height/weight before saving
            if (!validateHeightWeight()) {
                Toast.makeText(this, "Please fix height/weight.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existing = db.userProfileDAO().getProfileByUserId(userId)
                val newHeight = etHeight.text.toString().toIntOrNull() ?: existing?.height ?: 0
                val newWeight = etWeight.text.toString().toIntOrNull() ?: existing?.weight ?: 0
                val newActivity = spActivityLevel.selectedItem?.toString()
                    ?: existing?.activityLevel ?: "Lightly Active"
                val newGoal = spFitnessGoal.selectedItem?.toString()
                    ?: existing?.goal ?: "Build Muscle"

                val updated = if (existing == null) {
                    UserProfile(
                        profileId = 0,
                        userId = userId,
                        height = newHeight,
                        weight = newWeight,
                        activityLevel = newActivity,
                        goal = newGoal,
                        equipment = null // managed in Settings via UserSettings
                    )
                } else {
                    existing.copy(
                        height = newHeight,
                        weight = newWeight,
                        activityLevel = newActivity,
                        goal = newGoal
                    )
                }

                if (existing == null) db.userProfileDAO().insert(updated)
                else db.userProfileDAO().update(updated)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setupNavigationBar()
    }

    // ---------- Repo sprite helpers ----------

    private fun setSpriteForSex(sexRaw: String?) {
        val resId = when (sexRaw?.trim()?.lowercase()) {
            "female", "f", "woman", "girl" -> R.drawable.user_icon_female_sprite
            "male", "m", "man", "boy"      -> R.drawable.user_icon_male_sprite
            else                                 -> R.drawable.user_icon_male_sprite
        }
        iconAnim?.stop()
        spriteView.setImageResource(resId)
        iconAnim = spriteView.drawable as? AnimationDrawable
        spriteView.post { iconAnim?.start() }
    }

    override fun onResume() {
        super.onResume()
        iconAnim?.start()
        bgSprite?.start()
    }

    override fun onPause() {
        bgSprite?.stop()
        iconAnim?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        // Optional: release ref to help GC
        bgView.setImageDrawable(null)
        bgSprite = null
        super.onDestroy()
    }


    // ---------- Validation (repo) ----------

    private fun validateHeight(): Boolean {
        val h = etHeight.text.toString().trim().toIntOrNull()
        return when {
            h == null -> { etHeight.error = "Enter height in cm"; false }
            h < MIN_HEIGHT_CM || h > MAX_HEIGHT_CM -> {
                etHeight.error = "Height must be $MIN_HEIGHT_CM–$MAX_HEIGHT_CM cm"; false
            }
            else -> { etHeight.error = null; true }
        }
    }

    private fun validateWeight(): Boolean {
        val w = etWeight.text.toString().trim().toDoubleOrNull()
        return when {
            w == null -> { etWeight.error = "Enter weight in kg"; false }
            w < MIN_WEIGHT_KG || w > MAX_WEIGHT_KG -> {
                etWeight.error = "Weight must be ${MIN_WEIGHT_KG.toInt()}–${MAX_WEIGHT_KG.toInt()} kg"; false
            }
            else -> { etWeight.error = null; true }
        }
    }

    private fun validateHeightWeight(): Boolean {
        val okH = validateHeight()
        val okW = validateWeight()
        return okH && okW
    }

    private fun setSpinnerToValue(spinner: Spinner, value: String?) {
        if (value.isNullOrBlank()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val itemText = adapter.getItem(i)?.toString() ?: continue
            if (itemText.equals(value, ignoreCase = true)) { spinner.setSelection(i); return }
        }
    }

    private fun checkboxTint(): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            Color.parseColor("#10B981"), // checked
            Color.parseColor("#9CA3AF")  // unchecked
        )
        return ColorStateList(states, colors)
    }

    // ---------- Settings dialog (Room-backed) ----------

    private fun showSettingsDialog() {
        if (userId == -1) {
            Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show()
            return
        }

        // exit immersive while dialog is shown (touch alignment)
        exitImmersive()

        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val tilTimer = view.findViewById<TextInputLayout>(R.id.til_timer)
        val etTimer = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_timer)
        val eqContainer = view.findViewById<GridLayout>(R.id.equipment_container)


        tilTimer.setDefaultHintTextColor(
            ContextCompat.getColorStateList(this, R.color.hint_label)!!
        )

        val checks = mutableListOf<CheckBox>()

        // preload timer
        lifecycleScope.launch {
            val existing = db.userSettingsDao().getByUserId(userId)
            withContext(Dispatchers.Main) { etTimer.setText(formatDuration(existing?.restTimerSec ?: 180)) }
        }

        // build equipment list from CSV (canonicalized)
        lifecycleScope.launch {
            val canonNames = loadEquipmentNamesFromCsv()
            val existing = db.userSettingsDao().getByUserId(userId)
            val selectedCanon = existing?.equipmentCsv
                ?.split('|')
                ?.filter { it.isNotBlank() }
                ?.toSet() ?: emptySet()

            withContext(Dispatchers.Main) {
                canonNames.forEach { canon ->
                    val label = displayLabel(canon)

                    val lp = GridLayout.LayoutParams().apply {
                        width = 0                                   // stretch evenly per column (weight)
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)  // weight = 1
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }

                    val cb = CheckBox(this@ProfileActivity).apply {
                        text = label
                        tag = canon
                        setTextColor(0xFF111827.toInt())
                        isChecked = canon in selectedCanon
                        CompoundButtonCompat.setButtonTintList(this, checkboxTint())
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = lp
                    }
                    checks += cb
                    eqContainer.addView(cb)
                }
            }
        }

        view.setPadding(20, 80, 20, 10)

        val dialog = MaterialAlertDialogBuilder(this /* , R.style.FitQuestSettingsImageDialog (optional overlay) */)
            .setView(view)         // no setPositive/Negative/NeutralButton here
            .create()



        dialog.setOnShowListener {
            // If you’re using a custom background image for the whole dialog:
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

            // wire up image buttons
            val btnSave   = view.findViewById<ImageButton>(R.id.btn_save)
            val btnCancel = view.findViewById<ImageButton>(R.id.btn_cancel)
            val btnLogout = view.findViewById<ImageButton>(R.id.btn_logout)

            btnSave.setOnClickListener {
                val seconds = parseDurationToSeconds(etTimer.text?.toString()?.trim() ?: "")
                if (seconds == null || seconds < 15 || seconds > 20 * 60) {
                    Toast.makeText(this, "Please enter 15s to 20m (e.g., 180 or 3:00).", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val chosenCanon = checks.filter { it.isChecked }.map { it.tag as String }
                lifecycleScope.launch {
                    db.userSettingsDao().upsert(
                        UserSettings(
                            userId = userId,
                            restTimerSec = seconds,
                            equipmentCsv = chosenCanon.joinToString("|")
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnLogout.setOnClickListener {
                lifecycleScope.launch {
                    DataStoreManager.clearUserId(this@ProfileActivity)
                    startActivity(
                        Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    finishAffinity()
                }
            }
        }

        dialog.setOnDismissListener { enterImmersive() }
        dialog.show()

    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_workout).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, WorkoutActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_shop).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ShopActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_dashboard).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DashboardActivity::class.java)); overridePendingTransition(0, 0)
        }
    }

    // ---------- helpers ----------

    private fun formatDuration(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }

    private fun parseDurationToSeconds(text: String): Int? {
        if (text.isBlank()) return null
        return if (text.contains(":")) {
            val parts = text.split(":")
            if (parts.size != 2) return null
            val m = parts[0].toIntOrNull() ?: return null
            val s = parts[1].toIntOrNull() ?: return null
            if (s in 0..59 && m >= 0) m * 60 + s else null
        } else {
            text.toIntOrNull()
        }
    }

    /** Normalize raw equipment name to a canonical (lowercase, singular) key. */
    private fun canonicalizeEquipment(raw: String): String {
        val s = raw.trim()
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .lowercase()
        return when (s) {
            // plurals → singular
            "dumbbells" -> "dumbbell"
            "kettlebells" -> "kettlebell"
            "resistance bands" -> "resistance band"
            "bands" -> "band"
            "battle rope" -> "battle ropes"
            // common typos / aliases
            "barbel" -> "barbell"
            else -> s
        }
    }

    /** Turn a canonical key into a nice label for UI. */
    private fun displayLabel(canon: String): String = when (canon) {
        "trx" -> "TRX"
        else -> canon.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
    }

    /** Load DISTINCT canonical equipment names from CSV in /assets. */
    private suspend fun loadEquipmentNamesFromCsv(): List<String> = withContext(Dispatchers.IO) {
        val path = "exercises.csv"
        runCatching {
            assets.open(path).bufferedReader().use { br ->
                val splitter = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
                val header = br.readLine() ?: return@use emptyList<String>()
                val cols = header.split(splitter).map { it.trim().trim('\"').lowercase() }
                val eqIdx = when {
                    "equipment" in cols -> cols.indexOf("equipment")
                    "required_equipment" in cols -> cols.indexOf("required_equipment")
                    else -> return@use emptyList<String>()
                }
                br.lineSequence()
                    .map { it.split(splitter) }
                    .mapNotNull { row -> row.getOrNull(eqIdx)?.trim()?.trim('\"') }
                    .filter { it.isNotBlank() }
                    .flatMap { cell -> cell.split('|', '/', ';').map { it.trim() } }
                    .map { canonicalizeEquipment(it) }
                    .filterNot { it == "bodyweight" }
                    .toSet()
                    .toList()
                    .sorted()
            }
        }.getOrElse {
            listOf("bench","barbell","dumbbell","kettlebell","pull-up bar","resistance band","cable")
        }
    }

    /** Enter immersive (hide system bars). */
    private fun enterImmersive() {
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

    /** Exit immersive so dialogs layout/touch correctly. */
    private fun exitImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
