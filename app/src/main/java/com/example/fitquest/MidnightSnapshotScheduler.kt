package com.example.fitquest

import android.content.Context
import androidx.work.*
import com.example.fitquest.work.MidnightSnapshotWorker
import java.time.*
import java.util.concurrent.TimeUnit

object MidnightSnapshotScheduler {
    private const val UNIQUE_NAME = "midnight_snapshot"

    fun schedule(context: Context) {
        val zone = ZoneId.of("Asia/Manila")
        val now  = ZonedDateTime.now(zone)
        val next = now.plusDays(1).toLocalDate().atTime(0, 5).atZone(zone)
        val initialMs = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val req = PeriodicWorkRequestBuilder<MidnightSnapshotWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialMs, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }
}