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
import com.example.fitquest.repository.FitquestRepository
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import android.view.MotionEvent
import android.text.method.DigitsKeyListener
import com.example.fitquest.shop.ShopRepository
import com.example.fitquest.database.WeightLog


/**
 * ProfileActivity with Edit Ticket gating:
 * - Locked fields by default.
 * - Unlock requires owning an Edit Profile Ticket (consumed on Save).
 * - If no ticket, redirect to Shop Items tab.
 */

class ProfileActivity : AppCompatActivity() {

    // ----- New gating state -----
    private var editingUnlocked = false
    private var btnUnlock: ImageButton? = null
    private lateinit var repoShop: ShopRepository

    companion object {
        private const val EDIT_TICKET_CODE = "edit_profile_ticket"
        // mirror ShopActivity extras to avoid hard coupling
        private const val EXTRA_SHOP_TAB = "shop_tab"
        private const val TAB_ITEMS = "items"
    }

    // Declare repo field
    private lateinit var repository: FitquestRepository

    private var userId: Int = -1
    private lateinit var tvName: TextView
    private lateinit var tvAge: TextView
    private lateinit var tvSex: TextView
    private lateinit var spActivityLevel: Spinner
    private lateinit var spFitnessGoal: Spinner
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var etGoalWeight: EditText
    private lateinit var btnSave: ImageButton
    private lateinit var db: AppDatabase

    private var loggedInUser: User? = null

    private lateinit var tvBmiValue: TextView
    private var goalWeightEditedManually = false

    // sprites/animations from repo version
    private lateinit var spriteView: ImageView
    private var iconAnim: AnimationDrawable? = null

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var bgView: ImageView
    private var bgSprite: SpriteSheetDrawable? = null

    // --- VALIDATION CONSTANTS (repo) ---
    private val MIN_HEIGHT_CM = 120
    private val MAX_HEIGHT_CM = 230
    private val MIN_WEIGHT_KG = 30.0
    private val MAX_WEIGHT_KG = 300.0

    private lateinit var tvSettingsHint: TextView
    private var hintHideJob: kotlinx.coroutines.Job? = null

    private val HINT_DEFAULT  = "Manage rest timer and equipment in Settings"
    private val HINT_HEIGHT   = "Height: enter 120–230 cm. Digits only."
    private val HINT_WEIGHT   = "Weight: enter 30–300 kg. Decimals OK (e.g., 72.5)."
    private val HINT_ACTIVITY = "Activity level: pick how active you are on non-workout days. It affects calorie estimates."
    private val HINT_GOAL     = "Fitness goal: choose fat loss, maintenance, or muscle gain to shape your plan."

    // Spinner interaction guards
    private var activitySpinnerTouched = false
    private var goalSpinnerTouched = false
    private var suppressHint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bgView = findViewById(R.id.bg_anim)
        tvSettingsHint = findViewById(R.id.tv_settings_hint)
        showHint(HINT_DEFAULT)

        // Load BG sheet
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_page_profile_spritesheet0, opts)

        // Initialize repos/db
        repository = FitquestRepository(this)
        db = AppDatabase.getInstance(applicationContext)
        repoShop = ShopRepository(db)

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

        // Views
        spriteView = findViewById(R.id.iv_profile_photo)
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvSex = findViewById(R.id.tv_sex)
        spActivityLevel = findViewById(R.id.sp_activity_level)
        spFitnessGoal = findViewById(R.id.sp_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        etGoalWeight = findViewById(R.id.et_goal_weight)
        btnSave = findViewById(R.id.btn_save_profile)
        btnUnlock = findViewById(R.id.btn_unlock_edit) // optional; safe if null
        tvBmiValue = findViewById(R.id.tv_bmi_value)

        // Lock UI initially
        unlockEditing(false)

        etGoalWeight.addTextChangedListener {
            if (etGoalWeight.hasFocus()) goalWeightEditedManually = true
        }

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

        // input constraints & live validation (NO keyListener here; unlocked later)
        etHeight.filters = arrayOf(android.text.InputFilter.LengthFilter(3))
        etWeight.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        etHeight.addTextChangedListener {
            if (!suppressHint && etHeight.hasFocus()) showHint(HINT_HEIGHT)
            validateHeight()
            updateBmi()
        }
        etWeight.addTextChangedListener {
            if (!suppressHint && etWeight.hasFocus()) showHint(HINT_WEIGHT)
            validateWeight()
            updateBmi()
        }

        etHeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showHint(HINT_HEIGHT) else validateHeight()
        }
        etWeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showHint(HINT_WEIGHT) else validateWeight()
        }
        etHeight.setOnClickListener { showHint(HINT_HEIGHT) }
        etWeight.setOnClickListener { showHint(HINT_WEIGHT) }

        // ---- Activity Level spinner ----
        spActivityLevel.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN && !suppressHint) showHint(HINT_ACTIVITY)
            activitySpinnerTouched = true
            false
        }
        spActivityLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (!suppressHint && activitySpinnerTouched) showHint(HINT_ACTIVITY)
                activitySpinnerTouched = false
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        spActivityLevel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !suppressHint) showHint(HINT_ACTIVITY)
        }

        // ---- Fitness Goal spinner ----
        spFitnessGoal.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN && !suppressHint) showHint(HINT_GOAL)
            goalSpinnerTouched = true
            false
        }
        spFitnessGoal.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (!suppressHint && goalSpinnerTouched) showHint(HINT_GOAL)
                goalSpinnerTouched = false
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        spFitnessGoal.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !suppressHint) showHint(HINT_GOAL)
        }

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

            val (user, profile) = withContext(Dispatchers.IO) {
                db.userDAO().getUserById(userId) to db.userProfileDAO().getProfileByUserId(userId)
            }

            withContext(Dispatchers.Main) {
                if (user == null) {
                    Toast.makeText(this@ProfileActivity, "No user found", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                    finish()
                    return@withContext
                }
                loggedInUser = user
                tvName.text = "${user.firstName} ${user.lastName}"
                tvAge.text = "Age: ${user.age}"
                tvSex.text = "Sex: ${user.sex}"
                setSpriteForSex(user.sex)

                suppressHint = true
                etGoalWeight.setText(profile?.goalWeight?.toString().orEmpty())
                profile?.let {
                    etHeight.setText(it.height.toString())
                    etWeight.setText(it.weight.toString())
                    updateBmi()
                    setSpinnerToValue(spActivityLevel, it.activityLevel)
                    setSpinnerToValue(spFitnessGoal, it.goal)
                }
                suppressHint = false
                showHint(HINT_DEFAULT)
            }
        }

        // Optional Unlock button (if present in layout)
        btnUnlock?.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val have = withContext(Dispatchers.IO) { repoShop.getItemQuantity(userId, EDIT_TICKET_CODE) }
                if (have <= 0) {
                    // Inflate the custom layout that uses container_handler.png as bg
                    val view = layoutInflater.inflate(R.layout.dialog_edit_locked, null)
                    view.findViewById<TextView>(R.id.tv_title).text = "Edit Ticket required"
                    view.findViewById<TextView>(R.id.tv_message).text =
                        "Buy an Edit Profile Ticket in the Shop to unlock editing."

                    val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ProfileActivity)
                        .setView(view)
                        .create()

                    // Transparent window so the image shows edge-to-edge
                    dlg.setOnShowListener { dlg.window?.setBackgroundDrawableResource(android.R.color.transparent) }

                    // ImageButtons: button_open_shop.png and buttons_cancel.png
                    view.findViewById<ImageButton>(R.id.btn_open_shop).setOnClickListener {
                        startActivity(Intent(this@ProfileActivity, ShopActivity::class.java).apply {
                            putExtra(EXTRA_SHOP_TAB, TAB_ITEMS)
                        })
                        overridePendingTransition(0, 0)
                        dlg.dismiss()
                    }
                    view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener {
                        dlg.dismiss()
                    }

                    dlg.show()
                } else {
                    unlockEditing(true)
                    Toast.makeText(
                        this@ProfileActivity,
                        "Editing unlocked. Ticket will be used when you save.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)

            lifecycleScope.launch {
                // If not unlocked yet, handle gating here
                if (!editingUnlocked) {
                    val have = withContext(Dispatchers.IO) { repoShop.getItemQuantity(userId, EDIT_TICKET_CODE) }

                    if (have <= 0) {
                        val view = layoutInflater.inflate(R.layout.dialog_edit_locked, null)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ProfileActivity)
                            .setView(view)     // uses the ImageButtons from the custom layout
                            .create()

                        dlg.setOnShowListener {
                            dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        }

                        // Wire the ImageButtons
                        view.findViewById<ImageButton>(R.id.btn_open_shop).setOnClickListener {
                            startActivity(Intent(this@ProfileActivity, ShopActivity::class.java).apply {
                                putExtra(EXTRA_SHOP_TAB, TAB_ITEMS)
                            })
                            overridePendingTransition(0, 0)
                            dlg.dismiss()
                        }
                        view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener {
                            dlg.dismiss()
                        }

                        dlg.show()
                        return@launch
                    }
                    else {
                        val view = layoutInflater.inflate(R.layout.dialog_use_ticket, null)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ProfileActivity)
                            .setView(view)
                            .create()

                        dlg.setOnShowListener {
                            dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        }

                        // Wire image buttons
                        view.findViewById<ImageButton>(R.id.btn_use_ticket).setOnClickListener {
                            unlockEditing(true)
                            Toast.makeText(
                                this@ProfileActivity,
                                "Editing unlocked. Make your changes and press Save.",
                                Toast.LENGTH_LONG
                            ).show()
                            dlg.dismiss()
                        }
                        view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener {
                            dlg.dismiss()
                        }

                        dlg.show()
                        return@launch
                    }

                }

                // validate height/weight before saving (once unlocked)
                if (!validateHeightWeight()) {
                    Toast.makeText(this@ProfileActivity, "Please fix height/weight.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Consume a ticket now (must have been unlocked)
                val consumed = withContext(Dispatchers.IO) { repoShop.consumeItem(userId, EDIT_TICKET_CODE, 1) }
                if (!consumed) {
                    Toast.makeText(this@ProfileActivity, "No Edit Ticket found.", Toast.LENGTH_SHORT).show()
                    unlockEditing(false)
                    return@launch
                }

                val plan = withContext(Dispatchers.IO) {
                    val existing = db.userProfileDAO().getProfileByUserId(userId)
                    val prevWeightInt = existing?.weight

                    // Use the decimal the user typed for logging; store rounded in profile (matches schema)
                    val typedWeightDouble = etWeight.text.toString().toDoubleOrNull()
                    val newWeightInt = typedWeightDouble?.roundToInt()
                        ?: existing?.weight
                        ?: 0

                    val updated = (existing?.copy(
                        height = etHeight.text.toString().toIntOrNull() ?: existing.height,
                        weight = newWeightInt,
                        goalWeight = etGoalWeight.text.toString().toDoubleOrNull()?.roundToInt(),
                        activityLevel = spActivityLevel.selectedItem?.toString(),
                        goal = spFitnessGoal.selectedItem?.toString()
                    ) ?: UserProfile(
                        profileId = 0, userId = userId,
                        height = etHeight.text.toString().toInt(),
                        weight = newWeightInt,
                        goalWeight = etGoalWeight.text.toString().toDoubleOrNull()?.roundToInt(),
                        activityLevel = spActivityLevel.selectedItem?.toString(),
                        goal = spFitnessGoal.selectedItem?.toString(),
                        equipment = null
                    ))

                    if (existing == null) {
                        db.userProfileDAO().insert(updated)
                    } else {
                        db.userProfileDAO().update(updated)
                    }

                    // ✨ NEW: write to weight history when ticket changes the weight
                    // Only log if the weight actually changed OR there was no previous value.
                    if (prevWeightInt == null || prevWeightInt != newWeightInt) {
                        val weightForLog = (typedWeightDouble?.toFloat()) ?: newWeightInt.toFloat()
                        db.weightLogDao().insert(
                            WeightLog(
                                userId = userId,
                                loggedAt = System.currentTimeMillis(),
                                weightKg = weightForLog
                            )
                        )
                    }

                    // Recompute macros as you already do
                    repository.computeAndSaveMacroPlan(userId)
                }

                unlockEditing(false) // lock again after success

                Toast.makeText(this@ProfileActivity,
                    "Macros updated: ${plan.calories} kcal | P ${plan.protein}g • F ${plan.fat}g • C ${plan.carbs}g",
                    Toast.LENGTH_LONG
                ).show()
                Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
            }
        }

        setupNavigationBar()
    }

    // ---- Gating toggle ----
    private fun unlockEditing(unlock: Boolean) {
        editingUnlocked = unlock

        // Spinners
        spActivityLevel.isEnabled = unlock
        spActivityLevel.isClickable = unlock
        spFitnessGoal.isEnabled = unlock
        spFitnessGoal.isClickable = unlock

        // EditTexts
        val edits = listOf(etHeight, etWeight, etGoalWeight)
        edits.forEach { et ->
            if (unlock) {
                // Fully re-enable interaction
                et.isEnabled = true                   // keep style but allow input
                et.isFocusable = true
                et.isFocusableInTouchMode = true
                et.isClickable = true
                et.isLongClickable = true
                et.setTextIsSelectable(true)

                // Give them a real inputType (ensures proper keyboard)
                et.inputType = if (et === etWeight || et === etGoalWeight) {
                    android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    android.text.InputType.TYPE_CLASS_NUMBER
                }

                // Digits guard
                val allowDecimals = et === etWeight || et === etGoalWeight
                et.keyListener = if (allowDecimals)
                    DigitsKeyListener.getInstance("0123456789.")
                else
                    DigitsKeyListener.getInstance("0123456789")

                // Make sure parents don't intercept taps (e.g., nested containers)
                et.setOnTouchListener { v, ev ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.onTouchEvent(ev) // let EditText handle selection/cursor
                    true
                }
            } else {
                // Keep styling but block edits
                et.keyListener = null
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isClickable = false
                et.isLongClickable = false
                et.setTextIsSelectable(false)
                et.setOnTouchListener(null)
            }
        }

        // Quality-of-life: when unlocking, jump cursor to Weight and show the keyboard
        if (unlock) {
            etWeight.post {
                etWeight.requestFocus()
                etWeight.setSelection(etWeight.text?.length ?: 0)
                showKeyboard(etWeight)
            }
        } else {
            // hide keyboard on lock (optional)
            hideKeyboard(currentFocus)
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View?) {
        view ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }


    private fun updateBmi() {
        val hCm = etHeight.text?.toString()?.trim()?.toIntOrNull()
        val wKg = etWeight.text?.toString()?.trim()?.toDoubleOrNull()

        val bmi = computeBmi(hCm, wKg)
        tvBmiValue.text = bmi?.let { b ->
            val bmiInt = kotlin.math.ceil(b).toInt()
            val cat = bmiCategory(b)
            "$bmiInt ($cat)"
        } ?: "—"
    }

    private fun computeBmi(heightCm: Int?, weightKg: Double?): Double? {
        if (heightCm == null || heightCm <= 0) return null
        if (weightKg == null || weightKg <= 0.0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    private fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Normal"
        bmi < 30.0 -> "Overweight"
        else       -> "Obese"
    }

    private fun showHint(text: String, durationMs: Long = 6000L, fadeMs: Long = 250L) {
        tvSettingsHint.text = text
        tvSettingsHint.alpha = 1f
        tvSettingsHint.visibility = View.VISIBLE

        hintHideJob?.cancel()
        hintHideJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(durationMs)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                tvSettingsHint.animate()
                    .alpha(0f)
                    .setDuration(fadeMs)
                    .withEndAction {
                        tvSettingsHint.visibility = View.GONE
                        tvSettingsHint.alpha = 1f
                    }
                    .start()
            }
        }
    }

    // ---------- Repo sprite helpers ----------
    private fun setSpriteForSex(sexRaw: String?) {
        val resId = when (sexRaw?.trim()?.lowercase()) {
            "female", "f", "woman", "girl" -> R.drawable.user_icon_female_sprite
            "male", "m", "man", "boy"      -> R.drawable.user_icon_male_sprite
            else                           -> R.drawable.user_icon_male_sprite
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
        val cbMandatory = view.findViewById<CheckBox>(R.id.cb_mandatory_rest)

        CompoundButtonCompat.setButtonTintList(cbMandatory, checkboxTint())

        tilTimer.setDefaultHintTextColor(
            ContextCompat.getColorStateList(this, R.color.hint_label)!!
        )

        val checks = mutableListOf<CheckBox>()

        // preload timer
        lifecycleScope.launch {
            val existing = db.userSettingsDao().getByUserId(userId)
            withContext(Dispatchers.Main) {
                etTimer.setText(formatDuration(existing?.restTimerSec ?: 180))
                cbMandatory.isChecked = existing?.mandatoryRest ?: false
            }
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
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
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

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

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
                            equipmentCsv = chosenCanon.joinToString("|"),
                            mandatoryRest = cbMandatory.isChecked
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
