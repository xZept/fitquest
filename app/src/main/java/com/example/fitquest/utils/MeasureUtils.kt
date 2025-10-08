package com.example.fitquest.util

enum class UnitKind { GRAM, MILLILITER, PIECE }

data class Measure(
    val kind: UnitKind,
    val name: String,             // e.g., "g", "ml", "piece", "medium"
    val gramsPerUnit: Double?     // required for PIECE; null for pure grams
)

object MeasureUtils {
    // Synonyms that should be treated as "piece"
    private val pieceAliases = setOf(
        "piece", "pieces", "pc", "pcs", "unit", "medium", "small", "large"
    )

    fun isPieceLike(name: String): Boolean =
        pieceAliases.contains(name.trim().lowercase())

    /**
     * Normalize raw measure labels from your data source into our Measure model.
     * - g/gram -> GRAM
     * - ml/milliliter -> MILLILITER
     * - any piece-like synonym -> PIECE (needs gramsPerUnit != null)
     */
    fun normalizeMeasures(
        raw: List<Pair<String, Double?>> // Pair<label, gramsPerUnit?> from your API/db
    ): List<Measure> {
        return raw.mapNotNull { (label, gPerUnit) ->
            val normalized = label.trim().lowercase()
            when {
                normalized in setOf("g", "gram", "grams") ->
                    Measure(UnitKind.GRAM, "g", 1.0)
                normalized in setOf("ml", "milliliter", "milliliters") ->
                    Measure(UnitKind.MILLILITER, "ml", null)
                isPieceLike(normalized) && gPerUnit != null ->
                    Measure(UnitKind.PIECE, "piece", gPerUnit)
                // If it's piece-like but we don't have grams, skip here; we'll show a message if chosen.
                else -> null
            }
        }.distinctBy { it.kind to it.gramsPerUnit }
    }

    fun convertToGrams(quantity: Double, measure: Measure): Double {
        return when (measure.kind) {
            UnitKind.GRAM -> quantity
            UnitKind.MILLILITER -> quantity // if you don’t have density, assume 1:1 (or adjust if you do)
            UnitKind.PIECE -> {
                requireNotNull(measure.gramsPerUnit) { "gramsPerUnit required for PIECE" }
                quantity * measure.gramsPerUnit
            }
        }
    }
}
