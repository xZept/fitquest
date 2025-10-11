package com.example.fitquest

import android.os.Bundle
import android.widget.ImageButton
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

    private var activeEndDayKey: Int = 0  // last day of the shown range

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_review)
        db = AppDatabase.getInstance(applicationContext)

        val tvRange = findViewById<TextView>(R.id.tv_range)
        val tvKcal = findViewById<TextView>(R.id.tv_week_kcal)
        val tvProtein = findViewById<TextView>(R.id.tv_week_protein)
        val tvWorkouts = findViewById<TextView>(R.id.tv_week_workouts)
        val btnPrev = findViewById<ImageButton>(R.id.btn_prev_week)
        val btnNext = findViewById<ImageButton>(R.id.btn_next_week)

        pbWeekKcal = findViewById(R.id.pb_week_kcal)
        pbWeekProtein = findViewById(R.id.pb_week_protein)

        activeEndDayKey = todayDayKey() // default: 7-day window ending today
        // If you prefer calendar weeks (Mon–Sun), use:
        // activeEndDayKey = endOfIsoWeekDayKey(todayDayKey())

        fun refresh(endDk: Int) {
            lifecycleScope.launch {
                val userId = DataStoreManager.getUserId(this@WeeklyReviewActivity).first()
                if (userId == -1) return@launch
                val repo = ProgressRepository(db)
                val week = repo.weeklySummary(userId, endDk)

                tvRange.text = "Week: ${fmtDayKey(week.fromDayKey)} – ${fmtDayKey(week.toDayKey)}"
                tvKcal.text = "Avg kcal deviation: ${fmtDev(week.avgKcalDeviation)}"
                tvProtein.text = "Avg protein hit: ${week.proteinHitPctAvg}%"
                tvWorkouts.text = "Workouts: ${week.workoutsCompleted}"

                val ok   = androidx.core.content.ContextCompat.getColor(this@WeeklyReviewActivity, R.color.progress_ok)
                val over = androidx.core.content.ContextCompat.getColor(this@WeeklyReviewActivity, R.color.progress_over)

                val avgDev = week.avgKcalDeviation
                val tolerance = 400
                val adherence = (100 - (kotlin.math.abs(avgDev).coerceAtMost(tolerance) * 100 / tolerance)).toInt()

                pbWeekKcal.max = 100
                pbWeekKcal.setProgressCompat(adherence.coerceIn(0, 100), true)
                pbWeekKcal.setIndicatorColor(if (kotlin.math.abs(avgDev) <= 100) ok else over)

                val proteinAvg = week.proteinHitPctAvg.coerceIn(0, 200)
                pbWeekProtein.max = 100
                pbWeekProtein.setProgressCompat(proteinAvg.coerceAtMost(100), true)
                pbWeekProtein.setIndicatorColor(if (proteinAvg <= 100) ok else over)

                // Enable/disable Next: don't allow going into the future
                btnNext.isEnabled = addDaysToDayKey(endDk, 7) <= todayDayKey()
                btnNext.alpha = if (btnNext.isEnabled) 1f else 0.5f
            }
        }

        btnPrev.setOnClickListener {
            activeEndDayKey = addDaysToDayKey(activeEndDayKey, -7)
            refresh(activeEndDayKey)
        }
        btnNext.setOnClickListener {
            val candidate = addDaysToDayKey(activeEndDayKey, +7)
            if (candidate <= todayDayKey()) {
                activeEndDayKey = candidate
                refresh(activeEndDayKey)
            }
        }

        refresh(activeEndDayKey)
    }

    // --- Helpers ---
    private fun todayDayKey(): Int {
        val z = java.time.ZoneId.of("Asia/Manila")
        val now = java.time.ZonedDateTime.now(z)
        return now.year * 10_000 + now.monthValue * 100 + now.dayOfMonth
    }

    private fun addDaysToDayKey(dayKey: Int, delta: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val dt = java.time.LocalDate.of(y, m, d).plusDays(delta.toLong())
        return dt.year * 10_000 + dt.monthValue * 100 + dt.dayOfMonth
    }

    // Optional: align to calendar week (ISO Mon–Sun)
    private fun endOfIsoWeekDayKey(dayKey: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val dt = java.time.LocalDate.of(y, m, d)
        val end = dt.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        return end.year * 10_000 + end.monthValue * 100 + end.dayOfMonth
    }

    private fun fmtDayKey(dk: Int): String {
        val y = dk / 10_000
        val m = (dk / 100) % 100
        val d = dk % 100
        return "%04d-%02d-%02d".format(y, m, d)
    }
    private fun fmtDev(dev: Int): String = if (dev == 0) "±0" else if (dev > 0) "+$dev" else "$dev"
}

