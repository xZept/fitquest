// WeightReminderScheduler.kt
package com.example.fitquest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime

object WeightReminderScheduler {

    fun scheduleNext6am(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, Intent(ctx, WeightReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.toInstant().toEpochMilli()

        // No exact-alarm privilege? Use inexact path (won’t crash).
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            if (Build.VERSION.SDK_INT >= 19) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60_000L, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            return
        }

        when {
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else ->
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleInMinutes(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, Intent(ctx, WeightReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            if (Build.VERSION.SDK_INT >= 19) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 2 * 60_000L, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            return
        }

        when {
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else ->
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun immutableFlag() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
