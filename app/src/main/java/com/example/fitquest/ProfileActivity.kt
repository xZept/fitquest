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
import com.example.fitquest.cosmetics.BgCosmetics // <-- NEW
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView


class ProfileActivity : AppCompatActivity() {

    // ----- New gating state -----
    private var editingUnlocked = false
    private var btnUnlock: ImageButton? = null
    private lateinit var repoShop: ShopRepository

    companion object {
        private const val EDIT_TICKET_CODE = "edit_profile_ticket"
        private const val EXTRA_SHOP_TAB = "shop_tab"
        private const val TAB_ITEMS = "items"

        private const val TOUR_PREFS = "onboarding"
        private const val PROFILE_TOUR_DONE_KEY_PREFIX = "profile_tour_done_v1_u_" // per-user key
        private const val FORCE_TOUR = false                                       // set true temporarily to test
        private val profileTourShownUsersThisProcess = mutableSetOf<Int>()          // per-process guard (per user)
    }

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

    private lateinit var spriteView: ImageView
    private var iconAnim: AnimationDrawable? = null

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var bgView: ImageView
    private var bgSprite: SpriteSheetDrawable? = null

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

    private var activitySpinnerTouched = false
    private var goalSpinnerTouched = false
    private var suppressHint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bgView = findViewById(R.id.bg_anim)
        tvSettingsHint = findViewById(R.id.tv_settings_hint)
        showHint(HINT_DEFAULT)

        // Initialize repos/db
        repository = FitquestRepository(this)
        db = AppDatabase.getInstance(applicationContext)
        repoShop = ShopRepository(db)

        // --- BACKGROUND COSMETIC: default tier 0, then upgrade when userId known
        bgSprite = BgCosmetics.buildDrawable(this, BgCosmetics.Page.PROFILE, 0).also { drawable ->
            bgView.setImageDrawable(drawable); drawable.start()
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
        btnUnlock = findViewById(R.id.btn_unlock_edit)
        tvBmiValue = findViewById(R.id.tv_bmi_value)

        unlockEditing(false)

        etGoalWeight.addTextChangedListener {
            if (etGoalWeight.hasFocus()) goalWeightEditedManually = true
        }

        findViewById<ImageButton>(R.id.btn_diary)?.setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DiaryActivity::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showSettingsDialog()
        }

        etHeight.filters = arrayOf(android.text.InputFilter.LengthFilter(3))
        etWeight.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        etHeight.addTextChangedListener {
            if (!suppressHint && etHeight.hasFocus()) showHint(HINT_HEIGHT)
            validateHeight(); updateBmi()
        }
        etWeight.addTextChangedListener {
            if (!suppressHint && etWeight.hasFocus()) showHint(HINT_WEIGHT)
            validateWeight(); updateBmi()
        }
        etHeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showHint(HINT_HEIGHT) else validateHeight()
        }
        etWeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showHint(HINT_WEIGHT) else validateWeight()
        }
        etHeight.setOnClickListener { showHint(HINT_HEIGHT) }
        etWeight.setOnClickListener { showHint(HINT_WEIGHT) }

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
        spActivityLevel.adapter = ArrayAdapter(this, R.layout.item_spinner_text, activityOptions)
            .apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        spFitnessGoal.adapter = ArrayAdapter(this, R.layout.item_spinner_text, goalOptions)
            .apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

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

            // --- upgrade background if the user owns a higher tier ---
            val tier = withContext(Dispatchers.IO) {
                BgCosmetics.highestOwnedTier(userId, repoShop, BgCosmetics.Page.PROFILE)
            }
            if (tier > 0) {
                withContext(Dispatchers.Main) {
                    val upgraded = BgCosmetics.buildDrawable(this@ProfileActivity, BgCosmetics.Page.PROFILE, tier)
                    bgView.setImageDrawable(upgraded)
                    bgSprite?.stop()
                    bgSprite = upgraded
                    upgraded.start()
                }
            }
        }

        btnUnlock?.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val have = withContext(Dispatchers.IO) { repoShop.getItemQuantity(userId, EDIT_TICKET_CODE) }
                if (have <= 0) {
                    val view = layoutInflater.inflate(R.layout.dialog_edit_locked, null)
                    view.findViewById<TextView>(R.id.tv_title).text = "Edit Ticket required"
                    view.findViewById<TextView>(R.id.tv_message).text =
                        "Buy an Edit Profile Ticket in the Shop to unlock editing."

                    val dlg = MaterialAlertDialogBuilder(this@ProfileActivity)
                        .setView(view).create()
                    dlg.setOnShowListener { dlg.window?.setBackgroundDrawableResource(android.R.color.transparent) }
                    view.findViewById<ImageButton>(R.id.btn_open_shop).setOnClickListener {
                        startActivity(Intent(this@ProfileActivity, ShopActivity::class.java).apply {
                            putExtra(EXTRA_SHOP_TAB, TAB_ITEMS)
                        })
                        overridePendingTransition(0, 0)
                        dlg.dismiss()
                    }
                    view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener { dlg.dismiss() }
                    dlg.show()
                } else {
                    unlockEditing(true)
                    Toast.makeText(this@ProfileActivity, "Editing unlocked. Ticket will be used when you save.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                if (!editingUnlocked) {
                    val have = withContext(Dispatchers.IO) { repoShop.getItemQuantity(userId, EDIT_TICKET_CODE) }
                    if (have <= 0) {
                        val view = layoutInflater.inflate(R.layout.dialog_edit_locked, null)
                        val dlg = MaterialAlertDialogBuilder(this@ProfileActivity).setView(view).create()
                        dlg.setOnShowListener { dlg.window?.setBackgroundDrawableResource(android.R.color.transparent) }
                        view.findViewById<ImageButton>(R.id.btn_open_shop).setOnClickListener {
                            startActivity(Intent(this@ProfileActivity, ShopActivity::class.java).apply {
                                putExtra(EXTRA_SHOP_TAB, TAB_ITEMS)
                            })
                            overridePendingTransition(0, 0)
                            dlg.dismiss()
                        }
                        view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener { dlg.dismiss() }
                        dlg.show()
                        return@launch
                    } else {
                        val view = layoutInflater.inflate(R.layout.dialog_use_ticket, null)
                        val dlg = MaterialAlertDialogBuilder(this@ProfileActivity).setView(view).create()
                        dlg.setOnShowListener { dlg.window?.setBackgroundDrawableResource(android.R.color.transparent) }
                        view.findViewById<ImageButton>(R.id.btn_use_ticket).setOnClickListener {
                            unlockEditing(true)
                            Toast.makeText(this@ProfileActivity, "Editing unlocked. Make your changes and press Save.", Toast.LENGTH_LONG).show()
                            dlg.dismiss()
                        }
                        view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener { dlg.dismiss() }
                        dlg.show()
                        return@launch
                    }
                }

                if (!validateHeightWeight()) {
                    Toast.makeText(this@ProfileActivity, "Please fix height/weight.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val consumed = withContext(Dispatchers.IO) { repoShop.consumeItem(userId, EDIT_TICKET_CODE, 1) }
                if (!consumed) {
                    Toast.makeText(this@ProfileActivity, "No Edit Ticket found.", Toast.LENGTH_SHORT).show()
                    unlockEditing(false); return@launch
                }

                val plan = withContext(Dispatchers.IO) {
                    val existing = db.userProfileDAO().getProfileByUserId(userId)
                    val prevWeightInt = existing?.weight
                    val typedWeightDouble = etWeight.text.toString().toDoubleOrNull()
                    val newWeightInt = typedWeightDouble?.roundToInt() ?: existing?.weight ?: 0

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

                    if (existing == null) db.userProfileDAO().insert(updated) else db.userProfileDAO().update(updated)

                    // log weight change so diary + dashboard update
                    if (prevWeightInt == null || prevWeightInt != newWeightInt) {
                        val weightForLog = typedWeightDouble?.toFloat() ?: newWeightInt.toFloat()
                        db.weightLogDao().insert(
                            WeightLog(userId = userId, loggedAt = System.currentTimeMillis(), weightKg = weightForLog)
                        )
                    }
                    sendBroadcast(Intent(DashboardActivity.ACTION_WEIGHT_LOGGED).apply { setPackage(packageName) })

                    repository.computeAndSaveMacroPlan(userId)
                }

                unlockEditing(false)

                Toast.makeText(
                    this@ProfileActivity,
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
        spActivityLevel.isEnabled = unlock
        spActivityLevel.isClickable = unlock
        spFitnessGoal.isEnabled = unlock
        spFitnessGoal.isClickable = unlock
        val edits = listOf(etHeight, etWeight, etGoalWeight)
        edits.forEach { et ->
            if (unlock) {
                et.isEnabled = true
                et.isFocusable = true
                et.isFocusableInTouchMode = true
                et.isClickable = true
                et.isLongClickable = true
                et.setTextIsSelectable(true)
                et.inputType = if (et === etWeight || et === etGoalWeight)
                    android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                else android.text.InputType.TYPE_CLASS_NUMBER
                val allowDecimals = et === etWeight || et === etGoalWeight
                et.keyListener = if (allowDecimals) DigitsKeyListener.getInstance("0123456789.") else DigitsKeyListener.getInstance("0123456789")
                et.setOnTouchListener { v, ev ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.onTouchEvent(ev); true
                }
            } else {
                et.keyListener = null
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isClickable = false
                et.isLongClickable = false
                et.setTextIsSelectable(false)
                et.setOnTouchListener(null)
            }
        }

        if (unlock) {
            etWeight.post {
                etWeight.requestFocus()
                etWeight.setSelection(etWeight.text?.length ?: 0)
                showKeyboard(etWeight)
            }
        } else hideKeyboard(currentFocus)
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
            val bmiInt = kotlin.math.ceil(b).toInt(); val cat = bmiCategory(b); "$bmiInt ($cat)"
        } ?: "—"
    }
    private fun computeBmi(heightCm: Int?, weightKg: Double?): Double? {
        if (heightCm == null || heightCm <= 0) return null
        if (weightKg == null || weightKg <= 0.0) return null
        val m = heightCm / 100.0; return weightKg / (m * m)
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
                tvSettingsHint.animate().alpha(0f).setDuration(fadeMs).withEndAction {
                    tvSettingsHint.visibility = View.GONE; tvSettingsHint.alpha = 1f
                }.start()
            }
        }
    }

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

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ProfileActivity).first()
            if (userId > 0) showProfileTourIfNeeded(userId)
        }
    }

    override fun onPause() { bgSprite?.stop(); iconAnim?.stop(); super.onPause() }
    override fun onDestroy() { bgView.setImageDrawable(null); bgSprite = null; super.onDestroy() }

    private fun validateHeight(): Boolean {
        val h = etHeight.text.toString().trim().toIntOrNull()
        return when {
            h == null -> { etHeight.error = "Enter height in cm"; false }
            h < MIN_HEIGHT_CM || h > MAX_HEIGHT_CM -> { etHeight.error = "Height must be $MIN_HEIGHT_CM–$MAX_HEIGHT_CM cm"; false }
            else -> { etHeight.error = null; true }
        }
    }
    private fun validateWeight(): Boolean {
        val w = etWeight.text.toString().trim().toDoubleOrNull()
        return when {
            w == null -> { etWeight.error = "Enter weight in kg"; false }
            w < MIN_WEIGHT_KG || w > MAX_WEIGHT_KG -> { etWeight.error = "Weight must be ${MIN_WEIGHT_KG.toInt()}–${MAX_WEIGHT_KG.toInt()} kg"; false }
            else -> { etWeight.error = null; true }
        }
    }
    private fun validateHeightWeight(): Boolean = validateHeight() && validateWeight()

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
        val colors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#9CA3AF"))
        return ColorStateList(states, colors)
    }

    private fun showSettingsDialog() {
        if (userId == -1) { Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show(); return }
        exitImmersive()
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val tilTimer = view.findViewById<TextInputLayout>(R.id.til_timer)
        val etTimer = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_timer)
        val eqContainer = view.findViewById<GridLayout>(R.id.equipment_container)
        val cbMandatory = view.findViewById<CheckBox>(R.id.cb_mandatory_rest)

        CompoundButtonCompat.setButtonTintList(cbMandatory, checkboxTint())
        tilTimer.setDefaultHintTextColor(ContextCompat.getColorStateList(this, R.color.hint_label)!!)

        val checks = mutableListOf<CheckBox>()

        lifecycleScope.launch {
            val existing = db.userSettingsDao().getByUserId(userId)
            withContext(Dispatchers.Main) {
                etTimer.setText(formatDuration(existing?.restTimerSec ?: 180))
                cbMandatory.isChecked = existing?.mandatoryRest ?: false
            }
        }

        lifecycleScope.launch {
            val canonNames = loadEquipmentNamesFromCsv()
            val existing = db.userSettingsDao().getByUserId(userId)
            val selectedCanon = existing?.equipmentCsv?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

            withContext(Dispatchers.Main) {
                canonNames.forEach { canon ->
                    val label = displayLabel(canon)
                    val lp = GridLayout.LayoutParams().apply {
                        width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                    val cb = CheckBox(this@ProfileActivity).apply {
                        text = label; tag = canon; setTextColor(0xFF111827.toInt())
                        isChecked = canon in selectedCanon
                        CompoundButtonCompat.setButtonTintList(this, checkboxTint())
                        maxLines = 2; ellipsize = TextUtils.TruncateAt.END; layoutParams = lp
                    }
                    checks += cb; eqContainer.addView(cb)
                }
            }
        }

        view.setPadding(20, 80, 20, 10)

        val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
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
                    startActivity(Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
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

    private fun formatDuration(totalSec: Int): String {
        val m = totalSec / 60; val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }
    private fun parseDurationToSeconds(text: String): Int? {
        if (text.isBlank()) return null
        return if (text.contains(":")) {
            val parts = text.split(":"); if (parts.size != 2) return null
            val m = parts[0].toIntOrNull() ?: return null
            val s = parts[1].toIntOrNull() ?: return null
            if (s in 0..59 && m >= 0) m * 60 + s else null
        } else text.toIntOrNull()
    }

    private fun canonicalizeEquipment(raw: String): String {
        val s = raw.trim().replace('_', ' ').replace(Regex("\\s+"), " ").lowercase()
        return when (s) {
            "dumbbells" -> "dumbbell"
            "kettlebells" -> "kettlebell"
            "resistance bands" -> "resistance band"
            "bands" -> "band"
            "battle rope" -> "battle ropes"
            "barbel" -> "barbell"
            else -> s
        }
    }
    private fun displayLabel(canon: String): String =
        if (canon == "trx") "TRX" else canon.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }

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
                    .toSet().toList().sorted()
            }
        }.getOrElse {
            listOf("bench","barbell","dumbbell","kettlebell","pull-up bar","resistance band","cable")
        }
    }

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
    private fun exitImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    private fun TapTarget.applyProfileTourStyle(): TapTarget = apply {
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
    private fun relativeTopInScroll(child: View, parent: ScrollView): Int {
        var y = 0
        var v: View? = child
        while (v != null && v !== parent) {
            y += v.top
            v = (v.parent as? View)
        }
        return y
    }
    private fun ensureVisible(scroll: ScrollView, target: View, extraTopPx: Int = dp(80)) {
        scroll.post {
            val y = (relativeTopInScroll(target, scroll) - extraTopPx).coerceAtLeast(0)
            scroll.smoothScrollTo(0, y)
        }
    }

    private fun showProfileTourIfNeeded(userId: Int) {
        if (userId <= 0) return

        val prefs = getSharedPreferences(TOUR_PREFS, MODE_PRIVATE)
        val userDoneKey = "$PROFILE_TOUR_DONE_KEY_PREFIX$userId"

        // DEV ONLY: uncomment during testing for a specific user
        if (FORCE_TOUR && BuildConfig.DEBUG) {
            prefs.edit().remove(userDoneKey).apply()
            profileTourShownUsersThisProcess.remove(userId)
        }

        // Guard once per process (per user) + once per install (per user)
        if (profileTourShownUsersThisProcess.contains(userId) || prefs.getBoolean(userDoneKey, false)) return
        profileTourShownUsersThisProcess.add(userId)

        val root   = findViewById<View>(R.id.profile_layout)
        val scroll = findViewById<ScrollView>(R.id.profile_scroll)

        // Build targets
        val steps = mutableListOf<Step>()
        findViewById<View>(R.id.btn_unlock_edit)?.let {
            steps += Step(it, "Use an Edit Ticket to enable profile changes.", "")
        }
        findViewById<View>(R.id.sp_activity_level)?.let {
            steps += Step(it, "Pick how active you are on rest days.", "", needsScroll = true)
        }
        findViewById<View>(R.id.sp_fitness_goal)?.let {
            steps += Step(it, "Choose fat loss, maintenance, or muscle gain.", "", needsScroll = true)
        }
        findViewById<View>(R.id.et_height)?.let {
            steps += Step(it, "Enter your height in cm.", "", needsScroll = true)
        }
        findViewById<View>(R.id.et_weight)?.let {
            steps += Step(it, "Enter your current weight (kg).", "", needsScroll = true)
        }
        findViewById<View>(R.id.et_goal_weight)?.let {
            steps += Step(it, "Set the weight you're aiming for (kg).", "", needsScroll = true)
        }
        findViewById<View>(R.id.tv_bmi_value)?.let {
            steps += Step(it, "Auto-calculated from height and weight.", "", needsScroll = true)
        }
        findViewById<View>(R.id.btn_save_profile)?.let {
            steps += Step(it, "Uses one Edit Ticket when you save your changes.", "", needsScroll = true)
        }
        findViewById<View>(R.id.btn_diary)?.let {
            steps += Step(it, "Review your logs and history in Diary.", "")
        }
        findViewById<View>(R.id.btn_settings)?.let {
            steps += Step(it, "Adjust timers and equipment in Settings.", "")
        }

        if (steps.isEmpty()) {
            prefs.edit().putBoolean(userDoneKey, true).apply()
            return
        }

        root.post {
            fun showStep(i: Int) {
                if (i >= steps.size) {
                    prefs.edit().putBoolean(userDoneKey, true).apply()
                    return
                }
                val s = steps[i]
                if (s.needsScroll && scroll != null) ensureVisible(scroll, s.view)

                s.view.postDelayed({
                    TapTargetView.showFor(
                        this@ProfileActivity,
                        TapTarget.forView(s.view, s.title, s.desc).applyProfileTourStyle(),
                        object : TapTargetView.Listener() {
                            override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                showStep(i + 1)
                            }
                        }
                    )
                }, 180)
            }
            showStep(0)
        }
    }


    private data class Step(
        val view: View,
        val title: String,
        val desc: String,
        val needsScroll: Boolean = false
    )




}
