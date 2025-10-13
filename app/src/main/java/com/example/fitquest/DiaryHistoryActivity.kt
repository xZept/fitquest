package com.example.fitquest

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.fragment.app.Fragment

class DiaryHistoryActivity : AppCompatActivity() {

    private val titles = arrayOf("daily history", "weekly history")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_history)

        val pager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.pager)
        val tabs  = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }


        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) DailyHistoryFragment() else WeeklyHistoryFragment()
        }

        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = titles[pos]
        }.attach()

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
    }
}
