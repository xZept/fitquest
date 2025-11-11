package com.example.fitquest.models

data class Tips(
    val id: Int,
    val category: String,
    val tip: String,

    val split: String = "any",
    val focus: String = "any",
    val goal: String = "any",
    val activityLevel: String = "any",
    val tags: String = ""
)
