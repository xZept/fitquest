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
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private var userId: Int = -1
    private var tvCoins: TextView? = null

    // Animated background bits
    private var bgBitmap: Bitmap? = null
    private var bgDrawable: SpriteSheetDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        db = AppDatabase.getInstance(applicationContext)
        tvCoins = findViewById(R.id.tv_coins_badge)

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

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ShopActivity).first()
            if (userId != -1) {
                withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }
                refreshCoins()
            }
        }

        // Apply animated background
        applyAnimatedBackground()

        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ShopActivity).first()
            if (userId != -1) {
                withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }
                refreshCoins()
            }
        }

        setupNavigationBar()
    }

    private fun applyAnimatedBackground() {
        val opts = BitmapFactory.Options().apply {
            inScaled = false                 // keep exact frame size (avoid density scaling)
            inPreferredConfig = Bitmap.Config.RGB_565 // lighter memory; switch to ARGB_8888 if needed
            inDither = true
        }

        bgBitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.bg_shop_spritesheet, // put PNG in drawable-nodpi ideally
            opts
        )

        val drawable = SpriteSheetDrawable(
            sheet = requireNotNull(bgBitmap) { "bg_workout_spritesheet failed to decode" },
            rows = 1,
            cols = 12,
            fps  = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )

        findViewById<RelativeLayout>(R.id.shop_layout).background = drawable
        bgDrawable = drawable
    }

    override fun onStart() {
        super.onStart()
        bgDrawable?.start()
    }

    override fun onStop() {
        super.onStop()
        bgDrawable?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Free the bitmap to avoid leaks when the Activity is finished
        bgBitmap?.recycle()
        bgBitmap = null
    }

    override fun onResume() {
        super.onResume()
        refreshCoins()
    }

    private fun refreshCoins() {
        val badge = tvCoins ?: return
        if (userId == -1) return
        lifecycleScope.launch(Dispatchers.IO) {
            val coins = db.userWalletDao().getCoins(userId) ?: 0
            withContext(Dispatchers.Main) { badge.text = coins.toString() }
        }
    }

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_workout).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, WorkoutActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
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
