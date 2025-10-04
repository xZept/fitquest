package com.example.fitquest

import android.R
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
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

    // set up dropdowns
    val units = MeasurementType.entries.map { it.name }
    binding.actvUnit.setAdapter(
        ArrayAdapter(requireContext(), R.layout.simple_list_item_1, units)
    )
    binding.actvUnit.setText(defaultUnit.name, false)

    binding.actvMeal.setAdapter(
        ArrayAdapter(requireContext(), R.layout.simple_list_item_1, MEALS)
    )
    binding.actvMeal.setText(defaultMeal, false)

    binding.etAmount.setText(
        if (defaultAmount == defaultAmount.toLong().toDouble())
            defaultAmount.toLong().toString() else defaultAmount.toString()
    )

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle("Add to log")
        .setView(binding.root)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Add", null)
        .create()

    dialog.setOnShowListener {
        val addBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        addBtn.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            val unitText = binding.actvUnit.text?.toString()?.trim().orEmpty()
            val mealText = binding.actvMeal.text?.toString()?.trim().orEmpty()

            val amount = amountText.toDoubleOrNull()
            val unit: MeasurementType? =
                MeasurementType.tryParse(unitText)

            var ok = true
            if (amount == null || amount <= 0.0) {
                binding.tilAmount.error = "Enter a positive number"; ok = false
            } else binding.tilAmount.error = null
            if (unit == null) {
                binding.tilUnit.error = "Choose a unit"; ok = false
            } else binding.tilUnit.error = null
            if (mealText.isBlank()) {
                binding.tilMeal.error = "Choose a meal"; ok = false
            } else binding.tilMeal.error = null
            if (!ok) return@setOnClickListener

            viewLifecycleOwner.lifecycleScope.launch {
                // 1) compute macros (this also ensures the food exists locally + portions)
                val macros = repo.getMacrosForMeasurement(
                    fdcId = fdcId,
                    userInput = MeasurementInput(unit!!, amount!!)
                )

                // 2) get local foodId (helper you added earlier)
                val foodId = repo.ensureLocalIdForFdc(fdcId)

                // 3) log intake (your repo expects mealType as String)
                val logId = repo.logIntake(
                    userId = userId,
                    foodId = foodId,
                    grams = macros.resolvedGramWeight,
                    mealType = mealText.uppercase() // e.g., "LUNCH"
                )
                onLogged(logId)
                dialog.dismiss()
            }

        }
        dialog.show()
    }
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

    // dropdowns
    val units = MeasurementType.entries.map { it.name }
    binding.actvUnit.setAdapter(
        ArrayAdapter(this, android.R.layout.simple_list_item_1, units)
    )
    binding.actvUnit.setText(defaultUnit.name, false)

    binding.actvMeal.setAdapter(
        ArrayAdapter(this, android.R.layout.simple_list_item_1, MEALS)
    )
    binding.actvMeal.setText(defaultMeal, false)

    binding.etAmount.setText(
        if (defaultAmount == defaultAmount.toLong().toDouble())
            defaultAmount.toLong().toString() else defaultAmount.toString()
    )

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Add to log")
        .setView(binding.root)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Add", null)
        .create()

    dialog.setOnShowListener {
        val addBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        addBtn.setOnClickListener {
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

            addBtn.isEnabled = false
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
                    addBtn.isEnabled = true
                    android.util.Log.e("LogFoodDialog", "Failed to log", t)
                }
            }
        }
    }

    dialog.show()
}
