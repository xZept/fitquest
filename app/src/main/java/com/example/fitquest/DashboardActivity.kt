package com.example.fitquest

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import com.example.fitquest.utils.TipsLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.WorkoutSessionDao
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.fitquest.repository.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import android.content.pm.PackageManager
import android.app.AlarmManager
import android.provider.Settings
import android.net.Uri
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts



class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView
    private lateinit var pressAnim: android.view.animation.Animation
    private var bgAnim: SpriteSheetDrawable? = null
    private lateinit var db: AppDatabase
    private lateinit var chart: BarChart

    private val splits = listOf("Push", "Pull", "Legs", "Upper")
    private lateinit var quickAction: ImageButton
    private var hasActiveQuest: Boolean = false

    private lateinit var tvKcal: TextView
    private lateinit var tvProtein: TextView
    private lateinit var tvWorkouts: TextView
    private lateinit var cardDaily: View
    private lateinit var pbKcal: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var pbProtein: com.google.android.material.progressindicator.LinearProgressIndicator

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Only schedule after user grants notifications on Android 13+
            if (granted) {
                WeightReminderScheduler.scheduleNext6am(this)
                // (Optional) debug:
                // WeightReminderScheduler.scheduleInSeconds(this, 15)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Ask for exact alarm special access (Android 12L+/13)
        requestExactAlarmIfNeeded()

        // Ask for POST_NOTIFICATIONS (Android 13+) and schedule once granted
        ensureNotificationPermissionThenSchedule()

        if (BuildConfig.DEBUG) {
            findViewById<View>(R.id.card_daily_summary)?.setOnLongClickListener {
                WeightReminderScheduler.scheduleInSeconds(this, 15)
                Toast.makeText(this, "Test: reminder in 15s", Toast.LENGTH_SHORT).show()
                true
            }
            findViewById<View>(R.id.btn_quick_action)?.setOnLongClickListener {
                WeightReminderScheduler.scheduleInSeconds(this, 15)
                Toast.makeText(this, "Test weight reminder in 15s", Toast.LENGTH_SHORT).show()
                true
            }
        }




        db = AppDatabase.getInstance(applicationContext)
        chart = findViewById(R.id.chart_splits)

        setupChartAppearance()
        loadDataAndRender()
        refreshDailySummary()



        // Animated spritesheet background (repo)
        val bgView = findViewById<ImageView>(R.id.dashboard_bg)
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_page_dashboard_spritesheet0, opts)
        bgAnim = SpriteSheetDrawable(
            sheet = sheet,
            rows = 1,
            cols = 12,
            fps = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )
        bgView.setImageDrawable(bgAnim)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        tvKcal = findViewById(R.id.tv_kcal)
        tvProtein = findViewById(R.id.tv_protein)
        tvWorkouts = findViewById(R.id.tv_workouts)
        cardDaily = findViewById(R.id.card_daily_summary)
        pbKcal = findViewById(R.id.pb_kcal)
        pbProtein = findViewById(R.id.pb_protein)




        attachMiniSeeder()

        cardDaily.setOnClickListener {
            startActivity(Intent(this@DashboardActivity, WeeklyReviewActivity::class.java))
        }



        // Hide system navigation (repo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        val root = findViewById<View>(R.id.dashboard_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(Type.systemBars())
            v.setPadding(0, 0, 0, sys.bottom)
            insets
        }

        dashboardTip = findViewById(R.id.dashboardTip)
        val tips = TipsLoader.loadTips(this)
        val general = tips.filter { it.category == "general" }
        dashboardTip.text =
            if (general.isNotEmpty()) {
                val dayIndex = (System.currentTimeMillis() / 86_400_000).toInt()
                general[dayIndex % general.size].tip
            } else {
                "Stay strong and keep going!"
            }

        quickAction = findViewById(R.id.btn_quick_action)
        quickAction.setOnClickListener {
            it.startAnimation(pressAnim)
            lifecycleScope.launch {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                val active = db.activeQuestDao().getActiveForUser(uid)
                if (active != null) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "You already have an active quest. Finish or abandon it first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                startActivity(Intent(this@DashboardActivity, QuestGeneratorActivity::class.java))
                overridePendingTransition(0, 0)
            }
        }

        setupNavigationBar()
    }

    override fun onStart() {
        super.onStart()
        bgAnim?.start()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
            hasActiveQuest = db.activeQuestDao().getActiveForUser(uid) != null
            quickAction.isEnabled = !hasActiveQuest
            quickAction.alpha = if (hasActiveQuest) 0.5f else 1f
        }
    }

    override fun onStop() {
        bgAnim?.stop()
        super.onStop()
    }




    private fun setupChartAppearance() {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(false)          // disables all touch: drag, scale, highlight
        chart.setDragEnabled(false)
        chart.setScaleEnabled(false)
        chart.setScaleXEnabled(false)
        chart.setScaleYEnabled(false)
        chart.setPinchZoom(false)
        chart.setDoubleTapToZoomEnabled(false)
        chart.setHighlightPerTapEnabled(false)
        // For safety on BarLineChartBase charts:
        chart.setHighlightPerDragEnabled(false)

        chart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f

            textColor = Color.WHITE     //  label color
            textSize  = 12f             // label size
            gridColor = Color.parseColor("#22FFFFFF") // optional light grid
            axisLineColor = Color.TRANSPARENT
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = IndexAxisValueFormatter(splits)
            labelRotationAngle = 0f


            textColor = Color.WHITE     //  label color
            textSize  = 12f
        }

        chart.data?.let { data ->
            data.setValueTextColor(Color.WHITE)  // 👈 bar value color
            data.setValueTextSize(12f)           // 👈 bar value size
            chart.invalidate()
        }
        chart.setNoDataText("No completed sets yet.")
        chart.setFitBars(true)

        // Space between the chart content and its edges (left, top, right, bottom) in px
        chart.setExtraOffsets(12f, 12f, 12f, 12f)

//// Or, if you want to fully override auto-calculated offsets:
//        chart.setViewPortOffsets(32f, 24f, 32f, 32f)



    }

    private fun loadDataAndRender() {
        lifecycleScope.launch {
            val userId = DataStoreManager.getUserId(this@DashboardActivity).first()
            if (userId == -1) return@launch

            val sessions = withContext(Dispatchers.IO) {
                db.workoutSessionDao().getCompletedSessionsForUser(userId)
            }

            // ✅ Count sessions per split (NOT sets)
            val counts = mutableMapOf("Push" to 0, "Pull" to 0, "Legs" to 0, "Upper" to 0)
            sessions.forEach { s ->
                extractSplitFromTitle(s.title)?.let { split ->
                    if (split in counts) counts[split] = counts[split]!! + 1
                }
            }

            val entries = splits.mapIndexed { idx, label ->
                com.github.mikephil.charting.data.BarEntry(idx.toFloat(), (counts[label] ?: 0).toFloat())
            }

            val dataSet = BarDataSet(entries, "Splits").apply {
                setDrawValues(true)
                color = Color.parseColor("#593A07")   // ← set bar color here
                valueTextColor = Color.WHITE
                valueTextSize = 12f
            }

            val data = com.github.mikephil.charting.data.BarData(dataSet).apply {
                barWidth = 0.6f
                setValueTextSize(12f)
                // show integers on bar labels
                setValueFormatter(object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getBarLabel(e: com.github.mikephil.charting.data.BarEntry?): String {
                        return e?.y?.toInt()?.toString() ?: "0"
                    }
                })
            }
            chart.axisLeft.granularity = 1f
            chart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(splits)
            chart.data = data
            chart.invalidate()
            chart.animateY(700)

        }

    }

    private fun todayDayKey(): Int {
        val z = ZoneId.of("Asia/Manila")
        val now = java.time.ZonedDateTime.now(z)
        return now.year * 10_000 + now.monthValue * 100 + now.dayOfMonth
    }


    private fun attachMiniSeeder() {
        if (!BuildConfig.DEBUG) return
        cardDaily.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                if (uid == -1) return@launch
                val dk = todayDayKey()

                // ⚠️ Adjust MacroDiary fields to match your entity exactly
                db.macroDiaryDao().upsert(
                    com.example.fitquest.database.MacroDiary(
                        id = 0, userId = uid, dayKey = dk,
                        calories = 2100, planCalories = 2000,
                        protein = 140,  planProtein = 130,
                        carbs = 0, planCarbs = 0,
                        fat = 0,  planFat = 0
                    )
                )

                // Optional: seed one finished workout today (adjust to your entity/DAO)
                // Optional: seed one finished workout today (adjust to your entity/DAO)
                try {
                    val now = System.currentTimeMillis()
                    db.workoutSessionDao().insert(
                        com.example.fitquest.database.WorkoutSession(
                            id = 0,                       // <-- use the actual PK name (likely 'id')
                            userId = uid,
                            title = "Push • General",
                            startedAt = now - 55*60_000L,
                            endedAt = now,
                            totalSets = 12,               // <-- required by your entity
                            completedSets = 12,           // <-- required by your entity
                            coinsEarned = 10
                            // add any other required non-null fields your entity has (e.g., notes = null)
                        )
                    )
                } catch (_: Throwable) { /* if your DAO uses upsert/other signature, adjust the call */ }

            }
            lifecycleScope.launch {
                Toast.makeText(this@DashboardActivity, "Seeded today", Toast.LENGTH_SHORT).show()
                refreshDailySummary()
            }
            true
        }
    }



    private fun fmtDev(dev: Int): String = when {
        dev == 0 -> "±0"
        dev > 0  -> "+$dev"
        else     -> dev.toString()
    }

    private fun ensureNotificationPermissionThenSchedule() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Permission not needed or already granted
        WeightReminderScheduler.scheduleNext6am(this)
        // (Optional) debug:
        // WeightReminderScheduler.scheduleInSeconds(this, 15)
    }


    private fun refreshDailySummary() {
        lifecycleScope.launch {
            try {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                if (uid == -1) return@launch

                // NOTE: use the package where you actually put ProgressRepository
                // You imported: com.example.fitquest.repository.ProgressRepository (keep that)
                val repo = ProgressRepository(db)

                val daily = repo.dailySummary(uid, todayDayKey())
                tvKcal.text = "${daily.calories} / ${daily.planCalories} kcal (${fmtDev(daily.kcalDeviation)})"
                tvProtein.text = "Protein: ${daily.protein} / ${daily.planProtein} g (${daily.proteinHitPct}%)"
                tvWorkouts.text = "Workouts: ${daily.workoutsCompletedToday} today"
                val ok   = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_ok)
                val over = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_over)

// Calories bar
                if (daily.planCalories > 0) {
                    pbKcal.max = daily.planCalories.coerceAtLeast(1)
                    pbKcal.setProgressCompat(daily.calories.coerceIn(0, pbKcal.max), true)
                    pbKcal.setIndicatorColor(if (daily.calories > daily.planCalories) over else ok)
                    pbKcal.visibility = View.VISIBLE
                } else {
                    pbKcal.visibility = View.GONE
                }

// Protein bar
                if (daily.planProtein > 0) {
                    pbProtein.max = daily.planProtein.coerceAtLeast(1)
                    pbProtein.setProgressCompat(daily.protein.coerceIn(0, pbProtein.max), true)
                    pbProtein.setIndicatorColor(if (daily.protein > daily.planProtein) over else ok)
                    pbProtein.visibility = View.VISIBLE
                } else {
                    pbProtein.visibility = View.GONE
                }

            } catch (t: Throwable) {
                // Fail quietly so dashboard never crashes
                tvKcal.text = "—"
                tvProtein.text = "—"
                tvWorkouts.text = "—"
            }
        }
    }


    /** Extracts the split ("Push","Pull","Legs","Upper") from a title like "Push • General". */
    private fun extractSplitFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val head = title.split("•").first().trim().lowercase()
        return when {
            head.startsWith("push")  -> "Push"
            head.startsWith("pull")  -> "Pull"
            head.startsWith("legs")  -> "Legs"
            head.startsWith("upper") -> "Upper"
            // if you sometimes use "lower", map it if needed:
            // head.startsWith("lower") -> "Legs"
            else -> null
        }
    }

    private fun requestExactAlarmIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }




    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_workout).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, WorkoutActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_shop).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ShopActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
    }


}
