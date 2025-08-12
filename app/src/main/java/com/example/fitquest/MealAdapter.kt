package com.example.fitquest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MealAdapter(
    private val meals: List<MealSlot>
) : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

    inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mealBg: ImageView = itemView.findViewById(R.id.meal_background)
        val mealImage: ImageView = itemView.findViewById(R.id.meal_image)
        val mealLabel: TextView = itemView.findViewById(R.id.meal_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_slot, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        val mealSlot = meals[position]

        // Set label
        holder.mealLabel.text = when (mealSlot.type) {
            MealType.BREAKFAST -> "Breakfast"
            MealType.LUNCH -> "Lunch"
            MealType.DINNER -> "Dinner"
        }

        if (mealSlot.hasMeal) {
            holder.mealImage.visibility = View.VISIBLE
            holder.mealImage.setImageResource(
                when (mealSlot.type) {
                    MealType.BREAKFAST -> R.drawable.breakfast_container
                    MealType.LUNCH -> R.drawable.lunch_container
                    MealType.DINNER -> R.drawable.dinner_container
                }
            )
        } else {
            holder.mealImage.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = meals.size
}
