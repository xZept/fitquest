package com.example.fitquest

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.fitquest.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.fitquest.database.WorkoutSetLog

class SessionDetailActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private val items = mutableListOf<WorkoutSetLog>()
    private val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    private lateinit var pressAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)
        hideSystemBars()

        db = AppDatabase.getInstance(applicationContext)
        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        val sessionId = intent.getLongExtra("SESSION_ID", -1L)
        if (sessionId == -1L) { finish(); return }

        listView = findViewById(R.id.lv_logs)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = items[position].id
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_set_log, parent, false)
                val l = items[position]
                v.findViewById<TextView>(R.id.tv_ex).text = l.exerciseName
                v.findViewById<TextView>(R.id.tv_set).text = "Set ${l.setNumber} • ${l.repsMin}-${l.repsMax} reps"
                v.findViewById<TextView>(R.id.tv_load).text = "${l.loadType}: ${l.loadValueText}"
                v.findViewById<TextView>(R.id.tv_time).text = sdf.format(Date(l.loggedAt))
                return v
            }
        }
        listView.adapter = adapter

        lifecycleScope.launch {
            val logs = db.workoutSetLogDao().getForSession(sessionId)
            withContext(Dispatchers.Main) {
                items.clear(); items.addAll(logs); adapter.notifyDataSetChanged()
            }
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            it.startAnimation(pressAnim)
            onBackPressedDispatcher.onBackPressed() // or: finish()
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
