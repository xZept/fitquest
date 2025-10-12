package com.example.fitquest

import MidnightMacroSnapshotWorker
import android.app.Application
import androidx.core.view.WindowCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.fitquest.fdc.FdcApi
import com.example.fitquest.fdc.FdcService
import com.example.fitquest.BuildConfig
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.data.repository.FoodRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class FitQuestApp : Application() {
    lateinit var fdcService: FdcService
        private set
    lateinit var db: AppDatabase

    val foodRepository: FoodRepository by lazy { FoodRepository(fdcService, db) }

    override fun onCreate() {
        super.onCreate()

        WeightReminderScheduler.scheduleNext6am(this)

        // Log macros for the day every 12 MN
        scheduleMidnightMacroSnapshot()

        // For debugging
        val key = BuildConfig.FDC_API_KEY
        android.util.Log.d("FDC", "API key length = ${key.length}")

        // BuildConfig is available here (app module)
        fdcService = FdcApi.create { BuildConfig.FDC_API_KEY }

        db = AppDatabase.getInstance(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }


    // Function for logging macros every 11:59 PM
    private fun scheduleMidnightMacroSnapshot() {
        val zone = ZoneId.of("Asia/Manila")
        val now = ZonedDateTime.now(zone)
        val nextRun = now.toLocalDate().plusDays(1).atTime(23, 59).atZone(zone)
        val initialDelay = java.time.Duration.between(now, nextRun).toMinutes()

        val request = PeriodicWorkRequestBuilder<MidnightMacroSnapshotWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .addTag("macroSnapshotDaily")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "macroSnapshotDaily",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
