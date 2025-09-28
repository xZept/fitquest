package com.example.fitquest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.WorkoutSession
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkoutHistoryFragment : Fragment(R.layout.fragment_workout_history) {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private val items = mutableListOf<WorkoutSession>()
    private val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext().applicationContext)
        listView = view.findViewById(R.id.lv_sessions)

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = items[position].id
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_session, parent, false)
                val s = items[position]
                v.findViewById<TextView>(R.id.tv_title).text = s.title
                v.findViewById<TextView>(R.id.tv_time).text =
                    "${sdf.format(Date(s.startedAt))} – " + if (s.endedAt == 0L) "ongoing" else sdf.format(Date(s.endedAt))
                v.findViewById<TextView>(R.id.tv_meta).text =
                    "Sets: ${s.completedSets}/${s.totalSets} • Coins: ${s.coinsEarned}"
                return v
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, pos, _ ->
            val sel = items[pos]
            startActivity(Intent(requireContext(), SessionDetailActivity::class.java).putExtra("SESSION_ID", sel.id))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(requireContext()).first()
            val sessions = withContext(Dispatchers.IO) { db.workoutSessionDao().getCompletedByUser(uid) }
            items.clear()
            items.addAll(sessions)
            adapter.notifyDataSetChanged()
        }
    }
}
