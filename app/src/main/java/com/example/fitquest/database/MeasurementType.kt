package com.example.fitquest.database

/**
 * Measurement units supported by FitQuest.
 *
 * Conversions:
 * - MASS: uses gramsPerUnit
 * - VOLUME: uses mlPerUnit × densityGPerMl (fallback 1.0 if unknown)
 * - COUNT (PIECE, SANDOK): must come from per-food portion mappings
 *
 * Notes:
 * - CUP default set to 240 mL (common nutrition convention). Adjust if your app uses 236.588 mL.
 * - SANDOK is a household ladle; treat as approximate and map per-food (e.g., rice: 1 sandok ≈ 150 g).
 */
enum class MeasurementType(
    val displayName: String,
    val kind: Kind,
    val gramsPerUnit: Double? = null, // for MASS
    val mlPerUnit: Double? = null,    // for VOLUME
    val synonyms: Set<String> = emptySet()
) {
    // MASS
    GRAM("g", Kind.MASS, gramsPerUnit = 1.0, synonyms = setOf("g", "gram", "grams")),
    OUNCE("oz", Kind.MASS, gramsPerUnit = 28.3495, synonyms = setOf("oz", "ounce", "ounces")),
    POUND("lb", Kind.MASS, gramsPerUnit = 453.592, synonyms = setOf("lb", "lbs", "pound", "pounds")),

    // VOLUME
    MILLILITER("ml", Kind.VOLUME, mlPerUnit = 1.0, synonyms = setOf("ml", "milliliter", "milliliters")),
    FL_OUNCE("fl oz", Kind.VOLUME, mlPerUnit = 29.5735, synonyms = setOf("floz", "fl oz", "fluid ounce")),
    TEASPOON("tsp", Kind.VOLUME, mlPerUnit = 5.0, synonyms = setOf("tsp", "teaspoon", "teaspoons")),
    TABLESPOON("tbsp", Kind.VOLUME, mlPerUnit = 15.0, synonyms = setOf("tbsp", "tablespoon", "tablespoons")),
    CUP("cup", Kind.VOLUME, mlPerUnit = 240.0, synonyms = setOf("cup", "cups")),

    // COUNT / HOUSEHOLD
    PIECE("piece", Kind.COUNT, synonyms = setOf("pc", "piece", "pieces")),
    SANDOK("sandok", Kind.COUNT, synonyms = setOf("sandok", "ladle"));

    enum class Kind { MASS, VOLUME, COUNT }

    val isMass get() = kind == Kind.MASS
    val isVolume get() = kind == Kind.VOLUME
    val isCount get() = kind == Kind.COUNT

    /**
     * Convert a quantity of this MeasurementType into edible grams.
     *
     * @param quantity user-entered amount (e.g., 1.5 cups)
     * @param portionMap per-food mapping of this unit to grams for ONE unit (e.g., CUP -> 158.0)
     * @param densityGPerMl fallback density for volume units when no portion mapping exists (default 1.0)
     *
     * @return grams (Double)
     * @throws IllegalArgumentException if COUNT unit has no mapping
     */
    fun gramsFor(
        quantity: Double,
        portionMap: Map<MeasurementType, Double> = emptyMap(),
        densityGPerMl: Double = 1.0
    ): Double {
        // Prefer per-food mapping if provided (e.g., PIECE, SANDOK, CUP for this food)
        portionMap[this]?.let { gramsPerOne -> return quantity * gramsPerOne }

        return when (kind) {
            Kind.MASS -> quantity * (gramsPerUnit ?: error("Missing gramsPerUnit for $this"))
            Kind.VOLUME -> quantity * (mlPerUnit ?: error("Missing mlPerUnit for $this")) * densityGPerMl
            Kind.COUNT -> error("No portion mapping for COUNT unit $this. Provide grams in portionMap.")
        }
    }

    companion object {
        /**
         * Try to parse a user string (e.g., "g", "grams", "tbsp", "sandok") into a MeasurementType.
         * Returns null if nothing matches.
         */
        fun tryParse(raw: String?): MeasurementType? {
            val s = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.name.equals(s, true) || s in it.synonyms }
        }
    }
}