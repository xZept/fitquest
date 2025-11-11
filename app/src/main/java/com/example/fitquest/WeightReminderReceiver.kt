package com.example.fitquest

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

class WeightReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WeightReminder", "onReceive called; rescheduling next 6am")
        ReminderScheduler.scheduleNext6am(context)

        val isAppInForeground = try {
            ProcessLifecycleOwner.get()
                .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (t: Throwable) {
            Log.w("WeightReminder", "ProcessLifecycleOwner not ready: ${t.message}")
            false
        }

        if (isAppInForeground) {
            Log.d("WeightReminder", "App in foreground → launching WeightPromptActivity")
            context.startActivity(
                Intent(context, WeightPromptActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            Log.d("WeightReminder", "App background → showing notification")
            showNotification(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(ctx: Context) {
        val channelId = "weight_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "Weight reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Daily prompt to log your morning weight" }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val open = Intent(ctx, WeightPromptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 2001, open,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val n = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Morning check-in")
            .setContentText("What’s your weight today?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, false)
            .build()

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("WeightReminder", "POST_NOTIFICATIONS not granted; skipping notify()")
                return
            }
        }
        NotificationManagerCompat.from(ctx).notify(9001, n)
    }
}
