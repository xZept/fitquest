package com.example.fitquest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {

    fun scheduleNext6am(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, WeightReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Inexact fallback
            if (Build.VERSION.SDK_INT >= 19) am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60 * 1000L, pi)
            else am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }

        when {
            Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 -> am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else                        -> am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** DEBUG: fire in N minutes (kept, but now with safe fallback). */
    fun scheduleInMinutes(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, WeightReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, 1001, intent, flags)

        val triggerAt = System.currentTimeMillis() + minutes * 60_000L

        // Fallback if exact-alarm permission isn’t granted on API 31+
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            if (Build.VERSION.SDK_INT >= 19) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 1000L, pi) // small window for debug
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }


    /** DEBUG: fire in N seconds (use this for 15s). */
    fun scheduleInSeconds(ctx: Context, seconds: Int) =
        scheduleInMillis(ctx, seconds * 1_000L, requestCode = 1002)

    private fun scheduleInMillis(ctx: Context, delayMs: Long, requestCode: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, WeightReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val triggerAt = System.currentTimeMillis() + delayMs

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Inexact fallback for debug
            if (Build.VERSION.SDK_INT >= 19) am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5_000L, pi)
            else am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }

        when {
            Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 -> am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else                        -> am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleNext2359PHT(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, MacroSnapshotReceiver::class.java)

        // Distinct request code so it doesn't collide with weight alarm
        val pi = PendingIntent.getBroadcast(
            ctx, 1011, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val zone = ZoneId.of("Asia/Manila")
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(23).withMinute(59).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Inexact fallback (may fire within ~15 min window)
            if (Build.VERSION.SDK_INT >= 19) am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60 * 1000L, pi)
            else am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }

        when {
            Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 -> am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else                        -> am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
