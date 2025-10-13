package com.example.fitquest

import MidnightMacroSnapshotWorker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MacroSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MacroSnapshot", "Alarm fired -> reschedule next 23:59 PHT and enqueue worker")
        // Reschedule the *next* day first (same pattern as weight receiver)
        ReminderScheduler.scheduleNext2359PHT(context.applicationContext)

        // Reuse the existing business logic via your Worker (idempotent by dayKey)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<MidnightMacroSnapshotWorker>().build()
        )
    }
}
