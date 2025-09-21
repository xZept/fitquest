package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfileActivity : AppCompatActivity() {

    private var userId: Int = -1
    private lateinit var tvName: TextView
    private lateinit var tvAge: TextView
    private lateinit var tvSex: TextView
    private lateinit var spActivityLevel: Spinner
    private lateinit var spFitnessGoal: Spinner
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var btnSave: Button
    private lateinit var db: AppDatabase
    private var loggedInUser: User? = null

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        pressAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.press)

        // Build database
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "fitquestDB"
        ).build()

        // Initialize UI
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvSex = findViewById(R.id.tv_sex)
        spActivityLevel = findViewById(R.id.sp_activity_level)
        spFitnessGoal = findViewById(R.id.sp_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        btnSave = findViewById(R.id.btn_save_profile)

        // Settings button (top-right gear)
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            it.startAnimation(pressAnim)
            showSettingsDialog()
        }

        // Spinners (custom item layouts so they’re visible)
        val activityOptions = resources.getStringArray(R.array.activity_levels)
        val goalOptions = resources.getStringArray(R.array.fitness_goals)

        spActivityLevel.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, activityOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        spFitnessGoal.adapter = ArrayAdapter(
            this, R.layout.item_spinner_text, goalOptions
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        // Load user + profile
        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ProfileActivity).first()
            if (userId != -1) {
                loggedInUser = db.userDAO().getUserById(userId)
                loggedInUser?.let { user ->
                    val profile = db.userProfileDAO().getProfileByUserId(userId)
                    runOnUiThread {
                        tvName.text = "${user.firstName} ${user.lastName}"
                        tvAge.text = "Age: ${user.age}"
                        // If your entity uses `gender`, change to user.gender
                        tvSex.text = "Sex: ${user.sex}"

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
                // Optional: kick back to login if somehow opened while logged out
                startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                finish()
            }
        }

        // Save profile (height, weight, activity, goal)
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

    private fun setSpinnerToValue(spinner: Spinner, value: String?) {
        if (value.isNullOrBlank()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val itemText = adapter.getItem(i)?.toString() ?: continue
            if (itemText.equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun showSettingsDialog() {
        if (userId == -1) {
            Toast.makeText(this, "No user loaded", Toast.LENGTH_SHORT).show()
            return
        }
        val store = SettingsStore(this)

        val pad = (16 * resources.displayMetrics.density).roundToInt()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)

        // Rest timer
        root.addView(TextView(this).apply {
            text = "Rest Timer"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rb3 = RadioButton(this).apply { text = "3 min" }
        val rb5 = RadioButton(this).apply { text = "5 min" }
        group.addView(rb3); group.addView(rb5)
        root.addView(group)
        when (store.getRestTimerSec(userId)) { 300 -> rb5.isChecked = true; else -> rb3.isChecked = true }

        // Equipment
        root.addView(TextView(this).apply {
            text = "Available Equipment"
            textSize = 16f
            setPadding(0, pad, 0, (8 * resources.displayMetrics.density).roundToInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

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
                isChecked = id in selected
            }
            checks += cb
            root.addView(cb)
        }

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(scroll)
            .setNeutralButton("Log out") { _, _ ->
                // Clear session and return to Login
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
            .setPositiveButton("Save") { _, _ ->
                val rest = if (rb5.isChecked) 300 else 180
                store.setRestTimerSec(userId, rest)
                val chosen = mutableSetOf<Long>()
                equipment.forEachIndexed { i, (id, _) -> if (checks[i].isChecked) chosen += id }
                store.setEquipmentIds(userId, chosen)
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .show()
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
