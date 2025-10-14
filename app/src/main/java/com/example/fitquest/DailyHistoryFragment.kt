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
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailyHistoryFragment : Fragment() {

    private val zone = ZoneId.of("Asia/Manila")
    private val fmt  = DateTimeFormatter.ofPattern("EEE, MMM d")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_daily_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<LinearLayout>(R.id.list_container)

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val db  = AppDatabase.getInstance(ctx)
            val uid = DataStoreManager.getUserId(ctx).first()
            if (uid == -1) return@launch

            val repo = ProgressRepository(db)

            // NEW: only days that actually have data (no prebuilt 0..13 loop)
            val summaries = withContext(Dispatchers.IO) {
                repo.dailyHistory(uid, limit = 30)   // <- uses activeDayKeys under the hood
            }

            list.removeAllViews()
            val inf = LayoutInflater.from(ctx)

            summaries.forEach { s ->
                val row = inf.inflate(R.layout.item_daily_history_row, list, false)

                // Convert dayKey -> LocalDate for the label you already show
                val y = s.dayKey / 10_000
                val m = (s.dayKey / 100) % 100
                val d = s.dayKey % 100
                val date = LocalDate.of(y, m, d)
                row.findViewById<TextView>(R.id.tv_day).text = date.format(fmt)

                val dev = s.calories - s.planCalories
                val devTxt = if (s.planCalories > 0) "(${if (dev > 0) "+$dev" else "$dev"})" else ""
                val workouts = s.workoutsCompletedToday

                row.findViewById<TextView>(R.id.tv_stats).text =
                    if (s.planCalories > 0)
                        "${s.calories} / ${s.planCalories} kcal $devTxt • $workouts workout${if (workouts==1) "" else "s"}"
                    else
                        "${s.calories} kcal • $workouts workout${if (workouts==1) "" else "s"}"

                list.addView(row)
            }
        }
    }

}
