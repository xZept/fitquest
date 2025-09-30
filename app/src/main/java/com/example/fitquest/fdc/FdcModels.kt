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
        val number: String?,    // e.g., "1008"
        val name: String?,
        val amount: Double?,    // usually per 100 g for SR Legacy/Foundation
        val unitName: String?
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