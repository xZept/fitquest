package com.example.fitquest.models

data class DailySummary(
    val dayKey: Int,
    val calories: Int,
    val planCalories: Int,
    val protein: Int,
    val planProtein: Int,
    val carbs: Int,
    val planCarbs: Int,
    val fat: Int,
    val planFat: Int,
    val kcalDeviation: Int,
    val proteinHitPct: Int,
    val workoutsCompletedToday: Int
)

data class WeeklySummary(
    val fromDayKey: Int,
    val toDayKey: Int,
    val days: List<DailySummary>,
    val avgKcalDeviation: Int,
    val proteinHitPctAvg: Int,
    val workoutsCompleted: Int
)
