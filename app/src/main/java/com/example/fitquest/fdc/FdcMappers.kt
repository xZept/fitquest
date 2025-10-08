    package com.example.fitquest.fdc

    import android.util.Log
    import com.example.fitquest.database.Food
    import com.example.fitquest.database.Portion
    import com.example.fitquest.database.MeasurementType
    import com.example.fitquest.fdc.FdcModels
    import kotlin.math.max

    private fun String.normalize() =
        lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun FdcModels.FdcFoodDetail.n(vararg numbers: String): Double {
        // 1) flat
        foodNutrients.firstOrNull { it.number != null && it.number in numbers }
            ?.amount
            ?.let { return it ?: 0.0 }

        // 2) nested
        foodNutrients.firstOrNull { it.nutrient?.number != null && it.nutrient.number in numbers }
            ?.amount
            ?.let { return it ?: 0.0 }

        // 3) branded label nutrients → normalize to per-100 g/ml if unit permits
        val perServing = when {
            "1008" in numbers || "208" in numbers -> labelNutrients?.calories?.value
            "1003" in numbers || "203" in numbers -> labelNutrients?.protein?.value
            "1004" in numbers || "204" in numbers -> labelNutrients?.fat?.value
            "1005" in numbers || "205" in numbers -> labelNutrients?.carbohydrates?.value
            else -> null
        }
        if (perServing != null) {
            val sz = servingSize
            val unit = servingSizeUnit?.lowercase()
            return if (sz != null && sz > 0 && (unit == "g" || unit == "ml")) (perServing / sz) * 100.0 else perServing
        }

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

                // Countable items (bananas, buns, eggs) often come as "1 small/medium/large", "1 whole", or "1 each"
                "piece" in mod || "slice" in mod
                        || "each" in mod
                        || "whole" in mod
                        || " small" in mod || " medium" in mod || " large" in mod
                    -> PortionSpec(MeasurementType.PIECE, 1.0, g, true)

                mod == "ml" || "ml" in mod         -> PortionSpec(MeasurementType.MILLILITER, 1.0, g, false)

                // Keep a safe fallback to GRAM so ambiguous things like "serving" DO NOT become PIECE
                else                               -> PortionSpec(MeasurementType.GRAM, 100.0, 100.0, false)
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

        return portions.distinctBy { Triple(it.measurementType, it.quantity, it.gramWeight) }
    }

    fun FdcModels.FdcFoodDetail.toFoodEntity(): Food {
        // Try *both* the new (100x) and old (20x) nutrient numbers
        val kcal100    = n("1008", "208")
        val protein100 = n("1003", "203")
        val fat100     = n("1004", "204")
        val carb100    = n("1005", "205")

        android.util.Log.d(
            "FDC.map",
            "toFoodEntity fdcId=$fdcId kcal=$kcal100 P=$protein100 F=$fat100 C=$carb100 dataType=${dataType ?: "?"}"
        )

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


