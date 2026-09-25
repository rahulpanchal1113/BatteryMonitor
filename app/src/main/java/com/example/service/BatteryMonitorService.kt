package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.BatteryApplication
import com.example.MainActivity
import com.example.R
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class BatteryMonitorService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastRecordedLevel = -1
    private var wasCharging: Boolean? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val app = applicationContext as? BatteryApplication ?: return
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    serviceScope.launch {
                        wasCharging = true
                        app.repository.onPowerConnected()
                        updateNotification()
                        BatteryWidgetProvider.updateAllWidgets(applicationContext)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    wasCharging = false
                    val prefs = applicationContext.getSharedPreferences("widget_battery_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_plugged", false).apply()
                    serviceScope.launch {
                        app.repository.onPowerDisconnected()
                        updateNotification()
                        BatteryWidgetProvider.updateAllWidgets(applicationContext)
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateFromBatteryChanged(intent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }

        // Periodic checkpoint every 30 seconds while charging to ensure consistent curve data
        serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                val app = applicationContext as? BatteryApplication ?: continue
                val status = app.repository.queryCurrentBatteryStatus()
                if (status.isCharging) {
                    app.repository.logBatterySample()
                    updateNotification()
                    BatteryWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val app = applicationContext as? BatteryApplication
        val status = app?.repository?.liveBatteryStatus?.value
        val isFahrenheit = app?.repository?.isFahrenheit() ?: false

        val tempStr = if (status != null && status.tempCelsius > 0f) {
            if (isFahrenheit) {
                String.format(Locale.US, "%.1f°F", status.tempFahrenheit)
            } else {
                String.format(Locale.US, "%.1f°C", status.tempCelsius)
            }
        } else ""

        val contentText = if (status != null && status.isCharging) {
            val plug = if (status.plugType.isNotBlank() && !status.plugType.equals("Battery", ignoreCase = true)) status.plugType else "Plugged In"
            if (tempStr.isNotEmpty()) "Charging: ${status.level}% • $plug ($tempStr)" else "Charging: ${status.level}% • $plug"
        } else if (status != null) {
            if (tempStr.isNotEmpty()) "Battery: ${status.level}% • ${status.status} ($tempStr)" else "Battery: ${status.level}% • ${status.status}"
        } else {
            getString(R.string.notification_charging_active)
        }

        return NotificationCompat.Builder(this, BatteryApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun updateFromBatteryChanged(intent: Intent) {
        val app = applicationContext as? BatteryApplication ?: return
        serviceScope.launch {
            val prevChargingState = wasCharging
            app.repository.updateLiveStatus()
            val currentStatus = app.repository.liveBatteryStatus.value

            wasCharging = currentStatus.isCharging

            if (prevChargingState != null && prevChargingState != currentStatus.isCharging) {
                if (currentStatus.isCharging) {
                    app.repository.onPowerConnected()
                } else {
                    app.repository.onPowerDisconnected()
                }
            } else if (currentStatus.isCharging && currentStatus.level != lastRecordedLevel) {
                lastRecordedLevel = currentStatus.level
                app.repository.logBatterySample()
            }

            updateNotification()
            BatteryWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
