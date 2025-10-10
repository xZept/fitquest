// app/src/main/java/com/example/fitquest/DashboardActivity.kt
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

class DashboardActivity : AppCompatActivity() {

    private lateinit var dashboardTip: TextView
    private lateinit var pressAnim: android.view.animation.Animation
    private var bgAnim: SpriteSheetDrawable? = null
    private lateinit var db: AppDatabase
    private lateinit var chart: BarChart

    private val splits = listOf("Push", "Pull", "Legs", "Upper")
    private lateinit var quickAction: ImageButton
    private var hasActiveQuest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = AppDatabase.getInstance(applicationContext)
        chart = findViewById(R.id.chart_splits)

        setupChartAppearance()
        loadDataAndRender()

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

        // If you keep a legend:
//        chart.legend.apply {
//            isEnabled = true
//            textColor = Color.WHITE     // 👈 legend color
//            textSize  = 12f             // 👈 legend size
//        }

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
