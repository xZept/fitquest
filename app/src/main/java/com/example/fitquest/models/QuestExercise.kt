package com.example.fitquest.models

data class QuestExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    var order: Int = 0,
    // make optional so we can construct quick dummy/basic plans without passing this every time
    val equipment: String? = null,
    val movementPattern: String? = null,
    val primaryMover: String? = null
)