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


class QuestPreviewActivity : AppCompatActivity(),  StartDragListener{

    private lateinit var rv: RecyclerView
    private lateinit var btnAdd: ImageButton
    private lateinit var btnBack: ImageButton    // uses R.id.btn_cancel in the XML
    private lateinit var btnSave: ImageButton

    private lateinit var mode: String       // "basic" or "advanced"
    private lateinit var split: String
    private lateinit var focus: String

    private var schemeMin = 8
    private var schemeMax = 12
    private var schemeSets = 3

    private val items = mutableListOf<String>()
    private val addable = mutableListOf<String>()

    private var bgDrawable: SpriteSheetDrawable? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var pressAnim: android.view.animation.Animation

    private lateinit var db: AppDatabase  // ← declare only

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_preview)
        hideNavBar()

        db = AppDatabase.getInstance(applicationContext)

        initAnimatedBg(
            rows = 1,           // adjust if your sheet differs
            cols = 12,          // your earlier note said 39 frames in a row
            fps = 12            // tweak to taste
        )


        rv = findViewById(R.id.rv_exercises)
        btnAdd   = findViewById(R.id.btn_add)
        btnBack  = findViewById(R.id.btn_cancel) // treat as Back
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

        addable.clear()
        addable.addAll(intent.getStringArrayListExtra("ADDABLE_NAMES")?.toList() ?: emptyList())

        val adapter = ExerciseAdapter(
            allowDelete = (mode == "advanced"),
            data = items,
            dragStarter = this,
            sets = schemeSets,
            minReps = schemeMin,
            maxReps = schemeMax
        ) {
            // onDataChanged: optional place to persist temp order
        }
        rv.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rv)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        // Add button only for advanced
        if (mode == "advanced") {
            btnAdd.visibility = View.VISIBLE
            btnAdd.setOnClickListener {
                it.startAnimation(pressAnim)
                val choices = addable
                    .filter { candidate -> items.none { it.equals(candidate, ignoreCase = true) } }
                    .sorted()
                    .toTypedArray()

                if (choices.isEmpty()) {
                    Toast.makeText(this, "No more exercises to add.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                AlertDialog.Builder(this)
                    .setTitle("Add Exercise")
                    .setItems(choices) { _, which ->
                        items.add(choices[which])
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            btnAdd.visibility = View.GONE
        }

        // Back → tell generator to reopen the chooser
        btnBack.setOnClickListener {
            it.startAnimation(pressAnim)
            setResult(RESULT_CANCELED, Intent().putExtra("BACK_TO_CHOICE", true))
            finish()
        }

        // Save → return final ordered names
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


        // Re-check latest monster and update the RV container
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

    private fun initAnimatedBg(rows: Int, cols: Int, fps: Int) {
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.bg_page_dashboard_spritesheet0)
        bgDrawable = SpriteSheetDrawable(
            sheet = bmp,
            rows = rows,
            cols = cols,
            fps = fps,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )

        val root = findViewById<View>(R.id.quest_root)
        root.background = bgDrawable
        // start happens in onResume, but we can kick it once here too
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
            "container_split_plan_${code}",    // e.g., container_split_plan_mushroom
            "container_split_plan",            // generic (if present)
            "container_split_plan_slime"       // safe fallback
        )
        rv.setBackgroundResource(if (bgId != 0) bgId else R.drawable.container_split_plan_slime)
    }




    private fun hideNavBar() {
        // Let your content draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide only the navigation bar. (Use Type.systemBars() if you also want to hide the status bar.)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.DRAG_START)
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
                    data.removeAt(pos)
                    notifyItemRemoved(pos)
                    onDataChanged()
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

// ItemTouchHelper glue
interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onItemDismiss(position: Int) {} // not used, but handy if you add swipe later
}

class SimpleItemTouchHelperCallback(
    private val adapter: ItemTouchHelperAdapter
) : androidx.recyclerview.widget.ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
    ): Int {
        val dragFlags = androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN
        val swipeFlags = 0 // disable swipe
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
        target: androidx.recyclerview.widget.RecyclerView.ViewHolder
    ): Boolean = adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) { /* no-op */ }

    override fun isLongPressDragEnabled(): Boolean = false // we'll start drag via handle
    override fun isItemViewSwipeEnabled(): Boolean = false

    // Optional: little visual feedback while dragging
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
