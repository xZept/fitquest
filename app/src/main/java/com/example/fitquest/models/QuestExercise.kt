package com.example.fitquest.models

data class QuestExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    var order: Int = 0,
    val equipment: String? = null,
    val movementPattern: String? = null,
    val primaryMover: String? = null
)