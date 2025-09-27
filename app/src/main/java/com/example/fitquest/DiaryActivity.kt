package com.example.fitquest

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.fitquest.database.WorkoutSessionEntity

class DiaryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private val items = mutableListOf<WorkoutSessionEntity>()
    private val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)

        listView = findViewById(R.id.lv_sessions)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = items[position].id
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
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
            startActivity(Intent(this, SessionDetailActivity::class.java).putExtra("SESSION_ID", sel.id))
        }

        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@DiaryActivity).first()
            val sessions = db.workoutSessionDao().getAllByUser(uid)
            withContext(Dispatchers.Main) {
                items.clear()
                items.addAll(sessions)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
