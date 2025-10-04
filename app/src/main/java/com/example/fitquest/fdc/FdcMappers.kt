package com.example.fitquest.fdc

import android.util.Log
import com.example.fitquest.database.Food
import com.example.fitquest.database.Portion
import com.example.fitquest.database.MeasurementType
import com.example.fitquest.fdc.FdcModels
import kotlin.math.max

private fun String.normalize() =
    lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

private fun FdcModels.FdcFoodDetail.n(no: String): Double {
    Log.d("FDC.n", "fdcId=$fdcId calc number=$no; nutrients=${foodNutrients.size}; " +
            "label=${labelNutrients != null}; serving=$servingSize $servingSizeUnit")

    // 1) Try foodNutrients (flat OR nested)
    foodNutrients.firstOrNull { it.number == no || it.nutrient?.number == no }?.let { fn ->
        val amt  = fn.amount
        val unit = fn.unitName ?: fn.nutrient?.unitName
        val num  = fn.number ?: fn.nutrient?.number
        Log.d("FDC.n", "match in foodNutrients: number=$num amount=$amt unit=$unit")
        if (amt != null) {
            val out = if (unit.equals("kJ", true)) amt * 0.239006 else amt
            Log.d("FDC.n", "return from foodNutrients: $out (kJ→kcal=${unit.equals("kJ", true)})")
            return out
        }
    }

    // 1b) Legacy energy code 208 → treat as 1008 if present
    if (no == "1008") {
        foodNutrients.firstOrNull { it.number == "208" || it.nutrient?.number == "208" }?.let { fn ->
            val amt  = fn.amount
            val unit = fn.unitName ?: fn.nutrient?.unitName
            Log.d("FDC.n", "fallback number=208 amount=$amt unit=$unit")
            if (amt != null) {
                val out = if (unit.equals("kJ", true)) amt * 0.239006 else amt
                Log.d("FDC.n", "return from 208 fallback: $out")
                return out
            }
        }
    }

    // 2) Try labelNutrients (Branded; per-serving → per-100 g/ml)
    val perServing: Double? = when (no) {
        "1008" -> labelNutrients?.calories?.value
        "1003" -> labelNutrients?.protein?.value
        "1004" -> labelNutrients?.fat?.value
        "1005" -> labelNutrients?.carbohydrates?.value
        else   -> null
    }
    if (perServing != null) {
        val size = servingSize
        val unit = servingSizeUnit?.lowercase()
        val out = if (size != null && size > 0 && (unit == "g" || unit == "ml")) {
            (perServing / size) * 100.0
        } else {
            perServing
        }
        Log.d("FDC.n", "from labelNutrients: perServing=$perServing size=$size unit=$unit → per100=$out")
        return out
    }

    Log.w("FDC.n", "no match for number=$no on fdcId=$fdcId → 0.0")
    return 0.0
}


private data class PortionSpec(
    val type: MeasurementType,
    val qty: Double,
    val grams: Double,
    val approx: Boolean
)

// ---------- PORTIONS (edible grams per unit) ----------
fun FdcModels.FdcFoodDetail.toPortions(foodId: Long): List<Portion> {
    val portions = mutableListOf<Portion>()

    // Always include GRAM(100)
    portions += Portion(
        portionId = 0L,
        foodId = foodId,
        measurementType = MeasurementType.GRAM,
        quantity = 100.0,
        gramWeight = 100.0,
        isApproximate = false
    )

    // Your DTO doesn't have measureUnit -> use modifier/description only
    foodPortions.forEach { p ->
        val g = p.gramWeight ?: return@forEach
        val mod = (p.modifier?.lowercase() ?: p.portionDescription?.lowercase() ?: "")

        val spec = when {
            "cup" in mod                       -> PortionSpec(MeasurementType.CUP,        1.0, g, false)
            "tbsp" in mod || "tablespoon" in mod
                -> PortionSpec(MeasurementType.TABLESPOON, 1.0, g, false)
            "tsp" in mod  || "teaspoon" in mod -> PortionSpec(MeasurementType.TEASPOON,   1.0, g, false)
            "fl oz" in mod                     -> PortionSpec(MeasurementType.FL_OUNCE,   1.0, g, false)
            mod == "oz" || "ounce" in mod      -> PortionSpec(MeasurementType.OUNCE,      1.0, g, false)
            "piece" in mod || "slice" in mod   -> PortionSpec(MeasurementType.PIECE,      1.0, g, true)
            mod == "ml" || "ml" in mod         -> PortionSpec(MeasurementType.MILLILITER, 1.0, g, false)
            else                                -> PortionSpec(MeasurementType.GRAM,     100.0, 100.0, false)
        }

        portions += Portion(
            portionId = 0L,
            foodId = foodId,
            measurementType = spec.type,
            quantity = spec.qty,
            gramWeight = spec.grams,
            isApproximate = spec.approx
        )
    }

    // Optional: default SANDOK for cooked rice
    if (description.contains("rice", true) && description.contains("cooked", true)) {
        portions += Portion(0L, foodId, MeasurementType.SANDOK, 1.0, 150.0, true)
    }

    return portions.distinctBy { Triple(it.measurementType, it.quantity, it.gramWeight) }
}

fun FdcModels.FdcFoodDetail.toFoodEntity(): Food {
    val kcal100    = n("1008")
    val protein100 = n("1003")
    val fat100     = n("1004")
    val carb100    = n("1005")
    Log.d("FDC.map", "toFoodEntity fdcId=$fdcId kcal=$kcal100 P=$protein100 F=$fat100 C=$carb100")
    return Food(
        foodId = 0L,
        foodName = description.trim(),
        normalizedName = description.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim(),
        category = null,
        locale = "US",
        source = "USDA FDC",
        sourceRef = fdcId.toString(),
        kcalPer100g = kotlin.math.max(0.0, kcal100),
        carbPer100g = kotlin.math.max(0.0, carb100),
        proteinPer100g = kotlin.math.max(0.0, protein100),
        fatPer100g = kotlin.math.max(0.0, fat100),
        ediblePortionPercent = 100.0
    )
}


