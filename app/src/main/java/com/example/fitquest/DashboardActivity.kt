package com.example.fitquest

import android.content.Intent
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import androidx.core.view.isVisible
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.time.format.DateTimeFormatter
import com.example.fitquest.utils.localDateFromDayKey
import java.time.Instant
import androidx.core.content.ContextCompat
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.getkeepsafe.taptargetview.TapTargetView
import kotlin.math.max


private const val TOUR_PREFS = "onboarding"
private const val TOUR_DONE_KEY_PREFIX = "dash_tour_done_v2_u_"
private const val FORCE_TOUR = false

private val tourShownUsersThisProcess = mutableSetOf<Int>()

class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView
    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private lateinit var chart: BarChart

    private val splits = listOf("Push", "Pull", "Legs", "Upper")
    private lateinit var quickAction: ImageButton
    private var hasActiveQuest: Boolean = false

    private lateinit var tvKcal: TextView
    private lateinit var tvWorkouts: TextView
    private lateinit var cardDaily: View
    private lateinit var weightChart: com.github.mikephil.charting.charts.LineChart
    private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("MMM d")

    private lateinit var pbKcalCircle: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var tvKcalCenter: android.widget.TextView

    private lateinit var tvWeeklyWorkouts: TextView
    private lateinit var tvWeeklyRange: TextView

    private lateinit var tvWeeklyKcalLabel: TextView
    private lateinit var pbWeeklyKcalCircle: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var tvWeeklyKcalCenter: TextView
    private lateinit var tvWeeklyNextRange: TextView




    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ReminderScheduler.scheduleNext6am(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        requestExactAlarmIfNeeded()

        ensureNotificationPermissionThenSchedule()

        if (BuildConfig.DEBUG) {
            findViewById<View>(R.id.card_daily_summary)?.setOnLongClickListener {
                ReminderScheduler.scheduleInSeconds(this, 15)
                Toast.makeText(this, "Test: reminder in 15s", Toast.LENGTH_SHORT).show()
                true
            }
            findViewById<View>(R.id.btn_quick_action)?.setOnLongClickListener {
                ReminderScheduler.scheduleInSeconds(this, 15)
                Toast.makeText(this, "Test weight reminder in 15s", Toast.LENGTH_SHORT).show()
                true
            }
        }

        findViewById<ImageButton>(R.id.btn_diary).apply {
            isEnabled = true
            setOnClickListener {
                startActivity(Intent(this@DashboardActivity, DiaryHistoryActivity::class.java))
            }
            // debug
            setOnLongClickListener {
                Toast.makeText(this@DashboardActivity, "Diary tapped", Toast.LENGTH_SHORT).show()
                true
            }
        }

        tvWeeklyNextRange   = findViewById(R.id.tv_weekly_next_range)
        tvWeeklyKcalLabel   = findViewById(R.id.tv_weekly_kcal_label)
        pbWeeklyKcalCircle  = findViewById(R.id.pb_weekly_kcal_circle)
        tvWeeklyKcalCenter  = findViewById(R.id.tv_weekly_kcal_center)
        tvWeeklyRange = findViewById(R.id.tv_weekly_range)
        tvWeeklyWorkouts = findViewById(R.id.tv_weekly_workouts)
        pbKcalCircle = findViewById(R.id.pb_kcal_circle)
        tvKcalCenter = findViewById(R.id.tv_kcal_center)
        db = AppDatabase.getInstance(applicationContext)
        chart = findViewById(R.id.chart_splits)
        weightChart = findViewById(R.id.weightChart)
        setupChart(weightChart)
        setupChartAppearance()
        loadDataAndRender()
        refreshDailySummary()


        val header = findViewById<LinearLayout>(R.id.header_bar)
        header.bringToFront()


        val basePadLeft   = header.paddingLeft
        val basePadTop    = header.paddingTop
        val basePadRight  = header.paddingRight
        val basePadBottom = header.paddingBottom

        // Apply status-bar inset
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(basePadLeft, basePadTop + topInset, basePadRight, basePadBottom)
            insets
        }

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        tvKcal = findViewById(R.id.tv_kcal)
        tvWorkouts = findViewById(R.id.tv_workouts)
        cardDaily = findViewById(R.id.card_daily_summary)

        attachMiniSeeder()

        cardDaily.setOnClickListener {
            startActivity(Intent(this@DashboardActivity, WeeklyReviewActivity::class.java))
        }



        // Hide system navigation
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
        ViewCompat.setOnApplyWindowInsetsListener(root) { v: View, insets: WindowInsetsCompat ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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

    companion object {
        const val ACTION_WEIGHT_LOGGED = "com.example.fitquest.WEIGHT_LOGGED"
        private var tourShownThisProcess = false   // prevent re-entry while app is alive

    }

    private val weightLoggedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadWeightSeries()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_WEIGHT_LOGGED)
        ContextCompat.registerReceiver(
            this,
            weightLoggedReceiver,
            filter,
           ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    override fun onResume() {
        refreshWeeklySummary()
        refreshDailySummary()
        super.onResume()
        loadWeightSeries()
        backfillInitialWeightIfMissing()

        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
            hasActiveQuest = db.activeQuestDao().getActiveForUser(uid) != null
            quickAction.isEnabled = !hasActiveQuest
            quickAction.alpha = if (hasActiveQuest) 0.5f else 1f
            showDashboardTourIfNeeded(uid)
        }
    }


    override fun onStop() {
        super.onStop()
        unregisterReceiver(weightLoggedReceiver)
    }

    private fun setupChartAppearance() {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDragEnabled(false)
        chart.setScaleEnabled(false)
        chart.setScaleXEnabled(false)
        chart.setScaleYEnabled(false)
        chart.setPinchZoom(false)
        chart.setDoubleTapToZoomEnabled(false)
        chart.setHighlightPerTapEnabled(false)
        chart.setHighlightPerDragEnabled(false)

        chart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f

            textColor = Color.parseColor("#000000")
            textSize  = 12f
            gridColor = Color.parseColor("#11000000")
            axisLineColor = Color.TRANSPARENT
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = IndexAxisValueFormatter(splits)
            labelRotationAngle = 0f

            textColor = Color.parseColor("#000000")
            textSize  = 12f
        }

        chart.data?.let { data ->
            data.setValueTextColor(Color.BLACK)
            data.setValueTextSize(12f)
            chart.invalidate()
        }
        chart.setNoDataText("No completed sets yet.")
        chart.setNoDataTextColor(Color.parseColor("#000000"))
        chart.setFitBars(true)
        chart.setExtraOffsets(12f, 12f, 12f, 12f)


    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.axisLeft.setDrawGridLines(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.legend.isEnabled = false
    }

    private fun loadWeightSeries() {
        lifecycleScope.launch {
            val userId = DataStoreManager.getUserId(this@DashboardActivity).first()
            val rows = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@DashboardActivity)
                    .weightLogDao()
                    .getAll(userId)
                    .sortedBy { it.loggedAt }
            }

            if (rows.isEmpty()) {
                weightChart.isVisible = false
                return@launch
            } else {
                weightChart.isVisible = true
            }

            val weightRed = Color.parseColor("#C73212")
            val weightFill = Color.parseColor("#33EF4444")

            val entries = rows.map { r ->
                val x = Instant.ofEpochMilli(r.loggedAt)
                    .atZone(ZoneId.of("Asia/Manila"))
                    .toLocalDate()
                    .toEpochDay()
                    .toFloat()
                Entry(x, r.weightKg)
            }.toMutableList()

            if (entries.size == 1) {
                val e = entries.first()
                entries.add(0, Entry(e.x - 1f, e.y))
            }


            val ds = LineDataSet(entries, "Weight (kg)").apply {
                setDrawCircles(true)
                circleRadius = 3f
                lineWidth = 2f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawValues(false)
                setDrawFilled(true)


                color = weightRed
                setCircleColor(weightRed)
                fillColor = weightFill
                highLightColor = weightRed
            }

            weightChart.data = LineData(ds)

            weightChart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return LocalDate.ofEpochDay(value.toLong()).format(dateFmt)
                }
            }

            val maxY = rows.maxOf { it.weightKg }.toFloat()
            val goalKg = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@DashboardActivity)
                    .userProfileDAO()
                    .getProfileByUserId(userId)
                    ?.goalWeight
                    ?.toDouble()
            } ?: 0.0

            val goalY = goalKg.toFloat()
            val maxForAxis = maxOf(maxY, if (goalY > 0f) goalY else 0f)
            val headroom = (maxForAxis * 0.05f).coerceAtLeast(1f)

            weightChart.axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = maxForAxis + headroom
                granularity = 1f
                setDrawZeroLine(true)
            }

            weightChart.axisLeft.removeAllLimitLines()
            if (goalY > 0f) {
                val goalBlue = Color.parseColor("#0F5ADB")
                val ll = LimitLine(goalY, "Goal").apply {
                    lineWidth = 1.5f
                    enableDashedLine(10f, 10f, 0f)
                    lineColor = goalBlue
                    textColor = goalBlue
                }
                weightChart.axisLeft.addLimitLine(ll)
            }

            weightChart.clear()
            weightChart.data = LineData(ds)
            weightChart.data.notifyDataChanged()
            weightChart.notifyDataSetChanged()
            weightChart.invalidate()

        }
    }


    private fun loadDataAndRender() {
        lifecycleScope.launch {
            val userId = DataStoreManager.getUserId(this@DashboardActivity).first()
            if (userId == -1) return@launch

            val sessions = withContext(Dispatchers.IO) {
                db.workoutSessionDao().getCompletedSessionsForUser(userId)
            }

            val counts = mutableMapOf("Push" to 0, "Pull" to 0, "Legs" to 0, "Upper" to 0)
            sessions.forEach { s ->
                extractSplitFromTitle(s.title)?.let { split ->
                    if (split in counts) counts[split] = counts[split]!! + 1
                }
            }

            val entries = splits.mapIndexed { idx, label ->
                com.github.mikephil.charting.data.BarEntry(idx.toFloat(), (counts[label] ?: 0).toFloat())
            }

            val black = Color.parseColor("#000000")


            val dataSet = BarDataSet(entries, "Splits").apply {
                setDrawValues(true)
                color = Color.parseColor("#593A07")
                valueTextColor = black
                valueTextSize = 12f
            }

            val data = com.github.mikephil.charting.data.BarData(dataSet).apply {
                barWidth = 0.6f
                setValueTextSize(12f)
                setValueTextColor(black)
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

    private fun dayKeyNDaysAgo(n: Int): Int {
        val z = java.time.ZoneId.of("Asia/Manila")
        val d = java.time.LocalDate.now(z).minusDays(n.toLong())
        return d.year * 10_000 + d.monthValue * 100 + d.dayOfMonth
    }

    private fun attachMiniSeeder() {
        if (!BuildConfig.DEBUG) return
        cardDaily.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                if (uid == -1) return@launch
                val dk = todayDayKey()

                db.macroDiaryDao().upsert(
                    com.example.fitquest.database.MacroDiary(
                        id = 0, userId = uid, dayKey = dk,
                        calories = 2100, planCalories = 2000,
                        protein = 140,  planProtein = 130,
                        carbs = 0, planCarbs = 0,
                        fat = 0,  planFat = 0
                    )
                )

                try {
                    val now = System.currentTimeMillis()
                    db.workoutSessionDao().insert(
                        com.example.fitquest.database.WorkoutSession(
                            id = 0,
                            userId = uid,
                            title = "Push • General",
                            startedAt = now - 55*60_000L,
                            endedAt = now,
                            totalSets = 12,
                            completedSets = 12,
                            coinsEarned = 10
                        )
                    )
                } catch (_: Throwable) {}

            }
            lifecycleScope.launch {
                Toast.makeText(this@DashboardActivity, "Seeded today", Toast.LENGTH_SHORT).show()
                refreshDailySummary()
            }
            true
        }
    }

    private fun refreshWeeklySummary() {
        lifecycleScope.launch {
            try {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                if (uid == -1) return@launch

                val zone = java.time.ZoneId.of("Asia/Manila")
                val today = java.time.LocalDate.now(zone)

                val (startDate, endDate) = currentMonthBucket(today)
                val (nextStart, nextEnd) = nextMonthBucket(today)

                val ok   = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_ok)
                val over = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_over)

                // Set timeframe labels
                tvWeeklyRange.text = formatDateRange(startDate, endDate)
                tvWeeklyNextRange.text = "Next: ${formatDateRange(nextStart, nextEnd)}"

                // Build dayKeys for all days in the current bucket
                val dayKeys = generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(endDate) }
                    .map { d -> d.year * 10_000 + d.monthValue * 100 + d.dayOfMonth }
                    .toList()

                val repo = ProgressRepository(db)
                val daily = withContext(Dispatchers.IO) {
                    dayKeys.map { dk -> repo.dailySummary(uid, dk) }
                }

                val daysCount = daily.size.coerceAtLeast(1)
                val totalCals = daily.sumOf { it.calories }
                val totalPlan = daily.sumOf { it.planCalories }
                val workouts  = daily.sumOf { it.workoutsCompletedToday }

                val avgCals = (totalCals / daysCount)
                val avgPlan = (totalPlan / daysCount).coerceAtLeast(0)
                val dev = if (avgPlan > 0) (avgCals - avgPlan) else 0

                // timeframe labels
                tvWeeklyRange.text = formatDateRange(startDate, endDate)
                tvWeeklyNextRange.text = "Next: ${formatDateRange(nextStart, nextEnd)}"

                tvWeeklyKcalLabel.text =
                    if (avgPlan > 0) "Avg: $avgCals / $avgPlan kcal (${fmtDev(dev)})"
                    else "Avg: $avgCals kcal"

                if (avgPlan > 0) {
                    pbWeeklyKcalCircle.setIndicatorColor(if (avgCals > avgPlan) over else ok)
                    pbWeeklyKcalCircle.max = avgPlan.coerceAtLeast(1)
                    pbWeeklyKcalCircle.setProgressCompat(avgCals.coerceIn(0, pbWeeklyKcalCircle.max), true)

                    val pct = ((avgCals * 100.0) / avgPlan).toInt().coerceAtLeast(0)
                    tvWeeklyKcalCenter.text = "$pct%"
                    pbWeeklyKcalCircle.visibility = View.VISIBLE
                } else {
                    pbWeeklyKcalCircle.visibility = View.GONE
                    tvWeeklyKcalCenter.text = ""
                }

                if (avgPlan > 0) {
                    pbWeeklyKcalCircle.setIndicatorColor(if (avgCals > avgPlan) over else ok)
                    pbWeeklyKcalCircle.max = avgPlan.coerceAtLeast(1)
                    pbWeeklyKcalCircle.setProgressCompat(avgCals.coerceIn(0, pbWeeklyKcalCircle.max), true)

                    val pct = ((avgCals * 100.0) / avgPlan).toInt().coerceAtLeast(0)
                    tvWeeklyKcalCenter.text = "$pct%"
                    pbWeeklyKcalCircle.visibility = View.VISIBLE
                } else {
                    pbWeeklyKcalCircle.visibility = View.GONE
                    tvWeeklyKcalCenter.text = ""
                }

                tvWeeklyWorkouts.text = workouts.toString()

                tvWeeklyRange.text = formatDateRange(startDate, endDate)
                tvWeeklyNextRange.text = "Next: ${formatDateRange(nextStart, nextEnd)}"

            } catch (_: Throwable) {
                tvWeeklyRange.text       = "—"
                tvWeeklyNextRange.text   = "Next: —"
                tvWeeklyKcalLabel.text   = "Avg: —"
                tvWeeklyKcalCenter.text  = ""
                pbWeeklyKcalCircle.visibility = View.GONE
                tvWeeklyWorkouts.text    = "—"
            }
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
        ReminderScheduler.scheduleNext6am(this)
    }




    private fun refreshDailySummary() {
        lifecycleScope.launch {
            try {
                val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
                if (uid == -1) return@launch

                val repo = ProgressRepository(db)
                val daily = repo.dailySummary(uid, todayDayKey())

                tvKcal.text = "${daily.calories} / ${daily.planCalories} kcal (${fmtDev(daily.kcalDeviation)})"
                tvWorkouts.text = "${daily.workoutsCompletedToday}"

                val ok   = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_ok)
                val over = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, R.color.progress_over)

                if (daily.planCalories > 0) {
                    pbKcalCircle.setIndicatorColor(if (daily.calories > daily.planCalories) over else ok)

                    pbKcalCircle.max = daily.planCalories.coerceAtLeast(1)
                    val clamped = daily.calories.coerceIn(0, pbKcalCircle.max)
                    pbKcalCircle.setProgressCompat(clamped, /*animate=*/true)

                    val pct = ((daily.calories * 100.0) / daily.planCalories).toInt()
                    tvKcalCenter.text = "${pct.coerceAtLeast(0)}%"

                    pbKcalCircle.visibility = View.VISIBLE
                } else {
                    pbKcalCircle.visibility = View.GONE
                    tvKcalCenter.text = ""
                }

            } catch (_: Throwable) {
                tvKcal.text = "—"
                tvWorkouts.text = "—"
                pbKcalCircle.visibility = View.GONE
            }
        }
    }

    private fun extractSplitFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val head = title.split("•").first().trim().lowercase()
        return when {
            head.startsWith("push")  -> "Push"
            head.startsWith("pull")  -> "Pull"
            head.startsWith("legs")  -> "Legs"
            head.startsWith("upper") -> "Upper"
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

    private fun backfillInitialWeightIfMissing() {
        lifecycleScope.launch(Dispatchers.IO) {
            val uid = DataStoreManager.getUserId(this@DashboardActivity).first()
            if (uid == -1) return@launch

            val db = AppDatabase.getInstance(applicationContext)
            val hasAny = db.weightLogDao().getAll(uid).isNotEmpty()
            if (!hasAny) {
                val profile = db.userProfileDAO().getProfileByUserId(uid)
                val w = profile?.weight ?: 0
                if (w > 0) {
                    db.weightLogDao().insert(
                        com.example.fitquest.database.WeightLog(
                            userId = uid,
                            loggedAt = System.currentTimeMillis(),
                            weightKg = w.toFloat()
                        )
                    )
                }
            }
        }
    }

    private fun currentMonthBucket(today: java.time.LocalDate): Pair<java.time.LocalDate, java.time.LocalDate> {
        val dom = today.dayOfMonth
        val monthLen = today.lengthOfMonth()
        val startDay = ((dom - 1) / 7) * 7 + 1
        val endDay   = kotlin.math.min(startDay + 6, monthLen)
        return today.withDayOfMonth(startDay) to today.withDayOfMonth(endDay)
    }

    private fun nextMonthBucket(today: java.time.LocalDate): Pair<java.time.LocalDate, java.time.LocalDate> {
        val (curStart, curEnd) = currentMonthBucket(today)
        val monthLen = today.lengthOfMonth()
        val nextStartDom = curEnd.dayOfMonth + 1

        return if (nextStartDom <= monthLen) {
            val ns = today.withDayOfMonth(nextStartDom)
            val ne = today.withDayOfMonth(kotlin.math.min(nextStartDom + 6, monthLen))
            ns to ne
        } else {
            // move to next month: 1..min(7, len)
            val firstNext = today.plusMonths(1).withDayOfMonth(1)
            firstNext to firstNext.withDayOfMonth(kotlin.math.min(7, firstNext.lengthOfMonth()))
        }
    }

    private fun formatDateRange(start: java.time.LocalDate, end: java.time.LocalDate): String {
        val fmtMdy = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
        val fmtMd  = java.time.format.DateTimeFormatter.ofPattern("MMM d")

        return when {
            start.year != end.year -> "${start.format(fmtMdy)} – ${end.format(fmtMdy)}"
            start.month != end.month -> "${start.format(fmtMd)} – ${end.format(fmtMd)}, ${end.year}"
            else -> "${start.format(fmtMd)}–${end.dayOfMonth}, ${end.year}"
        }
    }

    private fun showDashboardTourIfNeeded(userId: Int) {
        if (userId <= 0) return

        val prefs = getSharedPreferences(TOUR_PREFS, MODE_PRIVATE)
        val userDoneKey = "$TOUR_DONE_KEY_PREFIX$userId"

        if (FORCE_TOUR && BuildConfig.DEBUG) {
            prefs.edit().remove(userDoneKey).apply()
            tourShownUsersThisProcess.remove(userId)
        }

        if (tourShownUsersThisProcess.contains(userId) || prefs.getBoolean(userDoneKey, false)) return

        val root    = findViewById<View>(R.id.dashboard_root)
        val vScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_content)
        val hScroll = findViewById<android.widget.HorizontalScrollView>(R.id.summary_scroller)

        val quick   = findViewById<View>(R.id.btn_quick_action)
        val diary   = findViewById<View>(R.id.btn_diary)
        val daily   = findViewById<View>(R.id.card_daily_summary)
        val weekly  = findViewById<View>(R.id.card_weekly_summary)
        val weight  = findViewById<View>(R.id.weightChart)
        val splits  = findViewById<View>(R.id.chart_splits)

        fun startWhenReady(attempt: Int = 0) {
            if (!hasWindowFocus()) { root.postDelayed({ startWhenReady(attempt) }, 120L); return }
            if (!root.isLaidOut) { root.post { startWhenReady(attempt) }; return }

            if (daily != null && hScroll != null) scrollHToCenter(hScroll, daily)

            val initialTargets = buildList {
                if (quick?.isLaidOut == true) add(
                    TapTarget.forView(quick, "Generate a workout quest from your plan.", "")
                        .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle()
                )
                if (diary?.isLaidOut == true) add(
                    TapTarget.forView(diary, "Review your logs and history.", "")
                        .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle()
                )
                if (daily?.isLaidOut == true) add(
                    TapTarget.forView(daily, "Calories ring and your workouts for today.", "")
                        .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle()
                )
                if (weight?.isShown == true && weight.isLaidOut) add(
                    TapTarget.forView(weight, "Tracks your logged weight over time.", "")
                        .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle()
                )
            }

            if (initialTargets.isEmpty()) {
                if (attempt < 8) { root.postDelayed({ startWhenReady(attempt + 1) }, 130L) }
                return
            }

            // Mark as shown for this user in this process
            tourShownUsersThisProcess.add(userId)

            val finishTour: () -> Unit = {
                prefs.edit().putBoolean(userDoneKey, true).apply()
            }

            val showRest: () -> Unit = {
                if (weekly != null && hScroll != null) {
                    hScroll.post {
                        scrollHToCenter(hScroll, weekly)
                        weekly.postDelayed({
                            TapTargetView.showFor(
                                this@DashboardActivity,
                                TapTarget.forView(weekly, "Averages and total workouts for the period.", "")
                                    .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle(),
                                object : TapTargetView.Listener() {
                                    override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                        if (splits != null && vScroll != null) {
                                            vScroll.post {
                                                scrollVToCenter(vScroll, splits)
                                                splits.postDelayed({
                                                    TapTargetView.showFor(
                                                        this@DashboardActivity,
                                                        TapTarget.forView(splits, "See Push / Pull / Legs / Upper counts.", "")
                                                            .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle(),
                                                        object : TapTargetView.Listener() {
                                                            override fun onTargetDismissed(v2: TapTargetView?, ui: Boolean) { finishTour() }
                                                        }
                                                    )
                                                }, 320L)
                                            }
                                        } else finishTour()
                                    }
                                }
                            )
                        }, 280L)
                    }
                } else if (splits != null && vScroll != null) {
                    vScroll.post {
                        scrollVToCenter(vScroll, splits)
                        splits.postDelayed({
                            TapTargetView.showFor(
                                this@DashboardActivity,
                                TapTarget.forView(splits, "See Push / Pull / Legs / Upper counts.", "")
                                    .cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle(),
                                object : TapTargetView.Listener() {
                                    override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) { finishTour() }
                                }
                            )
                        }, 320L)
                    }
                } else finishTour()
            }

            TapTargetSequence(this@DashboardActivity)
                .targets(initialTargets)
                .continueOnCancel(true)
                .listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() = showRest()
                    override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                    override fun onSequenceCanceled(lastTarget: TapTarget) = showRest()
                })
                .start()
        }

        root.postDelayed({ startWhenReady() }, 200L)
    }

    private fun scrollHToCenter(hs: android.widget.HorizontalScrollView, child: View) {
        val r = android.graphics.Rect()
        child.getDrawingRect(r)
        hs.offsetDescendantRectToMyCoords(child, r)
        val targetX = (r.centerX() - hs.width / 2).coerceAtLeast(0)
        hs.smoothScrollTo(targetX, 0)
    }

    private fun scrollVToCenter(ns: androidx.core.widget.NestedScrollView, child: View) {
        val r = android.graphics.Rect()
        child.getDrawingRect(r)
        ns.offsetDescendantRectToMyCoords(child, r)
        val targetY = (r.centerY() - ns.height / 2).coerceAtLeast(0)
        ns.smoothScrollTo(0, targetY)
    }


    private fun showWeeklyThenSplits(
        hScroll: android.widget.HorizontalScrollView?,
        vScroll: androidx.core.widget.NestedScrollView?,
        weeklyCard: View?,
        splitsChart: View?,
        onEnd: () -> Unit
    ) {
        if (weeklyCard != null && hScroll != null) {
            hScroll.post {
                scrollHToView(hScroll, weeklyCard, 12)
                weeklyCard.postDelayed({
                    TapTargetView.showFor(
                        this@DashboardActivity,
                        TapTarget.forView(
                            weeklyCard, "Averages and total workouts for the period.", ""
                        ).cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle(),
                        object : TapTargetView.Listener() {
                            override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                if (splitsChart != null && vScroll != null) {
                                    vScroll.post {
                                        scrollVToView(vScroll, splitsChart, 24)
                                        splitsChart.postDelayed({
                                            TapTargetView.showFor(
                                                this@DashboardActivity,
                                                TapTarget.forView(
                                                    splitsChart, "See Push / Pull / Legs / Upper counts.", ""
                                                ).cancelable(true).tintTarget(true).drawShadow(true).applyTourStyle(),
                                                object : TapTargetView.Listener() {
                                                    override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                                        onEnd()
                                                    }
                                                }
                                            )
                                        }, 350L)
                                    }
                                } else {
                                    onEnd()
                                }
                            }
                        }
                    )
                }, 320L)
            }
        } else if (splitsChart != null && vScroll != null) {
            vScroll.post {
                scrollVToView(vScroll, splitsChart, 24)
                splitsChart.postDelayed({
                    TapTargetView.showFor(
                        this@DashboardActivity,
                        TapTarget.forView(
                            splitsChart, "See Push / Pull / Legs / Upper counts.", ""
                        ).cancelable(true).tintTarget(true).drawShadow(true),
                        object : TapTargetView.Listener() {
                            override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) = onEnd()
                        }
                    )
                }, 350L)
            }
        } else {
            onEnd()
        }
    }


    private fun scrollHToView(hs: android.widget.HorizontalScrollView, child: View, leftPadDp: Int = 12) {
        val r = android.graphics.Rect()
        child.getDrawingRect(r)
        hs.offsetDescendantRectToMyCoords(child, r)   // <-- convert to HS coords
        hs.smoothScrollTo(max(0, r.left - leftPadDp.dp()), 0)
    }

    private fun scrollVToView(ns: androidx.core.widget.NestedScrollView, child: View, topPadDp: Int = 24) {
        val r = android.graphics.Rect()
        child.getDrawingRect(r)
        ns.offsetDescendantRectToMyCoords(child, r)   // <-- convert to NS coords
        val targetY = max(0, r.top - topPadDp.dp())
        ns.smoothScrollTo(0, targetY)
    }


    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun TapTarget.applyTourStyle(): TapTarget = apply {
        dimColor(R.color.tour_white_80)

        titleTextColor(R.color.tour_orange)
        descriptionTextColor(R.color.tour_orange)

        outerCircleColor(R.color.white)
        outerCircleAlpha(0.12f)
        targetCircleColor(R.color.white)

        tintTarget(true)
        transparentTarget(true)
        cancelable(true)
        drawShadow(false)
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
