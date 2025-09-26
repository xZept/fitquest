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

class QuestPreviewActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnAdd: Button
    private lateinit var btnBack: Button    // uses R.id.btn_cancel in the XML
    private lateinit var btnSave: Button

    private lateinit var mode: String       // "basic" or "advanced"
    private lateinit var split: String
    private lateinit var focus: String

    private var schemeMin = 8
    private var schemeMax = 12
    private var schemeSets = 3

    private val items = mutableListOf<String>()
    private val addable = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_preview)

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
