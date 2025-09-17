package com.example.fitquest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log // It's good practice to add logs for debugging
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.User
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

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

        // --- START OF CHANGES ---

        // 1. CRITICAL FIX: Use the EXACT SAME database name as in your Repository.
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "fitquestDB" // Was "fitquest-db", now matches the repository
        ).build()

        // --- END OF CHANGES ---

        // Initialize UI elements
        tvName = findViewById(R.id.tv_name)
        tvAge = findViewById(R.id.tv_age)
        tvActivityLevel = findViewById(R.id.tv_activity_level)
        tvFitnessGoal = findViewById(R.id.tv_fitness_goal)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        chipGroupEquipment = findViewById(R.id.chipGroup_equipment)
        btnSave = findViewById(R.id.btn_save_profile)

        // Load equipment list from arrays.xml
        equipmentList = resources.getStringArray(R.array.equipment_list)

        // Dynamically add equipment chips
        equipmentList.forEach { equipment ->
            val chip = Chip(this).apply {
                text = equipment
                isCheckable = true
            }
            chipGroupEquipment.addView(chip)
        }

        // --- START OF CHANGES ---

        // 2. Load user info using the correct Long type
        val sharedPref = getSharedPreferences("FitQuestPrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getLong("LOGGED_IN_USER_ID", -1L) // Use getLong with a Long default value
        Log.d("FitquestDB", "ProfileActivity: Trying to load user with ID: $userId")


        if (userId != -1L) { // Check against the Long default value
            lifecycleScope.launch {
                // 3. Convert the Long ID to an Int right before the database call
                loggedInUser = db.userDAO().getUserById(userId.toInt())
                Log.d("FitquestDB", "ProfileActivity: Found user data: $loggedInUser")


                loggedInUser?.let { user ->
                    runOnUiThread {
                        tvName.text = "${user.firstName} ${user.lastName}"
                        tvAge.text = "Age: ${user.age}"
                        tvActivityLevel.text = "Activity Level: ${user.activityLevel}"
                        tvFitnessGoal.text = "Goal: ${user.goal}"
                        etHeight.setText(user.height.toString())
                        etWeight.setText(user.weight.toString())

                        // Restore previously selected equipment
                        val savedEquipments = user.equipmentPrefs?.split(",") ?: emptyList()
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
            Log.d("FitquestDB", "ProfileActivity: No valid user ID found in SharedPreferences.")
        }

        // --- END OF CHANGES ---

        // Save button listener
        btnSave.setOnClickListener {
            loggedInUser?.let { user ->
                val newHeight = etHeight.text.toString().toIntOrNull() ?: user.height
                val newWeight = etWeight.text.toString().toIntOrNull() ?: user.weight

                // Collect selected equipment
                val selectedEquipments = mutableListOf<String>()
                for (i in 0 until chipGroupEquipment.childCount) {
                    val chip = chipGroupEquipment.getChildAt(i) as Chip
                    if (chip.isChecked) {
                        selectedEquipments.add(chip.text.toString())
                    }
                }
                val newEquipmentPrefs = selectedEquipments.joinToString(",")

                val updatedUser = user.copy(
                    height = newHeight,
                    weight = newWeight,
                    equipmentPrefs = newEquipmentPrefs
                )

                lifecycleScope.launch {
                    db.userDAO().updateUser(updatedUser)
                    runOnUiThread {
                        Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // --- NAVIGATION SETUP ---
        val navDashboard = findViewById<ImageView>(R.id.nav_icon_dashboard)
        val navShop = findViewById<ImageView>(R.id.nav_icon_shop)
        val navProfile = findViewById<ImageView>(R.id.nav_icon_profile)
        val navWorkout = findViewById<ImageView>(R.id.nav_icon_workout)
        val navMacro = findViewById<ImageView>(R.id.nav_icon_macro)

        navDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navShop.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navProfile.setOnClickListener {
            // already here
        }

        navWorkout.setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navMacro.setOnClickListener {
            val intent = Intent(this, MacroActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }
}
