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

            // last 14 days (including today)
            val days = (0..13).map { n ->
                val d  = LocalDate.now(zone).minusDays(n.toLong())
                val dk = d.year*10000 + d.monthValue*100 + d.dayOfMonth
                Pair(d, dk)
            }

            val summaries = withContext(Dispatchers.IO) {
                days.map { (d, dk) -> d to repo.dailySummary(uid, dk) }
            }

            list.removeAllViews()
            val inf = LayoutInflater.from(ctx)
            summaries.forEach { (date, s) ->
                val row = inf.inflate(R.layout.item_daily_history_row, list, false)
                row.findViewById<TextView>(R.id.tv_day).text = date.format(fmt)

                val dev = s.calories - s.planCalories
                val devTxt = if (s.planCalories > 0) "(${if (dev>0) "+$dev" else "$dev"})" else ""
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
