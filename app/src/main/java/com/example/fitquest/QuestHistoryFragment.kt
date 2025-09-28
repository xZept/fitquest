package com.example.fitquest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.ToggleButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.ActiveQuest
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.QuestHistory
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestHistoryFragment : Fragment(R.layout.fragment_quest_history) {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private val items = mutableListOf<QuestHistory>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext().applicationContext)
        listView = view.findViewById(R.id.lv_qhistory)

        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = items[position].id
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_quest_history, parent, false)
                val row = items[position]

                v.findViewById<TextView>(R.id.tv_title).text = row.title

                val pin = v.findViewById<ToggleButton>(R.id.btn_pin)
                val take = v.findViewById<Button>(R.id.btn_take_again)

                pin.setOnCheckedChangeListener(null)
                pin.isChecked = row.pinned
                pin.setOnCheckedChangeListener { _, isChecked ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.questHistoryDao().setPinned(row.id, isChecked)
                        db.questHistoryDao().pruneUnpinned(row.userId)
                        refresh()
                    }
                }

                take.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = requireContext()
                        val uid = DataStoreManager.getUserId(ctx).first()

                        // 1) Still enforce single-active-quest rule
                        val active = withContext(Dispatchers.IO) { db.activeQuestDao().getActiveForUser(uid) }
                        if (active != null) {
                            Toast.makeText(ctx, "Finish or abandon your current quest first.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        withContext(Dispatchers.IO) {
                            db.activeQuestDao().upsert(
                                ActiveQuest(
                                    userId = uid,
                                    split = row.split,
                                    modifier = row.modifier,
                                    exercises = row.exercises,
                                    startedAt = System.currentTimeMillis()
                                )
                            )

                            db.questHistoryDao().touch(uid, row.key)
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
    }

    private suspend fun refresh() {
        val uid = DataStoreManager.getUserId(requireContext()).first()
        val data = withContext(Dispatchers.IO) { db.questHistoryDao().getAllOrdered(uid) }
        withContext(Dispatchers.Main) {
            items.clear(); items.addAll(data)
            (listView.adapter as BaseAdapter).notifyDataSetChanged()
        }
    }
}
