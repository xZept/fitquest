package com.example.fitquest


import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import com.example.fitquest.utils.TipsLoader


class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

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

        // Find the nav icons
        val navDashboard = findViewById<ImageView>(R.id.nav_icon_dashboard)
        val navShop = findViewById<ImageView>(R.id.nav_icon_shop)
        val navProfile = findViewById<ImageView>(R.id.nav_icon_profile)
        val navWorkout = findViewById<ImageView>(R.id.nav_icon_workout)
        val navMacro = findViewById<ImageView>(R.id.nav_icon_macro)

        // Set click listeners
        navDashboard.setOnClickListener {
            // You're already on dashboard, optionally do nothing
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
            val intent = Intent(this, MacroActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }


        val mainLayout = findViewById<View>(R.id.dashboard_root)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val systemInsets = insets.getInsets(Type.systemBars())
            view.setPadding(0, 0, 0, systemInsets.bottom)
            insets
        }

        dashboardTip = findViewById(R.id.dashboardTip)

        // Load all tips
        val tips = TipsLoader.loadTips(this)

        // Filter only general tips
        val generalTips = tips.filter { it.category == "general" }

        if (generalTips.isNotEmpty()) {
            // Rotate by day (using day of year as index)
            val dayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24)).toInt()
            val tipOfDay = generalTips[dayIndex % generalTips.size]

            dashboardTip.text = tipOfDay.tip
        } else {
            dashboardTip.text = "Stay strong and keep going!"
        }
    }
}
