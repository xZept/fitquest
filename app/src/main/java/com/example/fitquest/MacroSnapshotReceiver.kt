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
        ReminderScheduler.scheduleNext2359PHT(context.applicationContext)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<MidnightMacroSnapshotWorker>().build()
        )
    }
}
