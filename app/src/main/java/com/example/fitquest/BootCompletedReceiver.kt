package com.example.fitquest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ReminderScheduler.scheduleNext6am(context.applicationContext)
                ReminderScheduler.scheduleNext2359PHT(context.applicationContext)

            }
        }
    }
}
