// app/src/main/java/com/example/fitquest/DashboardActivity.kt
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import com.example.fitquest.utils.TipsLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView
    private lateinit var pressAnim: android.view.animation.Animation
    private var bgAnim: SpriteSheetDrawable? = null

    private lateinit var db: AppDatabase
    private lateinit var quickAction: ImageButton
    private var hasActiveQuest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = AppDatabase.getInstance(applicationContext)

        // Animated spritesheet background (repo)
        val bgView = findViewById<ImageView>(R.id.dashboard_bg)
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_page_dashboard_spritesheet0, opts)
        bgAnim = SpriteSheetDrawable(
            sheet = sheet,
            rows = 1,
            cols = 12,
            fps = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )
        bgView.setImageDrawable(bgAnim)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        // Hide system navigation (repo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        val root = findViewById<View>(R.id.dashboard_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(Type.systemBars())
            v.setPadding(0, 0, 0, sys.bottom)
            insets
        }

        dashboardTip = findViewById(R.id.dashboardTip)
        val tips = TipsLoader.loadTips(this)
        val general = tips.filter { it.category == "general" }
        dashboardTip.text =
            if (general.isNotEmpty()) {
                val dayIndex = (System.currentTimeMillis() / 86_400_000).toInt()
                general[dayIndex % general.size].tip
            } else {
                "Stay strong and keep going!"
            }

        quickAction = findViewById(R.id.btn_quick_action)
        quickAction.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                val active = db.activeQuestDao().getActiveForUser(uid)
                if (active != null) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "You already have an active quest. Finish or abandon it first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                startActivity(Intent(this@DashboardActivity, QuestGeneratorActivity::class.java))
                overridePendingTransition(0, 0)
            }
        }

        setupNavigationBar()
    }

    override fun onStart() {
        super.onStart()
        bgAnim?.start()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
            hasActiveQuest = db.activeQuestDao().getActiveForUser(uid) != null
            quickAction.isEnabled = !hasActiveQuest
            quickAction.alpha = if (hasActiveQuest) 0.5f else 1f
        }
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
