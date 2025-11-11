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

    private val zone = ZoneId.of("Asia/Manila")

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


    private fun weekStartOf(dayKey: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val monday = LocalDate.of(y, m, d).with(java.time.DayOfWeek.MONDAY)
        return monday.year * 10_000 + monday.monthValue * 100 + monday.dayOfMonth
    }

    private fun plusDays(dayKey: Int, days: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val dt = LocalDate.of(y, m, d).plusDays(days.toLong())
        return dt.year * 10_000 + dt.monthValue * 100 + dt.dayOfMonth
    }

    suspend fun activeDayKeys(userId: Int, limit: Int = 90): List<Int> = withContext(Dispatchers.IO) {
        val foodDays  = db.foodLogDao().distinctDayKeys(userId, limit)
        val diaryDays = db.macroDiaryDao().distinctDayKeys(userId, limit)
        (foodDays + diaryDays).distinct().sortedDescending()
    }
    suspend fun dailyHistory(userId: Int, limit: Int = 30): List<DailySummary> = withContext(Dispatchers.IO) {
        activeDayKeys(userId, limit).take(limit).map { dk -> dailySummary(userId, dk) }
    }

    suspend fun weeklyHistory(userId: Int, limitWeeks: Int = 12): List<WeeklySummary> = withContext(Dispatchers.IO) {
        val days = activeDayKeys(userId, 90)
        val grouped = days.groupBy { weekStartOf(it) }
            .toSortedMap(compareByDescending { it })

        val out = mutableListOf<WeeklySummary>()
        for ((wkStart, _) in grouped) {
            val wkEnd = plusDays(wkStart, 6)
            out += weeklySummary(userId, wkEnd) // uses your existing function
            if (out.size >= limitWeeks) break
        }
        out
    }

    // ---------- EXISTING (with a small fix) ----------
    suspend fun dailySummary(userId: Int, dayKey: Int): DailySummary = withContext(Dispatchers.IO) {

        val existing = db.macroDiaryDao().get(userId, dayKey)
        if (existing != null) return@withContext toDaily(existing, userId)

        val totals = db.foodLogDao().totalsForDay(userId, dayKey)
        val plan   = db.macroPlanDao().getLatestForUser(userId)
        val hasIntake = (totals.calories > 0.0 || totals.protein > 0.0 ||
                totals.carbohydrate > 0.0 || totals.fat > 0.0)

        val workoutsToday = db.workoutSessionDao().countCompletedBetween(
            userId, startOfDayMs(dayKey), endOfDayMs(dayKey)
        )

        if (!hasIntake) {
            val planCalories = plan?.calories ?: 0
            val planProtein  = plan?.protein  ?: 0
            val planCarbs    = plan?.carbs    ?: 0
            val planFat      = plan?.fat      ?: 0
            return@withContext DailySummary(
                dayKey,
                0, planCalories,
                0, planProtein,
                0, planCarbs,
                0, planFat,
                kcalDeviation = if (planCalories > 0) -planCalories else 0,
                proteinHitPct = 0,
                workoutsCompletedToday = workoutsToday
            )
        }

        val md = MacroDiary(
            userId = userId,
            dayKey = dayKey,
            calories = totals.calories.toInt(),
            protein  = totals.protein.toInt(),
            carbs    = totals.carbohydrate.toInt(),
            fat      = totals.fat.toInt(),
            planCalories = plan?.calories ?: 0,
            planProtein  = plan?.protein  ?: 0,
            planCarbs    = plan?.carbs    ?: 0,
            planFat      = plan?.fat      ?: 0,
            capturedAt   = System.currentTimeMillis()
        )
        db.macroDiaryDao().upsert(md)
        toDaily(md, userId)
    }

    suspend fun weeklySummary(userId: Int, endDayKey: Int): WeeklySummary =
        withContext(Dispatchers.IO) {
            val from = decrementDayKey(endDayKey, 6) // 7-day window
            val list = db.macroDiaryDao().between(userId, from, endDayKey)

            // Ensure all days present
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
        val latest = db.macroPlanDao().getLatestForUser(userId)
        val planCalories = if (md.planCalories > 0) md.planCalories else (latest?.calories ?: 0)
        val planProtein  = if (md.planProtein  > 0) md.planProtein  else (latest?.protein  ?: 0)
        val planCarbs    = if (md.planCarbs    > 0) md.planCarbs    else (latest?.carbs    ?: 0)
        val planFat      = if (md.planFat      > 0) md.planFat      else (latest?.fat      ?: 0)

        val proteinPct = if (planProtein > 0) ((md.protein * 100.0) / planProtein).toInt() else 0
        val workouts = db.workoutSessionDao().countCompletedBetween(
            userId, startOfDayMs(md.dayKey), endOfDayMs(md.dayKey)
        )

        return DailySummary(
            md.dayKey,
            md.calories, planCalories,
            md.protein,  planProtein,
            md.carbs,    planCarbs,
            md.fat,      planFat,
            kcalDeviation = (md.calories - planCalories),
            proteinHitPct = proteinPct.coerceIn(0, 200),
            workoutsCompletedToday = workouts
        )
    }

    private fun decrementDayKey(dayKey: Int, days: Int): Int {
        val y = dayKey / 10_000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val dt = LocalDate.of(y, m, d).minusDays(days.toLong())
        return dt.year * 10_000 + dt.monthValue * 100 + dt.dayOfMonth
    }
}
