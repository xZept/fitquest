package com.example.fitquest

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fitquest.workout.gitWorkoutEngine
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.BitmapFactory
import android.view.animation.AnimationUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import java.util.Collections
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestPreviewActivity : AppCompatActivity(), StartDragListener {

    private lateinit var rv: RecyclerView
    private lateinit var btnAdd: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSave: ImageButton

    private lateinit var mode: String
    private lateinit var split: String
    private lateinit var focus: String

    private var schemeMin = 8
    private var schemeMax = 12
    private var schemeSets = 3

    private val items = mutableListOf<String>()

    // Addable lists
    private val addableSuggestions = mutableListOf<String>() // dynamic (after deletions)
    private val addableBaseline = mutableListOf<String>()    // from intent

    // Track recent deletions (MRU, most recent first)
    private val recentlyRemoved = ArrayDeque<String>() // keep small

    private var bgDrawable: SpriteSheetDrawable? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var pressAnim: android.view.animation.Animation

    private lateinit var tvBanner: TextView
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_preview)
        hideNavBar()

        tvBanner = findViewById(R.id.tv_banner)
        showBanner("Here is your quest, adventurer! You can reorder exercises; switch to Advanced to add or delete.", 8_000L)
        tvBanner.setOnClickListener { hideBannerRunnable.run() }

        db = AppDatabase.getInstance(applicationContext)
        initAnimatedBg(rows = 1, cols = 12, fps = 12)

        rv = findViewById(R.id.rv_exercises)
        btnAdd   = findViewById(R.id.btn_add)
        btnBack  = findViewById(R.id.btn_cancel)
        btnSave  = findViewById(R.id.btn_save)
        rv.layoutManager = LinearLayoutManager(this)

        mode  = intent.getStringExtra("MODE") ?: "basic"
        split = intent.getStringExtra("SPLIT") ?: "Push"
        focus = intent.getStringExtra("FOCUS") ?: "General"

        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestPreviewActivity).first()
            val latestCode = withContext(Dispatchers.IO) {
                db.monsterDao().getLatestOwnedForUser(uid)?.code ?: "slime"
            }
            setRvContainerBackgroundForCode(latestCode)
        }

        gitWorkoutEngine.defaultScheme(focus).let { (mn, mx, st) ->
            schemeMin = mn; schemeMax = mx; schemeSets = st
        }

        items.clear()
        items.addAll(intent.getStringArrayListExtra("START_NAMES")?.toList() ?: emptyList())

        addableBaseline.clear()
        addableBaseline.addAll(intent.getStringArrayListExtra("ADDABLE_NAMES")?.toList() ?: emptyList())

        addableSuggestions.clear()
        addableSuggestions.addAll(addableBaseline)

        val adapter = ExerciseAdapter(
            allowDelete = (mode == "advanced"),
            data = items,
            dragStarter = this,
            sets = schemeSets,
            minReps = schemeMin,
            maxReps = schemeMax
        ) { /* onDataChanged */ }
        rv.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rv)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        if (mode == "advanced") {
            btnAdd.visibility = View.VISIBLE
            btnAdd.setOnClickListener {
                it.startAnimation(pressAnim)
                showAddDialog(useSuggestions = recentlyRemoved.isNotEmpty(), adapter = adapter)
            }
        } else btnAdd.visibility = View.GONE

        btnBack.setOnClickListener {
            it.startAnimation(pressAnim)
            setResult(RESULT_CANCELED, Intent().putExtra("BACK_TO_CHOICE", true))
            finish()
        }

        btnSave.setOnClickListener {
            it.startAnimation(pressAnim)
            val out = adapter.currentItems().distinctBy { it.trim().lowercase() }
            val data = Intent().apply {
                putStringArrayListExtra("RESULT_NAMES", ArrayList(out))
                putExtra("SPLIT", split)
                putExtra("FOCUS", focus)
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        bgDrawable?.start()
        lifecycleScope.launch {
            val uid = DataStoreManager.getUserId(this@QuestPreviewActivity).first()
            val latestCode = withContext(Dispatchers.IO) {
                db.monsterDao().getLatestOwnedForUser(uid)?.code ?: "slime"
            }
            setRvContainerBackgroundForCode(latestCode)
        }
    }

    override fun onPause() {
        bgDrawable?.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavBar()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun showBanner(message: String, durationMs: Long) {
        tvBanner.text = message
        tvBanner.alpha = 1f
        tvBanner.visibility = View.VISIBLE
        tvBanner.animate().cancel()
        tvBanner.removeCallbacks(hideBannerRunnable)
        tvBanner.postDelayed(hideBannerRunnable, durationMs)
    }

    private val hideBannerRunnable = Runnable {
        tvBanner.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                tvBanner.visibility = View.INVISIBLE
                tvBanner.alpha = 1f
            }
            .start()
    }

    private fun initAnimatedBg(rows: Int, cols: Int, fps: Int) {
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.bg_page_dashboard_spritesheet0)
        bgDrawable = SpriteSheetDrawable(
            sheet = bmp, rows = rows, cols = cols, fps = fps, loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )
        findViewById<View>(R.id.quest_root).background = bgDrawable
        bgDrawable?.start()
    }

    private fun resolveFirstDrawable(vararg names: String): Int {
        for (n in names) {
            val id = resources.getIdentifier(n, "drawable", packageName)
            if (id != 0) return id
        }
        return 0
    }

    private fun setRvContainerBackgroundForCode(codeRaw: String?) {
        val code = (codeRaw ?: "slime").lowercase()
        val bgId = resolveFirstDrawable(
            "container_split_plan_${code}",
            "container_split_plan",
            "container_split_plan_slime"
        )
        rv.setBackgroundResource(if (bgId != 0) bgId else R.drawable.container_split_plan_slime)
    }

    private fun hideNavBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.DRAG_START)
    }

    // --- Add dialog helpers ---

    private fun showAddDialog(useSuggestions: Boolean, adapter: ExerciseAdapter) {
        val currentPool = if (useSuggestions) addableSuggestions else addableBaseline

        val choices = currentPool
            .filter { candidate -> items.none { it.equals(candidate, ignoreCase = true) } }
            .distinctBy { it.trim().lowercase() }
            .sorted()
            .toTypedArray()

        if (choices.isEmpty()) {
            Toast.makeText(this, "No more exercises to add.", Toast.LENGTH_SHORT).show()
            return
        }

        val title = when {
            useSuggestions && recentlyRemoved.size > 1 ->
                "Add Exercise — alternatives for ${recentlyRemoved.size} removed items"
            useSuggestions && recentlyRemoved.isNotEmpty() ->
                "Add Exercise — alternatives for: ${recentlyRemoved.first()}"
            else -> "Add Exercise"
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(choices) { _, which ->
                items.add(choices[which])
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)

        if (useSuggestions) {
            builder.setNeutralButton("Show all that fit my filter") { dlg, _ ->
                dlg.dismiss()
                showAddDialog(useSuggestions = false, adapter = adapter)
            }
        }

        builder.show()
    }

    // --- utils ---

    private suspend fun userOwnedEquip(): Set<String> {
        val uid = DataStoreManager.getUserId(this@QuestPreviewActivity).first()
        val settings = db.userSettingsDao().getByUserId(uid)
        return settings?.equipmentCsv
            ?.split('|')
            ?.mapNotNull { it.trim().lowercase().ifEmpty { null } }
            ?.toSet() ?: emptySet()
    }

    private inner class ExerciseAdapter(
        private val allowDelete: Boolean,
        private val data: MutableList<String>,
        private val dragStarter: StartDragListener,
        private val sets: Int,
        private val minReps: Int,
        private val maxReps: Int,
        private val onDataChanged: () -> Unit
    ) : RecyclerView.Adapter<ExerciseAdapter.VH>(), ItemTouchHelperAdapter {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvDetail: TextView = v.findViewById(R.id.tv_detail)
            val btnDel: ImageButton = v.findViewById(R.id.btn_delete)
            val dragHandle: ImageView = v.findViewById(R.id.drag_handle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_editable_exercise, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tvName.text = data[position]
            holder.tvDetail.text = "$sets sets • $minReps–$maxReps reps"

            if (allowDelete) {
                holder.btnDel.visibility = View.VISIBLE
                holder.btnDel.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                    val removedName = data[pos]
                    data.removeAt(pos)
                    notifyItemRemoved(pos)
                    onDataChanged()

                    lifecycleScope.launch {
                        // MRU list of deletions
                        recentlyRemoved.remove(removedName)
                        recentlyRemoved.addFirst(removedName)
                        while (recentlyRemoved.size > 5) recentlyRemoved.removeLast()

                        val owned = userOwnedEquip()
                        val suggestions = gitWorkoutEngine.buildAddablePoolForReplacementsBatch(
                            context = this@QuestPreviewActivity,
                            removedExerciseNames = recentlyRemoved.toList(), // include ALL removed, most-recent first
                            split = split,
                            focus = focus,
                            ownedEquipCanonical = owned,
                            currentPlanNames = data, // exclude what remains
                            limit = 60
                        )

                        addableSuggestions.clear()
                        addableSuggestions.addAll(
                            suggestions.filter { s -> data.none { it.equals(s, ignoreCase = true) } }
                        )

                        Toast.makeText(
                            this@QuestPreviewActivity,
                            if (recentlyRemoved.size > 1)
                                "Showing alternatives for ${recentlyRemoved.size} removed items"
                            else
                                "Showing alternatives for: $removedName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                holder.btnDel.visibility = View.GONE
                holder.btnDel.setOnClickListener(null)
            }

            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    dragStarter.onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount(): Int = data.size

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            if (fromPosition == toPosition) return false
            Collections.swap(data, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
            onDataChanged()
            return true
        }

        fun currentItems(): List<String> = data
    }
}

// ItemTouchHelper glue (top-level)
interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onItemDismiss(position: Int) {} // not used
}

class SimpleItemTouchHelperCallback(
    private val adapter: ItemTouchHelperAdapter
) : androidx.recyclerview.widget.ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
    ): Int {
        val dragFlags = androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
        target: androidx.recyclerview.widget.RecyclerView.ViewHolder
    ): Boolean = adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) { /* no-op */ }

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun onSelectedChanged(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.85f
        }
    }
    override fun clearView(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1f
    }
}

interface StartDragListener { fun onStartDrag(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) }
