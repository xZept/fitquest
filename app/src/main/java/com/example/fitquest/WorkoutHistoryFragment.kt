package com.example.fitquest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.ActiveQuest
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

                v.setOnClickListener {
                    startActivity(
                        Intent(requireContext(), SessionDetailActivity::class.java)
                            .putExtra("SESSION_ID", s.id)
                    )
                }

                // --- Buttons ---
                val btnTake = v.findViewById<ImageButton>(R.id.btn_take_again)
                val btnPin  = v.findViewById<ImageButton>(R.id.btn_pin)

                btnPin.isSelected = s.pinned
                btnPin.setOnClickListener {
                    val nowPinned = !btnPin.isSelected
                    btnPin.isSelected = nowPinned
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.workoutSessionDao().setPinned(s.id, nowPinned)
                        val uid = DataStoreManager.getUserId(requireContext()).first()
                        val data = db.workoutSessionDao().getCompletedByUserOrdered(uid)
                        withContext(Dispatchers.Main) {
                            items.clear()
                            items.addAll(data)
                            (listView.adapter as BaseAdapter).notifyDataSetChanged()
                        }
                    }
                }

                btnTake.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = requireContext()
                        val uid = DataStoreManager.getUserId(ctx).first()

                        val active = withContext(Dispatchers.IO) { db.activeQuestDao().getActiveForUser(uid) }
                        if (active != null) {
                            Toast.makeText(ctx, "Finish or abandon your current workout first.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        val template = withContext(Dispatchers.IO) { db.questHistoryDao().getLastByTitle(uid, s.title) }
                        if (template == null) {
                            Toast.makeText(ctx, "No template found for '${s.title}'.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        withContext(Dispatchers.IO) {
                            db.activeQuestDao().upsert(
                                ActiveQuest(
                                    userId = uid,
                                    split = template.split,
                                    modifier = template.modifier,
                                    exercises = template.exercises,
                                    startedAt = System.currentTimeMillis()
                                )
                            )
                            db.questHistoryDao().touch(uid, template.key)
                            db.questHistoryDao().pruneUnpinned(uid)
                        }

                        startActivity(Intent(ctx, WorkoutActivity::class.java))
                    }
                }

                return v
            }
        }
        listView.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch { refresh() }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val sel = items[pos]
            startActivity(
                Intent(requireContext(), SessionDetailActivity::class.java)
                    .putExtra("SESSION_ID", sel.id)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(requireContext()).first()
            val sessions = withContext(Dispatchers.IO) {
                db.workoutSessionDao().getCompletedByUser(uid)
            }
            items.clear()
            items.addAll(sessions)
            adapter.notifyDataSetChanged()
        }

        listView.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch { refresh() }

    }
    private suspend fun refresh() {
        val uid = DataStoreManager.getUserId(requireContext()).first()
        val data = withContext(Dispatchers.IO) { db.workoutSessionDao().getCompletedByUserOrdered(uid) }
        withContext(Dispatchers.Main) {
            items.clear()
            items.addAll(data)
            (listView.adapter as BaseAdapter).notifyDataSetChanged()
        }
    }
}

