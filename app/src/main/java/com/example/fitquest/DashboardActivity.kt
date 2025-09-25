package com.example.fitquest


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import com.example.fitquest.utils.TipsLoader

class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView
    private lateinit var pressAnim: android.view.animation.Animation
    private var bgAnim: SpriteSheetDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Attach animated sprite-sheet as the background
        val bgView = findViewById<ImageView>(R.id.dashboard_bg)
        val opts = BitmapFactory.Options().apply {
            inScaled = false // important because we use pixel math on the sheet
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_dashboard_spritesheet, opts)

        bgAnim = SpriteSheetDrawable(
            sheet = sheet,
            rows = 1,
            cols = 12,           // <-- change if your sheet differs
            fps = 12,            // 12–24 is a good range
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )

        // Use as the ImageView's drawable or background
        bgView.setImageDrawable(bgAnim)

        // ...your existing code below (pressAnim, insets, tips, nav setup, etc.)
        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        // (rest of your onCreate unchanged)

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
        setupNavigationBar()



    }

    override fun onStart() {
        super.onStart()
        bgAnim?.start()
    }

    override fun onStop() {
        bgAnim?.stop()
        super.onStop()
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
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
    }

}
