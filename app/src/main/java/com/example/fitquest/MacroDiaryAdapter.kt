package com.example.fitquest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.database.MacroDiary
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


private val INPUT_YMD = DateTimeFormatter.BASIC_ISO_DATE           // yyyyMMdd
private val DISPLAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

class MacroDiaryAdapter(
    private val onClick: (MacroDiary) -> Unit = {}
) : ListAdapter<MacroDiary, MacroDiaryAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<MacroDiary>() {
        override fun areItemsTheSame(a: MacroDiary, b: MacroDiary) = a.id == b.id
        override fun areContentsTheSame(a: MacroDiary, b: MacroDiary) = a == b
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate = view.findViewById<TextView>(R.id.tvDate)
        private val tvCalories = view.findViewById<TextView>(R.id.tvCalories)
        private val tvMacros = view.findViewById<TextView>(R.id.tvMacros)
        private val tvGoalCalories = view.findViewById<TextView>(R.id.tvGoalCalories)
        private val tvGoalMacros= view.findViewById<TextView>(R.id.tvGoalMacros)

        fun bind(item: MacroDiary) {
            tvDate.text = formatDayKey(item.dayKey).toString()
            tvCalories.text = "${item.calories} kcal"
            tvMacros.text = "P ${item.protein}g • C ${item.carbs}g • F ${item.fat}g"
            tvGoalCalories.text = "${item.planCalories} kcal"
            tvGoalMacros.text = "P ${item.planProtein}g • C ${item.planCarbs}g • F ${item.planFat}g"

            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_macro_diary, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}

private fun formatDayKey(dayKey: Int): String {
    val date = LocalDate.parse(dayKey.toString(), INPUT_YMD)
    return date.format(DISPLAY)
}

