package com.example.fitquest.models

data class Exercise(
    val id: Int,
    val name: String,
    val equipment: String,
    val variation: String,
    val utility: String,
    val mechanics: String,
    val force: String,
    val targetMuscles: String,
    val mainMuscle: String,
    val restrictedFor: List<String>,
    val difficulty: Int,
    val type: String,
    val description: String
)
