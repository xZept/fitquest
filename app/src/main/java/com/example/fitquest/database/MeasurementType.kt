package com.example.fitquest.database

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


    fun gramsFor(
        quantity: Double,
        portionMap: Map<MeasurementType, Double> = emptyMap(),
        densityGPerMl: Double = 1.0
    ): Double {
        portionMap[this]?.let { gramsPerOne -> return quantity * gramsPerOne }

        return when (kind) {
            Kind.MASS -> quantity * (gramsPerUnit ?: error("Missing gramsPerUnit for $this"))
            Kind.VOLUME -> quantity * (mlPerUnit ?: error("Missing mlPerUnit for $this")) * densityGPerMl
            Kind.COUNT -> error("No portion mapping for COUNT unit $this. Provide grams in portionMap.")
        }
    }

    companion object {

        fun tryParse(raw: String?): MeasurementType? {
            val s = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.name.equals(s, true) || s in it.synonyms }
        }
    }
}