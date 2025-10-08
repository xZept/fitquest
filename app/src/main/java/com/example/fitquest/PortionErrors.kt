package com.example.fitquest

import com.example.fitquest.database.MeasurementType

// Put this in a shared file (e.g., PortionErrors.kt)
class MissingPortionException(
    val unit: MeasurementType,
    val fdcId: Long,
    val description: String,
    val available: Set<MeasurementType>
) : IllegalArgumentException(
    "Unit '$unit' not available for '$description' (FDC $fdcId). " +
            "Available: ${available.joinToString()}"
)
