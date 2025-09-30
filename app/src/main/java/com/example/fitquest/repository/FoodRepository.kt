package com.example.fitquest.data.repository

import com.example.fitquest.fdc.FdcModels
import com.example.fitquest.fdc.FdcService
import com.example.fitquest.fdc.toFoodEntity
import com.example.fitquest.fdc.toPortions
import com.example.fitquest.database.*
import com.example.fitquest.database.MeasurementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FoodRepository(
    private val api: FdcService,
    private val db: AppDatabase
) {
    // 1) SEARCH & SHOW MATCHES
    suspend fun search(term: String, page: Int = 1, pageSize: Int = 50) =
        api.searchFoods(query = term, pageNumber = page, pageSize = pageSize).foods

    // 2) GET MACROS FOR A GIVEN MEASUREMENT (returns computed macros)
    suspend fun getMacrosForMeasurement(fdcId: Long, userInput: MeasurementInput): PortionMacros =
        withContext(Dispatchers.IO) {
            val detail = api.getFood(fdcId)

            // ensure the food exists locally (for dedupe & logging)
            val foodId = db.foodDao().upsert(detail.toFoodEntity())
            val existing = db.portionDao().getForFood(foodId)
            if (existing.isEmpty()) {
                db.portionDao().insertAll(detail.toPortions(foodId))
            }

            // resolve grams for the user's input
            val grams = resolveGrams(userInput, detail, db.portionDao().getForFood(foodId))

            // compute from per-100g baseline (your schema)
            val f = db.foodDao().getById(foodId)!!
            val factor = grams / 100.0
            PortionMacros(
                calories = f.kcalPer100g * factor,
                protein = f.proteinPer100g * factor,
                carbohydrate = f.carbPer100g * factor,
                fat = f.fatPer100g * factor,
                resolvedGramWeight = grams
            )
        }

    // 3) LOG TO HISTORY (for quick reuse next time)
    suspend fun logIntake(userId: Int, foodId: Long, grams: Double, note: String? = null): Long =
        withContext(Dispatchers.IO) {
            val f = db.foodDao().getById(foodId) ?: error("Food not found")
            val factor = grams / 100.0
            val log = FoodLog(
                logId = 0L,
                userId = userId,
                foodId = foodId,
                grams = grams,
                calories = f.kcalPer100g * factor,
                protein = f.proteinPer100g * factor,
                carbohydrate = f.carbPer100g * factor,
                fat = f.fatPer100g * factor,
                note = note,
                loggedAt = System.currentTimeMillis()
            )
            db.foodLogDao().insert(log)
        }

    // --- helpers ---
    private fun resolveGrams(input: MeasurementInput, detail: FdcModels.FdcFoodDetail, portions: List<Portion>): Double {
        return when (input.type) {
            MeasurementType.GRAM -> input.quantity
            MeasurementType.MILLILITER -> {
                // Try to infer density from an ML or CUP portion; fallback 1 g/ml
                val gPerMl = portions.firstOrNull { it.measurementType == MeasurementType.MILLILITER }?.gramWeight
                    ?: portions.firstOrNull { it.measurementType == MeasurementType.CUP }?.let { it.gramWeight / 240.0 }
                    ?: 1.0
                input.quantity * gPerMl
            }
            MeasurementType.CUP -> portions.firstOrNull { it.measurementType == MeasurementType.CUP }?.let { input.quantity * it.gramWeight }
                ?: input.quantity * 240.0 // fallback (water-like)
            MeasurementType.TABLESPOON -> portions.firstOrNull { it.measurementType == MeasurementType.TABLESPOON }?.let { input.quantity * it.gramWeight }
                ?: input.quantity * (15.0) // ml → g approx
            MeasurementType.TEASPOON -> portions.firstOrNull { it.measurementType == MeasurementType.TEASPOON }?.let { input.quantity * it.gramWeight }
                ?: input.quantity * (5.0)
            MeasurementType.FL_OUNCE -> portions.firstOrNull { it.measurementType == MeasurementType.FL_OUNCE }?.let { input.quantity * it.gramWeight }
                ?: input.quantity * 29.5735
            MeasurementType.OUNCE -> input.quantity * 28.3495
            MeasurementType.POUND -> input.quantity * 453.592
            MeasurementType.PIECE -> portions.firstOrNull { it.measurementType == MeasurementType.PIECE }?.let { input.quantity * it.gramWeight }
                ?: error("No 'piece' mapping for this food; pick grams or a listed portion.")
            MeasurementType.SANDOK -> portions.firstOrNull { it.measurementType == MeasurementType.SANDOK }?.let { input.quantity * it.gramWeight }
                ?: error("No 'sandok' mapping for this food; add a default for rice/soups.")
        }
    }
}

// Simple holder for what the user typed/selected:
data class MeasurementInput(val type: MeasurementType, val quantity: Double)

data class PortionMacros(
    val calories: Double,
    val protein: Double,
    val carbohydrate: Double,
    val fat: Double,
    val resolvedGramWeight: Double
)
