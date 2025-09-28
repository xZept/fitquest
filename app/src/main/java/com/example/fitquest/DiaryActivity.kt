package com.example.fitquest

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class DiaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)
        hideSystemBars()

        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        val pager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) WorkoutHistoryFragment() else QuestHistoryFragment()
        }

        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = if (pos == 0) "Workout History" else "Quest History"
        }.attach()
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
