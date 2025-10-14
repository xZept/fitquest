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

            // NEW: only weeks that actually have data (no prebuilt Mon–Sun loop)
            val weeks = withContext(Dispatchers.IO) {
                repo.weeklyHistory(uid, limitWeeks = 12)
            }

            list.removeAllViews()
            val inf = LayoutInflater.from(ctx)

            weeks.forEach { w ->
                // --- Derive start/end dayKey safely ---
                // If your WeeklySummary exposes start/end directly, prefer those:
                // val startDk = w.from          // or w.startDayKey
                // val endDk   = w.endDayKey
                // Otherwise, derive from the contained days:
                val daysList = w.days           // adjust if your property name differs
                val startDk = daysList.minOf { it.dayKey }
                val endDk   = daysList.maxOf { it.dayKey }

                fun dkToDate(dk: Int): LocalDate {
                    val y = dk / 10_000
                    val m = (dk / 100) % 100
                    val d = dk % 100
                    return LocalDate.of(y, m, d)
                }
                val startDate = dkToDate(startDk)
                val endDate   = dkToDate(endDk)

                // --- Compute the text you already show ---
                val avgCals = daysList.map { it.calories }.average().toInt()
                val avgPlan = daysList.map { it.planCalories }.average().toInt()
                val dev     = if (avgPlan > 0) (avgCals - avgPlan) else 0
                // If WeeklySummary already has total workouts, you can use w.workouts instead
                val workouts = daysList.sumOf { it.workoutsCompletedToday }

                val row = inf.inflate(R.layout.item_weekly_history_row, list, false)
                row.findViewById<TextView>(R.id.tv_week_range).text =
                    "${startDate.format(fmt)} – ${endDate.format(fmt)}"

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
