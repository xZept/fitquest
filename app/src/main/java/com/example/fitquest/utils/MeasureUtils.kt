package com.example.fitquest.util

enum class UnitKind { GRAM, MILLILITER, PIECE }

data class Measure(
    val kind: UnitKind,
    val name: String,
    val gramsPerUnit: Double?
)

object MeasureUtils {
    private val pieceAliases = setOf(
        "piece", "pieces", "pc", "pcs", "unit", "medium", "small", "large"
    )

    fun isPieceLike(name: String): Boolean =
        pieceAliases.contains(name.trim().lowercase())

    fun normalizeMeasures(
        raw: List<Pair<String, Double?>>
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

                else -> null
            }
        }.distinctBy { it.kind to it.gramsPerUnit }
    }

    fun convertToGrams(quantity: Double, measure: Measure): Double {
        return when (measure.kind) {
            UnitKind.GRAM -> quantity
            UnitKind.MILLILITER -> quantity
            UnitKind.PIECE -> {
                requireNotNull(measure.gramsPerUnit) { "gramsPerUnit required for PIECE" }
                quantity * measure.gramsPerUnit
            }
        }
    }
}
