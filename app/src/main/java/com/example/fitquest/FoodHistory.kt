package com.example.fitquest

import android.R.attr.onClick
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageButton
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.fitquest.database.MacroDiary

private var currentUserId: Int = -1
private lateinit var db: AppDatabase
private lateinit var rv: RecyclerView
private lateinit var emptyView: TextView

class FoodHistory : AppCompatActivity() {

    private val diaryAdapter = MacroDiaryAdapter { item ->
    }

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_history)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        rv = findViewById(R.id.rvDiary)
        emptyView = findViewById(R.id.tvEmpty)
        rv.setHasFixedSize(true)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rv.adapter = diaryAdapter

        lifecycleScope.launch {
            currentUserId = DataStoreManager.getUserId(this@FoodHistory).first()
            if (currentUserId <= 0) {
                // Navigate to sign-in if user is not found
                Toast.makeText(this@FoodHistory, "User not found, navigating back to sign-in page.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@FoodHistory, LoginActivity::class.java))
                finish()
            } else {
                val history = withContext(Dispatchers.IO) {
                    db.macroDiaryDao().recent(currentUserId, limit = 30)
                }

                diaryAdapter.submitList(history)

                emptyView.isVisible = history.isEmpty()
                rv.isVisible = history.isNotEmpty()
            }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            it.startAnimation(pressAnim)
            if (!isTaskRoot) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                startActivity(
                    Intent(this, ProfileActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}