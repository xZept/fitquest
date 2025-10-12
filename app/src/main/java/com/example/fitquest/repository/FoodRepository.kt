package com.example.fitquest.data.repository

import android.util.Log
import com.example.fitquest.fdc.FdcModels
import com.example.fitquest.fdc.FdcService
import com.example.fitquest.fdc.toFoodEntity
import com.example.fitquest.fdc.toPortions
import com.example.fitquest.database.*
import com.example.fitquest.database.MeasurementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.ZoneId

class FoodRepository(
    private val api: FdcService,
    private val db: AppDatabase
) {
    suspend fun deleteLog(logId: Long) {
        db.foodLogDao().deleteById(logId)
    }

    // Overload if you prefer deleting by entity
    suspend fun deleteLog(log: FoodLog) {
        db.foodLogDao().delete(log)
    }

    suspend fun updateLogServing(
        logId: Long,
        newGrams: Double,
        inputUnit: MeasurementType? = null,
        inputQuantity: Double? = null
    ): Int = withContext(Dispatchers.IO) {
        val log  = db.foodLogDao().getById(logId) ?: return@withContext 0
        val food = db.foodDao().getById(log.foodId) ?: return@withContext 0

        val factor = newGrams / 100.0
        val cals = food.kcalPer100g * factor
        val pro  = food.proteinPer100g * factor
        val carb = food.carbPer100g * factor
        val fat  = food.fatPer100g * factor

        db.foodLogDao().updateServing(
            logId, newGrams, cals, pro, carb, fat, inputUnit, inputQuantity
        )
    }


    suspend fun getTodayLogs(userId: Int, zone: ZoneId = ZoneId.of("Asia/Manila")): List<FoodLogRow> =
        withContext(Dispatchers.IO) {
            val dk = dayKeyFor(System.currentTimeMillis(), zone)
            db.foodLogDao().logsForDay(userId, dk)
        }

    suspend fun getTodayTotals(userId: Int, zone: ZoneId = ZoneId.of("Asia/Manila")): DayTotals {
        val dk = dayKeyFor(System.currentTimeMillis(), zone)
        return db.foodLogDao().totalsForDay(userId, dk)
    }

    // 1) SEARCH & SHOW MATCHES
    suspend fun search(term: String, page: Int = 1, pageSize: Int = 50) =
        api.searchFoods(query = term, pageNumber = page, pageSize = pageSize).foods

    // 2) GET MACROS FOR A GIVEN MEASUREMENT (returns computed macros)
    suspend fun getMacrosForMeasurement(fdcId: Long, userInput: MeasurementInput): PortionMacros =
        withContext(Dispatchers.IO) {
            val detail = api.getFood(fdcId)
            detail.debugDump()

            // ensure the food exists locally (for dedupe & logging)
            val foodId = db.foodDao().upsertKeepId(detail.toFoodEntity())
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

    fun dayKeyFor(instantMs: Long, zone: ZoneId = ZoneId.of("Asia/Manila")): Int {
        val d = Instant.ofEpochMilli(instantMs).atZone(zone).toLocalDate()
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth  // YYYYMMDD
    }

    suspend fun logIntake(
        userId: Int,
        foodId: Long,
        grams: Double,
        mealType: String,
        inputUnit: MeasurementType? = null,
        inputQuantity: Double? = null
    ): Long = withContext(Dispatchers.IO) {
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
            loggedAt = System.currentTimeMillis(),
            dayKey  = dayKeyFor(System.currentTimeMillis()),
            mealType = mealType.uppercase(),
            inputUnit = inputUnit,
            inputQuantity = inputQuantity
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

    suspend fun ensureLocalIdForFdc(fdcId: Long): Long = withContext(Dispatchers.IO) {
        val detail = api.getFood(fdcId)
        ensureFood(detail)
    }

    private suspend fun ensureFood(detail: FdcModels.FdcFoodDetail): Long =
        withContext(Dispatchers.IO) { db.foodDao().upsertKeepId(detail.toFoodEntity()) }

    suspend fun recentFoodsAsSearchItems(limit: Int = 20): List<FdcModels.FdcSearchFood> =
        withContext(Dispatchers.IO) {
            db.foodDao().recent(limit)
                // only keep those that came from FDC so adapter can reuse preview logic
                .mapNotNull { f ->
                    val fdcId = f.sourceRef?.toLongOrNull() ?: return@mapNotNull null
                    FdcModels.FdcSearchFood(
                        fdcId = fdcId,
                        description = f.foodName,
                        dataType = f.source ?: "local"
                    )
                }
        }

    private val previewCache = mutableMapOf<Long, PreviewMacros>()

    suspend fun previewMacrosPer100g(fdcId: Long): PreviewMacros = withContext(Dispatchers.IO) {
        previewCache[fdcId]?.let { return@withContext it }
        val d = api.getFood(fdcId)
        val food = d.toFoodEntity() // uses n(...) above
        val out = PreviewMacros(
            protein = food.proteinPer100g,
            fat     = food.fatPer100g,
            carbs   = food.carbPer100g
        )
        previewCache[fdcId] = out
        out
    }

    suspend fun availableApiPortions(fdcId: Long): List<ApiPortion> = withContext(Dispatchers.IO) {
        val d = api.getFood(fdcId)

        fun isBadLabel(raw: String): Boolean {
            val t = raw.trim()
            if (t.isEmpty()) return true
            // Pure numeric FNDDS portion codes like 90000/62772/40016
            if (t.all { it.isDigit() }) return true
            return false
        }
        fun cleanLabel(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

        val portions = d.foodPortions
            .mapNotNull { p ->
                val g = p.gramWeight ?: return@mapNotNull null

                val desc = p.portionDescription?.trim().orEmpty()
                val mod  = p.modifier?.trim().orEmpty()

                val label = when {
                    desc.isNotBlank() && !isBadLabel(desc) -> cleanLabel(desc)
                    mod.isNotBlank()  && !isBadLabel(mod)  -> cleanLabel(mod)
                    else -> return@mapNotNull null
                }

                ApiPortion(label = label, gramsPerUnit = g)
            }
            .distinctBy { it.label.lowercase() to it.gramsPerUnit }
            .sortedBy { it.label.lowercase() }

        // --- Fallback: if the API has no household portions, offer grams ---
        if (portions.isEmpty()) listOf(ApiPortion(label = "grams (g)", gramsPerUnit = 1.0))
        else portions
    }

    // Get food id from API using the food id from database
    suspend fun fdcIdForFoodId(foodId: Long): Long? = withContext(Dispatchers.IO) {
        db.foodDao().getById(foodId)?.sourceRef?.toLongOrNull()
    }

    suspend fun availableUnitsFor(fdcId: Long): List<MeasurementType> = withContext(Dispatchers.IO) {
        // Ensure cached locally (same flow you use for getMacrosForMeasurement)
        val detail = api.getFood(fdcId)
        val foodId = db.foodDao().upsertKeepId(detail.toFoodEntity())  // persists food
        var portions = db.portionDao().getForFood(foodId)
        if (portions.isEmpty()) {
            db.portionDao().insertAll(detail.toPortions(foodId))
            portions = db.portionDao().getForFood(foodId)
        }

        // Only what API-derived portions gave us (+ GRAM baseline which is always present)
        portions.map { it.measurementType }.distinct()
    }

    suspend fun gramsFor(foodId: Long, unit: MeasurementType, quantity: Double): Double =
        withContext(Dispatchers.IO) {
            val portions = db.portionDao().getForFood(foodId)

            fun perOne(t: MeasurementType): Double? =
                portions.firstOrNull { it.measurementType == t }?.let { it.gramWeight / it.quantity }

            when (unit) {
                MeasurementType.GRAM -> quantity
                MeasurementType.MILLILITER -> {
                    val gPerMl = perOne(MeasurementType.MILLILITER)
                        ?: perOne(MeasurementType.CUP)?.let { it / 240.0 }
                        ?: 1.0
                    quantity * gPerMl
                }
                MeasurementType.CUP -> {
                    val gPerCup = perOne(MeasurementType.CUP) ?: 240.0
                    quantity * gPerCup
                }
                MeasurementType.TABLESPOON -> {
                    val gPerTbsp = perOne(MeasurementType.TABLESPOON)
                        ?: (perOne(MeasurementType.MILLILITER)?.times(15.0))
                        ?: 15.0
                    quantity * gPerTbsp
                }
                MeasurementType.TEASPOON -> {
                    val gPerTsp = perOne(MeasurementType.TEASPOON)
                        ?: (perOne(MeasurementType.MILLILITER)?.times(5.0))
                        ?: 5.0
                    quantity * gPerTsp
                }
                MeasurementType.FL_OUNCE -> {
                    val gPerFloz = perOne(MeasurementType.FL_OUNCE)
                        ?: (perOne(MeasurementType.MILLILITER)?.times(29.5735))
                        ?: 29.5735
                    quantity * gPerFloz
                }
                MeasurementType.OUNCE -> quantity * (MeasurementType.OUNCE.gramsPerUnit ?: 28.3495)
                MeasurementType.POUND -> quantity * (MeasurementType.POUND.gramsPerUnit ?: 453.592)
                MeasurementType.PIECE, MeasurementType.SANDOK -> {
                    val perOne = perOne(unit)
                        ?: error("No per-${unit.displayName} mapping for this food.")
                    quantity * perOne
                }
            }
        }


    suspend fun availableUnitsForFood(foodId: Long): List<MeasurementType> = withContext(Dispatchers.IO) {
        val portions = db.portionDao().getForFood(foodId)
        val units = portions.map { it.measurementType }.distinct()
        if (units.isEmpty()) listOf(MeasurementType.GRAM) else units
    }

    suspend fun availableApiPortionsForFoodId(foodId: Long): List<ApiPortion> {
        val food = db.foodDao().getById(foodId) ?: return listOf(ApiPortion("grams (g)", 1.0))
        val fdcId = food.sourceRef?.toLongOrNull() ?: return listOf(ApiPortion("grams (g)", 1.0))
        return availableApiPortions(fdcId)
    }



}


data class PreviewMacros(
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

// Simple holder for what the user typed/selected:
data class MeasurementInput(val type: MeasurementType, val quantity: Double)

// Keep/put this small DTO in the same file (near other small classes)
data class ApiPortion(
    val label: String,        // e.g., "1 leg", "1 oz yields", "cup, sliced"
    val gramsPerUnit: Double  // grams for ONE unit of that label
)

data class PortionMacros(
    val calories: Double,
    val protein: Double,
    val carbohydrate: Double,
    val fat: Double,
    val resolvedGramWeight: Double
)

fun FdcModels.FdcFoodDetail.debugDump(tag: String = "FDC.dump") {
    Log.d(tag, "fdcId=$fdcId desc='${description}' dataType=$dataType serving=$servingSize $servingSizeUnit")
    Log.d(tag, "labelNutrients=${labelNutrients}")
    if (foodNutrients.isEmpty()) {
        Log.d(tag, "foodNutrients: []")
    } else {
        val preview = foodNutrients.take(20).joinToString(" | ") {
            val num  = it.nutrient?.number ?: it.number
            val name = it.nutrient?.name ?: it.name
            val unit = it.nutrient?.unitName ?: it.unitName
            "$num:$name=${it.amount} $unit"
        }
        Log.d(tag, "foodNutrients(${foodNutrients.size}): $preview")
    }
}



