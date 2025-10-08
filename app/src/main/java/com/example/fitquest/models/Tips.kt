package com.example.fitquest.models

data class Tips(
    val id: Int,
    val category: String,         // general | macro | workout | recovery
    val tip: String,

    // Filters (all normalized to lowercase; "any" or "" acts as wildcard)
    val split: String = "any",        // push | pull | legs | upper | any
    val focus: String = "any",        // general | hypertrophy | strength | any
    val goal: String = "any",         // lose fat | build muscle | maintain | any
    val activityLevel: String = "any",// sedentary | lightly active | moderately active | very active | any

    val tags: String = ""
)
