package com.example.fitquest.fdc

import com.example.fitquest.database.Food
import com.example.fitquest.database.Portion
import com.example.fitquest.database.MeasurementType
import com.example.fitquest.fdc.FdcModels
import kotlin.math.max

private fun String.normalize() =
    lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

// Your DTOs are flat: foodNutrients[].number + amount
// IDs: 1008 kcal, 1003 protein, 1004 fat, 1005 carbs
private fun FdcModels.FdcFoodDetail.n(no: String): Double =
    foodNutrients.firstOrNull { it.number == no }?.amount ?: 0.0

// ---------- FOOD (per-100 g) ----------
fun FdcModels.FdcFoodDetail.toFoodEntity(): Food {
    val kcal100    = n("1008")
    val protein100 = n("1003")
    val fat100     = n("1004")
    val carb100    = n("1005")

    return Food(
        foodId = 0L,
        foodName = description.trim(),
        normalizedName = description.normalize(),
        category = null,
        locale = "US",
        source = "USDA FDC",
        sourceRef = fdcId.toString(),
        kcalPer100g = max(0.0, kcal100),
        carbPer100g = max(0.0, carb100),
        proteinPer100g = max(0.0, protein100),
        fatPer100g = max(0.0, fat100),
        ediblePortionPercent = 100.0
    )
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
