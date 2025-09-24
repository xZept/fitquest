package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.datastore.DataStoreManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.widget.ImageButton



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

    // at top-level fields
    private lateinit var spriteView: ImageView
    private var iconAnim: AnimationDrawable? = null

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Hide system navigation (match other screens)
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

        pressAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.press)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "fitquestDB"
        ).build()

        spriteView = findViewById(R.id.iv_profile_photo)
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvSex = findViewById(R.id.tv_sex)
        spActivityLevel = findViewById(R.id.sp_activity_level)
        spFitnessGoal = findViewById(R.id.sp_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        btnSave = findViewById(R.id.btn_save_profile)

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showSettingsDialog()
        }

        val activityOptions = resources.getStringArray(R.array.activity_levels)
        val goalOptions = resources.getStringArray(R.array.fitness_goals)

        spActivityLevel.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, activityOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        spFitnessGoal.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, goalOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        // Default sprite while loading (optional)
        setSpriteForSex(null)

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ProfileActivity).first()
            if (userId != -1) {
                loggedInUser = db.userDAO().getUserById(userId)
                loggedInUser?.let { user ->
                    val profile = db.userProfileDAO().getProfileByUserId(userId)
                    runOnUiThread {
                        tvName.text = "${user.firstName} ${user.lastName}"
                        tvAge.text = "Age: ${user.age}"
                        tvSex.text = "Sex: ${user.sex}" // or user.gender

                        // switch sprite now that we know the user's sex
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
                Toast.makeText(this@ProfileActivity, "No user found", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                finish()
            }
        }

        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)
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
                        equipment = null // managed in Settings
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

                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setupNavigationBar()
    }

    private fun setSpriteForSex(sexRaw: String?) {
        val resId = when (sexRaw?.trim()?.lowercase()) {
            "female", "f", "woman", "girl" -> R.drawable.user_icon_female_sprite
            "male", "m", "man", "boy"      -> R.drawable.user_icon_male_sprite
            else                           -> R.drawable.user_icon_male_sprite // fallback
        }
        iconAnim?.stop()
        spriteView.setImageResource(resId)
        iconAnim = spriteView.drawable as? AnimationDrawable
        spriteView.post { iconAnim?.start() } // start after laid out to avoid flicker
    }

    override fun onResume() {
        super.onResume()
        iconAnim?.start()
    }

    override fun onPause() {
        iconAnim?.stop()
        super.onPause()
    }

    private fun setSpinnerToValue(spinner: Spinner, value: String?) {
        if (value.isNullOrBlank()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val itemText = adapter.getItem(i)?.toString() ?: continue
            if (itemText.equals(value, ignoreCase = true)) {
                spinner.setSelection(i); return
            }
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

    /** Settings dialog with typed timer + equipment + logout */
    private fun showSettingsDialog() {
        if (userId == -1) {
            Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show()
            return
        }
        val store = SettingsStore(this)

        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val tilTimer = view.findViewById<TextInputLayout>(R.id.til_timer)
        val etTimer = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_timer)
        val eqContainer = view.findViewById<LinearLayout>(R.id.equipment_container)

        tilTimer.setDefaultHintTextColor(
            ContextCompat.getColorStateList(this, R.color.hint_label)!!
        )

        val currentSec = store.getRestTimerSec(userId)
        etTimer.setText(formatDuration(currentSec))

        val equipment = listOf(
            1L to "Dumbbells",
            2L to "Bench",
            3L to "Pull-up Bar",
            4L to "Barbell",
            5L to "Kettlebell"
        )
        val selected = store.getEquipmentIds(userId)
        val checks = mutableListOf<CheckBox>()
        equipment.forEach { (id, name) ->
            val cb = CheckBox(this).apply {
                text = name
                setTextColor(0xFF111827.toInt())
                isChecked = id in selected
                CompoundButtonCompat.setButtonTintList(this, checkboxTint())
            }
            checks += cb
            eqContainer.addView(cb)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setView(view)
            .setNeutralButton("Log out") { _, _ ->
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
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_bg_card)

            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val neutral  = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            listOf(positive, negative, neutral).forEach { it?.isAllCaps = false }
            positive?.setTextColor(0xFF10B981.toInt())
            negative?.setTextColor(0xFF6B7280.toInt())
            neutral?.setTextColor(0xFFEF4444.toInt())

            positive?.setOnClickListener {
                val seconds = parseDurationToSeconds(etTimer.text?.toString()?.trim() ?: "")
                if (seconds == null || seconds < 15 || seconds > 20 * 60) {
                    Toast.makeText(this, "Please enter 15s to 20m (e.g., 180 or 3:00).", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                store.setRestTimerSec(userId, seconds)

                val chosen = mutableSetOf<Long>()
                equipment.forEachIndexed { i, (id, _) -> if (checks[i].isChecked) chosen += id }
                store.setEquipmentIds(userId, chosen)

                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

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
}

/** Minimal per-user settings storage (replace with Room later). */
private class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fitquest_settings", Context.MODE_PRIVATE)
    fun getRestTimerSec(userId: Int): Int = prefs.getInt("rest_$userId", 180)
    fun setRestTimerSec(userId: Int, seconds: Int) {
        prefs.edit().putInt("rest_$userId", seconds).apply()
    }
    fun getEquipmentIds(userId: Int): MutableSet<Long> =
        prefs.getStringSet("equip_$userId", emptySet())!!
            .mapNotNull { it.toLongOrNull() }.toMutableSet()
    fun setEquipmentIds(userId: Int, ids: Set<Long>) {
        prefs.edit().putStringSet("equip_$userId", ids.map { it.toString() }.toSet()).apply()
    }
}
