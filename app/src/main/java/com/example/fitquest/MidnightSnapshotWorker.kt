package com.example.fitquest.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.MacroDiary
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.flow.first
import java.time.*

class MidnightSnapshotWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    private val zone = ZoneId.of("Asia/Manila")

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = AppDatabase.getInstance(ctx)
        val uid = DataStoreManager.getUserId(ctx).first()
        if (uid == -1) return Result.success()

        // We snapshot YESTERDAY after midnight
        val today = LocalDate.now(zone)
        val yday  = today.minusDays(1)
        val yKey  = yday.year * 10_000 + yday.monthValue * 100 + yday.dayOfMonth

        // Skip if already snapshotted
        if (db.macroDiaryDao().get(uid, yKey) != null) return Result.success()

        // Compute totals for yesterday
        val totals = db.foodLogDao().totalsForDay(uid, yKey)
        val hasIntake = (totals.calories > 0.0 || totals.protein > 0.0 ||
                totals.carbohydrate > 0.0 || totals.fat > 0.0)

        val startMs = yday.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs   = startMs + 86_399_000L
        val workouts = db.workoutSessionDao().countCompletedBetween(uid, startMs, endMs)

        // Only write a diary row if there was *something* (intake or workouts)
        if (!hasIntake && workouts == 0) return Result.success()

        val plan = db.macroPlanDao().getLatestForUser(uid)

        db.macroDiaryDao().upsert(
            MacroDiary(
                userId = uid,
                dayKey = yKey,
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
        )

        return Result.success()
    }
}
