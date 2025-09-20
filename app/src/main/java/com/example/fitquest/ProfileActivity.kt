package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.datastore.DataStoreManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private var userId: Int = -1
    private lateinit var tvName: TextView
    private lateinit var tvAge: TextView
    private lateinit var tvActivityLevel: TextView
    private lateinit var tvFitnessGoal: TextView
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var chipGroupEquipment: ChipGroup
    private lateinit var btnSave: Button
    private lateinit var db: AppDatabase
    private var loggedInUser: User? = null

    private lateinit var equipmentList: Array<String>

    // Extension property for DataStore
    val Context.dataStore by preferencesDataStore("user_prefs")

    // Key for storing userId
    val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID")

    // Get userId as Flow<Long>
    fun getUserId(context: Context): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ID_KEY] ?: -1
        }
    }

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        // Hide system navigation
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

        // Build database
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "fitquestDB"
        ).build()

        // Initialize UI elements
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvActivityLevel = findViewById(R.id.tv_activity_level)
        tvFitnessGoal = findViewById(R.id.tv_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        chipGroupEquipment = findViewById(R.id.chipGroup_equipment)
        btnSave = findViewById(R.id.btn_save_profile)

        // Load equipment list
        equipmentList = resources.getStringArray(R.array.equipment_list)
        equipmentList.forEach { equipment ->
            val chip = Chip(this).apply {
                text = equipment
                isCheckable = true
            }
            chipGroupEquipment.addView(chip)
        }

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ProfileActivity).first()
            Log.d("FitquestDB", "User ID from DataStore: $userId")

            if (userId != -1) {
                loggedInUser = db.userDAO().getUserById(userId.toInt())
                Log.d("FitquestDB", "ProfileActivity: Found user data: $loggedInUser")

                loggedInUser?.let { user ->
                    val profile = db.userProfileDAO().getProfileByUserId(userId.toInt())
                    Log.d("FitquestDB", "User: $user")
                    Log.d("FitquestDB", "Profile: $profile")

                    runOnUiThread {
                        tvName.text = "${user.firstName} ${user.lastName}"
                        tvAge.text = "Age: ${user.age}"

                        profile?.let {
                            tvActivityLevel.text = "Activity Level: ${it.activityLevel}"
                            tvFitnessGoal.text = "Goal: ${it.goal}"
                            etHeight.setText(it.height.toString())
                            etWeight.setText(it.weight.toString())

                            val savedEquipments = it.equipment?.split(",") ?: emptyList()
                            for (i in 0 until chipGroupEquipment.childCount) {
                                val chip = chipGroupEquipment.getChildAt(i) as Chip
                                if (savedEquipments.contains(chip.text.toString())) {
                                    chip.isChecked = true
                                }
                            }
                        }
                    }
                }
            } else {
                Log.d("FitquestDB", "ProfileActivity: No valid user ID found in DataStore.")
            }
        }

        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val userIdInt = this@ProfileActivity.userId.toInt()

                // Collect selected equipment
                val selectedEquipments = mutableListOf<String>()
                for (i in 0 until chipGroupEquipment.childCount) {
                    val chip = chipGroupEquipment.getChildAt(i) as Chip
                    if (chip.isChecked) {
                        selectedEquipments.add(chip.text.toString())
                    }
                }
                val newEquipmentPrefs = selectedEquipments.joinToString(",")

                // Load existing profile (if any)
                val existingProfile = db.userProfileDAO().getProfileByUserId(userIdInt)
                val newHeight = etHeight.text.toString().toIntOrNull() ?: existingProfile?.height ?: 0
                val newWeight = etWeight.text.toString().toIntOrNull() ?: existingProfile?.weight ?: 0

                val updatedProfile = UserProfile(
                    profileId = existingProfile?.profileId ?: 0,
                    userId = userIdInt,
                    height = newHeight,
                    weight = newWeight,
                    activityLevel = tvActivityLevel.text.toString(),
                    goal = tvFitnessGoal.text.toString(),
                    equipment = newEquipmentPrefs
                )

                if (existingProfile == null) {
                    db.userProfileDAO().insert(updatedProfile)
                } else {
                    db.userProfileDAO().update(updatedProfile)
                }

                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        setupNavigationBar()
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
