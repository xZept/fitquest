package com.example.fitquest

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fitquest.workout.WorkoutEngine
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.BitmapFactory
import com.example.fitquest.ui.widgets.SpriteSheetDrawable

class QuestPreviewActivity : AppCompatActivity() {

    private lateinit var listView: ListView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_preview)
        hideNavBar()

        initAnimatedBg(
            rows = 1,           // adjust if your sheet differs
            cols = 12,          // your earlier note said 39 frames in a row
            fps = 12            // tweak to taste
        )


        listView = findViewById(R.id.lv_exercises)
        btnAdd   = findViewById(R.id.btn_add)
        btnBack  = findViewById(R.id.btn_cancel) // treat as Back
        btnSave  = findViewById(R.id.btn_save)

        mode  = intent.getStringExtra("MODE") ?: "basic"
        split = intent.getStringExtra("SPLIT") ?: "Push"
        focus = intent.getStringExtra("FOCUS") ?: "General"

        WorkoutEngine.defaultScheme(focus).let { (mn, mx, st) ->
            schemeMin = mn; schemeMax = mx; schemeSets = st
        }

        items.clear()
        items.addAll(intent.getStringArrayListExtra("START_NAMES")?.toList() ?: emptyList())

        addable.clear()
        addable.addAll(intent.getStringArrayListExtra("ADDABLE_NAMES")?.toList() ?: emptyList())

        val adapter = EditAdapter(
            allowDelete = (mode == "advanced"),
            data = items
        )
        listView.adapter = adapter



        // Add button only for advanced
        if (mode == "advanced") {
            btnAdd.visibility = View.VISIBLE
            btnAdd.setOnClickListener {
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
            setResult(RESULT_CANCELED, Intent().putExtra("BACK_TO_CHOICE", true))
            finish()
        }

        // Save → return final ordered names
        btnSave.setOnClickListener {
            val out = items.distinctBy { it.trim().lowercase() }
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
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.bg_dashboard_spritesheet)
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



    private fun hideNavBar() {
        // Let your content draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide only the navigation bar. (Use Type.systemBars() if you also want to hide the status bar.)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }


    private inner class EditAdapter(
        private val allowDelete: Boolean,
        private val data: MutableList<String>
    ) : BaseAdapter() {

        override fun getCount(): Int = data.size
        override fun getItem(position: Int): String = data[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@QuestPreviewActivity)
                .inflate(R.layout.item_editable_exercise, parent, false)

            val tvName   = v.findViewById<TextView>(R.id.tv_name)
            val tvDetail = v.findViewById<TextView>(R.id.tv_detail)
            val btnUp    = v.findViewById<ImageButton>(R.id.btn_up)
            val btnDown  = v.findViewById<ImageButton>(R.id.btn_down)
            val btnDel   = v.findViewById<ImageButton>(R.id.btn_delete)

            tvName.text = data[position]
            tvDetail.text = "$schemeSets sets • $schemeMin–$schemeMax reps"

            btnUp.setOnClickListener {
                if (position > 0) {
                    data.add(position - 1, data.removeAt(position))
                    notifyDataSetChanged()
                }
            }
            btnDown.setOnClickListener {
                if (position < data.lastIndex) {
                    data.add(position + 1, data.removeAt(position))
                    notifyDataSetChanged()
                }
            }

            if (allowDelete) {
                btnDel.visibility = View.VISIBLE
                btnDel.setOnClickListener {
                    data.removeAt(position)
                    notifyDataSetChanged()
                }
            } else {
                btnDel.visibility = View.GONE
                btnDel.setOnClickListener(null)
            }

            return v
        }
    }
}
