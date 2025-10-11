package com.example.fitquest.repository

import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.MacroDiary
import com.example.fitquest.models.DailySummary
import com.example.fitquest.models.WeeklySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProgressRepository(private val db: AppDatabase) {

    private val zone = ZoneId.of("Asia/Manila") // matches your dayKey convention

    private fun dayKeyFor(tsMs: Long): Int {
        val zdt = Instant.ofEpochMilli(tsMs).atZone(zone)
        return zdt.year * 10_000 + zdt.monthValue * 100 + zdt.dayOfMonth
    }

    private fun startOfDayMs(dayKey: Int): Long {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        return LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    private fun endOfDayMs(dayKey: Int): Long = startOfDayMs(dayKey) + 86_399_000L

    suspend fun dailySummary(userId: Int, dayKey: Int): DailySummary = withContext(Dispatchers.IO) {
        val md = db.macroDiaryDao().get(userId, dayKey)
        val calories = md?.calories ?: 0
        val planCalories = md?.planCalories ?: 0
        val protein = md?.protein ?: 0
        val planProtein = md?.planProtein ?: 0
        val carbs = md?.carbs ?: 0
        val planCarbs = md?.planCarbs ?: 0
        val fat = md?.fat ?: 0
        val planFat = md?.planFat ?: 0

        val kcalDev = if (planCalories > 0) calories - planCalories else 0
        val proteinPct = if (planProtein > 0) ((protein * 100.0) / planProtein).toInt() else 0

        val workoutsToday = db.workoutSessionDao().countCompletedBetween(
            userId,
            startOfDayMs(dayKey),
            endOfDayMs(dayKey)
        )

        DailySummary(
            dayKey, calories, planCalories, protein, planProtein, carbs, planCarbs, fat, planFat,
            kcalDeviation = kcalDev,
            proteinHitPct = proteinPct.coerceIn(0, 200),
            workoutsCompletedToday = workoutsToday
        )
    }

    suspend fun weeklySummary(userId: Int, endDayKey: Int): WeeklySummary =
        withContext(Dispatchers.IO) {
            val from = decrementDayKey(endDayKey, 6) // 7-day window
            val list = db.macroDiaryDao().between(userId, from, endDayKey)

            // Ensure all days present (fill missing with zeros)
            val filled = (0..6).map { i ->
                val dk = decrementDayKey(endDayKey, 6 - i)
                val row = list.find { it.dayKey == dk }
                if (row != null) toDaily(row, userId)
                else DailySummary(dk, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, workoutsCompletedToday = 0)
            }

            val avgDev = filled.map { it.kcalDeviation }.average().toInt()
            val avgProteinPct = filled.map { it.proteinHitPct }.average().toInt()

            val allWorkouts = db.workoutSessionDao().countCompletedBetween(
                userId,
                startOfDayMs(from),
                endOfDayMs(endDayKey)
            )

            WeeklySummary(from, endDayKey, filled, avgDev, avgProteinPct, allWorkouts)
        }

    private suspend fun toDaily(md: MacroDiary, userId: Int): DailySummary {
        val proteinPct = if (md.planProtein > 0) ((md.protein * 100.0) / md.planProtein).toInt() else 0
        val workouts = db.workoutSessionDao().countCompletedBetween(userId, startOfDayMs(md.dayKey), endOfDayMs(md.dayKey))
        return DailySummary(
            md.dayKey,
            md.calories, md.planCalories,
            md.protein, md.planProtein,
            md.carbs, md.planCarbs,
            md.fat, md.planFat,
            kcalDeviation = (md.calories - md.planCalories),
            proteinHitPct = proteinPct.coerceIn(0, 200),
            workoutsCompletedToday = workouts
        )
    }

    // decrement by N days using DayKey arithmetic (YYYYMMDD)
    private fun decrementDayKey(dayKey: Int, days: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val dt = LocalDate.of(y, m, d).minusDays(days.toLong())
        return dt.year * 10_000 + dt.monthValue * 100 + dt.dayOfMonth
    }
}