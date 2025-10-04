package com.example.fitquest.fdc

class FdcModels {
    // FdcModels.kt (trimmed)
    data class FdcSearchResponse(
        val totalHits: Int,
        val foods: List<FdcSearchFood>
    )

    data class FdcSearchFood(
        val fdcId: Long,
        val description: String,
        val dataType: String?,
        val brandOwner: String? = null
    )

    data class FdcFoodDetail(
        val fdcId: Long,
        val description: String,
        val dataType: String?,
        val foodNutrients: List<FdcNutrient> = emptyList(),
        val foodPortions: List<FdcPortion> = emptyList(),
        // branded foods:
        val servingSize: Double? = null,
        val servingSizeUnit: String? = null,
        val labelNutrients: LabelNutrients? = null
    )

    data class FdcNutrient(
        // flat (rare in detail, common in search)
        val number: String? = null,
        val name: String? = null,
        val unitName: String? = null,
        val amount: Double? = null,

        // nested (common in /food/{id})
        val nutrient: NutrientRef? = null
    )

    data class NutrientRef(
        val id: Long? = null,
        val number: String? = null,
        val name: String? = null,
        val unitName: String? = null
    )

    data class FdcPortion(
        val gramWeight: Double?,              // grams per household portion
        val modifier: String? = null,         // e.g., "cup", "tbsp"
        val portionDescription: String? = null
    )

    data class LabelNutrients(
        val calories: LnVal? = null,
        val protein: LnVal? = null,
        val fat: LnVal? = null,
        val carbohydrates: LnVal? = null
    )
    data class LnVal(val value: Double?)

}