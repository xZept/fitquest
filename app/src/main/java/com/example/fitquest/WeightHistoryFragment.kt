package com.example.fitquest

import android.os.Bundle
import android.view.View
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.WeightLog
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeightHistoryFragment : Fragment(R.layout.fragment_weight_history) {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private val items = mutableListOf<WeightLog>()
    private val fmt = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext().applicationContext)
        listView = view.findViewById(R.id.lv_weight_history)

        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = items[position].id
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_weight_log, parent, false)
                val row = items[position]
                v.findViewById<TextView>(R.id.tv_weight).text = "${row.weightKg}"
                v.findViewById<TextView>(R.id.tv_time).text = fmt.format(Date(row.loggedAt))
                return v
            }
        }
        listView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(requireContext()).first()
            val data = withContext(Dispatchers.IO) { db.weightLogDao().getAll(uid) }
            items.clear(); items.addAll(data)
            (listView.adapter as BaseAdapter).notifyDataSetChanged()
        }
    }
}
