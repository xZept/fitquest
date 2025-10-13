package com.example.fitquest

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.repository.ProgressRepository
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter

class WeeklyHistoryFragment : Fragment() {

    private val zone = ZoneId.of("Asia/Manila")
    private val fmt  = DateTimeFormatter.ofPattern("MMM d")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_weekly_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<LinearLayout>(R.id.list_weeks)

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val db  = AppDatabase.getInstance(ctx)
            val uid = DataStoreManager.getUserId(ctx).first()
            if (uid == -1) return@launch

            val repo = ProgressRepository(db)

            // last 4 full weeks (Mon–Sun) ending this week
            val today    = LocalDate.now(zone)
            val thisMon  = today.with(DayOfWeek.MONDAY)
            val weeks    = (0..3).map { n ->
                val start = thisMon.minusWeeks(n.toLong())
                val end   = start.plusDays(6)
                start to end
            }

            val inf = LayoutInflater.from(ctx)
            list.removeAllViews()

            for ((start, end) in weeks) {
                // 7 daily summaries -> aggregate
                val dayKeys = (0..6).map { d ->
                    val ld = start.plusDays(d.toLong())
                    ld.year*10000 + ld.monthValue*100 + ld.dayOfMonth
                }
                val daily = withContext(Dispatchers.IO) {
                    dayKeys.map { dk -> repo.dailySummary(uid, dk) }
                }

                val daysCount = daily.size.coerceAtLeast(1)
                val totalCals = daily.sumOf { it.calories }
                val totalPlan = daily.sumOf { it.planCalories }
                val workouts  = daily.sumOf { it.workoutsCompletedToday }

                val avgCals   = (totalCals / daysCount)
                val avgPlan   = (totalPlan / daysCount).coerceAtLeast(0)
                val dev       = if (avgPlan > 0) (avgCals - avgPlan) else 0

                val row = inf.inflate(R.layout.item_weekly_history_row, list, false)
                row.findViewById<TextView>(R.id.tv_week_range).text =
                    "${start.format(fmt)} – ${end.format(fmt)}"

                row.findViewById<TextView>(R.id.tv_week_stats).text =
                    if (avgPlan > 0)
                        "Avg: $avgCals / $avgPlan kcal (${if (dev>0) "+$dev" else "$dev"}) • $workouts workout${if (workouts==1) "" else "s"}"
                    else
                        "Avg: $avgCals kcal • $workouts workout${if (workouts==1) "" else "s"}"

                list.addView(row)
            }
        }
    }
}
