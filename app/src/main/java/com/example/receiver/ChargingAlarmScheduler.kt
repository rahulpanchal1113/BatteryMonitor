package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ChargingAlarmScheduler {

    private const val REQUEST_CODE_CHECKPOINT = 4001
    private const val REQUEST_CODE_WIDGET_PERIODIC = 4002

    const val ACTION_CHARGING_CHECKPOINT = "com.example.ACTION_CHARGING_CHECKPOINT"
    const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"

    fun scheduleNextCheckpoint(context: Context, delayMs: Long = 60_000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ChargingCheckpointReceiver::class.java).apply {
            action = ACTION_CHARGING_CHECKPOINT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CHECKPOINT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMs = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (_: Exception) {
            // Fallback for security exceptions or strict alarm limits
        }
    }

    fun scheduleWidgetPeriodicRefresh(context: Context, delayMs: Long = 15 * 60_000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ChargingCheckpointReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WIDGET_PERIODIC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMs = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (_: Exception) {
            // Fallback
        }
    }

    fun cancelCheckpoints(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ChargingCheckpointReceiver::class.java).apply {
            action = ACTION_CHARGING_CHECKPOINT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CHECKPOINT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
