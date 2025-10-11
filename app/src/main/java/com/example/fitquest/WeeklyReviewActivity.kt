package com.example.fitquest

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.repository.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

class WeeklyReviewActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var pbWeekKcal: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var pbWeekProtein: com.google.android.material.progressindicator.LinearProgressIndicator


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_review)
        db = AppDatabase.getInstance(applicationContext)

        val tvRange = findViewById<TextView>(R.id.tv_range)
        val tvKcal = findViewById<TextView>(R.id.tv_week_kcal)
        val tvProtein = findViewById<TextView>(R.id.tv_week_protein)
        val tvWorkouts = findViewById<TextView>(R.id.tv_week_workouts)

        pbWeekKcal = findViewById(R.id.pb_week_kcal)
        pbWeekProtein = findViewById(R.id.pb_week_protein)





        lifecycleScope.launch {
            val userId = DataStoreManager.getUserId(this@WeeklyReviewActivity).first()
            if (userId == -1) return@launch
            val repo = ProgressRepository(db)

            val todayMs = System.currentTimeMillis()
            val z = ZoneId.of("Asia/Manila")
            val zdt = java.time.Instant.ofEpochMilli(todayMs).atZone(z)
            val endDayKey = zdt.year * 10_000 + zdt.monthValue * 100 + zdt.dayOfMonth

            val week = repo.weeklySummary(userId, endDayKey)

            // Colors (use the Activity context)
            val ok   = androidx.core.content.ContextCompat.getColor(this@WeeklyReviewActivity, R.color.progress_ok)
            val over = androidx.core.content.ContextCompat.getColor(this@WeeklyReviewActivity, R.color.progress_over)

// --- Calories adherence bar (0..100% where 100 = near target)
            val avgDev = week.avgKcalDeviation // can be negative or positive
            val tolerance = 400 // 0% when >= ±400 kcal off on average
            val adherence = (100 - (kotlin.math.abs(avgDev).coerceAtMost(tolerance) * 100 / tolerance)).toInt()

            pbWeekKcal.max = 100
            pbWeekKcal.setProgressCompat(adherence.coerceIn(0, 100), true)
            pbWeekKcal.setIndicatorColor(if (kotlin.math.abs(avgDev) <= 100) ok else over)

// --- Protein hit bar (0..100% where 100 = target met on average)
            val proteinAvg = week.proteinHitPctAvg.coerceIn(0, 200)
            pbWeekProtein.max = 100
            pbWeekProtein.setProgressCompat(proteinAvg.coerceAtMost(100), true)
            pbWeekProtein.setIndicatorColor(if (proteinAvg <= 100) ok else over)


            tvRange.text = "Week: ${fmtDayKey(week.fromDayKey)} – ${fmtDayKey(week.toDayKey)}"
            tvKcal.text = "Avg kcal deviation: ${fmtDev(week.avgKcalDeviation)}"
            tvProtein.text = "Avg protein hit: ${week.proteinHitPctAvg}%"
            tvWorkouts.text = "Workouts: ${week.workoutsCompleted}"

            // (Next iteration: draw a line chart of daily kcal vs plan)
        }
    }

    private fun fmtDayKey(dk: Int): String {
        val y = dk / 10_000
        val m = (dk / 100) % 100
        val d = dk % 100
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private fun fmtDev(dev: Int): String = if (dev == 0) "±0" else if (dev > 0) "+$dev" else "$dev"
}
