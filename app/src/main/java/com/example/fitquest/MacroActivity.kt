package com.example.fitquest

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

class MacroActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro)

        // hides the system navigation
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

//        val mealList = listOf(
//            MealItem(R.drawable.empty_plate_container, "Empty"),
//            MealItem(R.drawable.empty_plate_container, "Empty"),
//            MealItem(R.drawable.empty_plate_container, "Empty")
//        )

//        val recyclerView: RecyclerView = findViewById(R.id.meal_recycler_view)
//        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
//        recyclerView.adapter = MealAdapter(mealList)

//        // Optional: Snap to center when scrolling
//        val snapHelper = LinearSnapHelper()
//        snapHelper.attachToRecyclerView(recyclerView)



        // Find the nav icons
        val navDashboard = findViewById<ImageView>(R.id.nav_icon_dashboard)
        val navShop = findViewById<ImageView>(R.id.nav_icon_shop)
        val navProfile = findViewById<ImageView>(R.id.nav_icon_profile)
        val navWorkout = findViewById<ImageView>(R.id.nav_icon_workout)
        val navMacro = findViewById<ImageView>(R.id.nav_icon_macro)

        // Set click listeners
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
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navWorkout.setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navMacro.setOnClickListener {
            // You're already on dashboard, optionally do nothing
        }
    }


}
