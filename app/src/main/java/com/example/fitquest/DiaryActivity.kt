package com.example.fitquest

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class DiaryActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)
        hideSystemBars()


        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        val pager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) WorkoutHistoryFragment() else QuestHistoryFragment()
        }

        TabLayoutMediator(tabs, pager) { tab, pos ->
            val tv = TextView(this).apply {
                text = if (pos == 0) "Workout History" else "Quest History"
                setTextColor(android.graphics.Color.BLACK)
                textSize = 16f                     // unselected size
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
            }
            tab.customView = tv
        }.attach()

        // Grow selected tab text, shrink unselected
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                (t.customView as? TextView)?.apply { textSize = 18f }
            }
            override fun onTabUnselected(t: TabLayout.Tab) {
                (t.customView as? TextView)?.apply { textSize = 16f }
            }
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            it.startAnimation(pressAnim)
            // If there's a previous screen, this behaves like a normal back.
            if (!isTaskRoot) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                // Fallback: ensure we land on ProfileActivity
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
