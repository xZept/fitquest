package com.example.fitquest

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.fitquest.databinding.DialogLogFoodBinding
import com.example.fitquest.data.repository.FoodRepository
import com.example.fitquest.data.repository.MeasurementInput
import com.example.fitquest.database.MeasurementType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.R


private val MEALS = listOf("Breakfast", "Lunch", "Dinner", "Snack")

fun Fragment.showLogFoodDialog(
    repo: FoodRepository,
    userId: Int,
    fdcId: Long,
    defaultAmount: Double = 100.0,
    defaultUnit: MeasurementType = MeasurementType.GRAM,
    defaultMeal: String = "Lunch",
    onLogged: (logId: Long) -> Unit = {}
) {
    val binding = DialogLogFoodBinding.inflate(LayoutInflater.from(requireContext()))

    // dropdowns
    val units = MeasurementType.entries.map { it.name }
    binding.actvUnit.setAdapter(
        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, units)
    )
    binding.actvUnit.setText(defaultUnit.name, false)

    binding.actvMeal.setAdapter(
        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, MEALS)
    )
    binding.actvMeal.setText(defaultMeal, false)

    binding.etAmount.setText(
        if (defaultAmount == defaultAmount.toLong().toDouble())
            defaultAmount.toLong().toString() else defaultAmount.toString()
    )

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setView(binding.root)   // no positive/negative buttons
        .create()

    dialog.setOnShowListener {
        // Make wrapper transparent so your PNG shows
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        (binding.root.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

        // Optional ripple feedback
        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val ta = requireContext().obtainStyledAttributes(attrs)
        val ripple = ta.getDrawable(0); ta.recycle()
        binding.root.findViewById<ImageButton>(R.id.btn_cancel_img).foreground = ripple
        binding.root.findViewById<ImageButton>(R.id.btn_add_img).foreground = ripple

        // Cancel
        binding.root.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener {
            dialog.dismiss()
        }

        // Add (moved from default positive button)
        val addBtnImg = binding.root.findViewById<ImageButton>(R.id.btn_add_img)
        addBtnImg.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            val unitText   = binding.actvUnit.text?.toString()?.trim().orEmpty()
            val mealText   = binding.actvMeal.text?.toString()?.trim().orEmpty()

            val amount = amountText.toDoubleOrNull()
            val unit   = MeasurementType.tryParse(unitText)

            var ok = true
            if (amount == null || amount <= 0.0) { binding.tilAmount.error = "Enter a positive number"; ok = false } else binding.tilAmount.error = null
            if (unit == null)                    { binding.tilUnit.error   = "Choose a unit"; ok = false }         else binding.tilUnit.error   = null
            if (mealText.isBlank())              { binding.tilMeal.error   = "Choose a meal"; ok = false }         else binding.tilMeal.error   = null
            if (!ok) return@setOnClickListener

            addBtnImg.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val macros = repo.getMacrosForMeasurement(
                        fdcId = fdcId,
                        userInput = MeasurementInput(unit!!, amount!!)
                    )
                    val foodId = repo.ensureLocalIdForFdc(fdcId)
                    val logId = repo.logIntake(
                        userId = userId,
                        foodId = foodId,
                        grams = macros.resolvedGramWeight,
                        mealType = mealText.uppercase()
                    )
                    onLogged(logId)
                    dialog.dismiss()
                } catch (t: Throwable) {
                    addBtnImg.isEnabled = true
                    android.util.Log.e("LogFoodDialog", "Failed to log", t)
                }
            }
        }
    }

    dialog.show()
}


// Overload activity
fun FragmentActivity.showLogFoodDialog(
    repo: FoodRepository,
    userId: Int,
    fdcId: Long,
    defaultAmount: Double = 100.0,
    defaultUnit: MeasurementType = MeasurementType.GRAM,
    defaultMeal: String = "Lunch",
    onLogged: (logId: Long) -> Unit = {}
) {
    val binding = DialogLogFoodBinding.inflate(LayoutInflater.from(this))

    val units = MeasurementType.entries.map { it.name }
    binding.actvUnit.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, units))
    binding.actvUnit.setText(defaultUnit.name, false)

    binding.actvMeal.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, MEALS))
    binding.actvMeal.setText(defaultMeal, false)

    binding.etAmount.setText(
        if (defaultAmount == defaultAmount.toLong().toDouble())
            defaultAmount.toLong().toString() else defaultAmount.toString()
    )

    val dialog = MaterialAlertDialogBuilder(this)
        .setView(binding.root)   // no positive/negative buttons
        .create()

    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        (binding.root.parent as? ViewGroup)?.setPadding(0, 0, 0, 0)

        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val ta = obtainStyledAttributes(attrs)
        val ripple = ta.getDrawable(0); ta.recycle()
        binding.root.findViewById<ImageButton>(R.id.btn_cancel_img).foreground = ripple
        binding.root.findViewById<ImageButton>(R.id.btn_add_img).foreground = ripple

        binding.root.findViewById<ImageButton>(R.id.btn_cancel_img).setOnClickListener {
            dialog.dismiss()
        }
        val addBtnImg = binding.root.findViewById<ImageButton>(R.id.btn_add_img)
        addBtnImg.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            val unitText   = binding.actvUnit.text?.toString()?.trim().orEmpty()
            val mealText   = binding.actvMeal.text?.toString()?.trim().orEmpty()

            val amount = amountText.toDoubleOrNull()
            val unit   = MeasurementType.tryParse(unitText)

            var ok = true
            if (amount == null || amount <= 0.0) { binding.tilAmount.error = "Enter a positive number"; ok = false } else binding.tilAmount.error = null
            if (unit == null)                    { binding.tilUnit.error   = "Choose a unit"; ok = false }         else binding.tilUnit.error   = null
            if (mealText.isBlank())              { binding.tilMeal.error   = "Choose a meal"; ok = false }         else binding.tilMeal.error   = null
            if (!ok) return@setOnClickListener

            addBtnImg.isEnabled = false
            lifecycleScope.launch {
                try {
                    val macros = repo.getMacrosForMeasurement(
                        fdcId = fdcId,
                        userInput = MeasurementInput(unit!!, amount!!)
                    )
                    val foodId = repo.ensureLocalIdForFdc(fdcId)
                    val logId = repo.logIntake(
                        userId = userId,
                        foodId = foodId,
                        grams = macros.resolvedGramWeight,
                        mealType = mealText.uppercase()
                    )
                    onLogged(logId)
                    dialog.dismiss()
                } catch (t: Throwable) {
                    addBtnImg.isEnabled = true
                    android.util.Log.e("LogFoodDialog", "Failed to log", t)
                }
            }
        }
    }

    dialog.show()
}

