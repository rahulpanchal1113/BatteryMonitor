package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.BatteryApplication
import com.example.service.BatteryMonitorService
import com.example.service.ChargingJobService
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? BatteryApplication ?: return

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                handleConnected(context, app)
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                handleDisconnected(context, app)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                handleBootOrReplaced(context, app)
            }
        }
    }

    private fun handleBootOrReplaced(context: Context, app: BatteryApplication) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:BootPowerWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure persistent job scheduler is active for hardware charging detection
                ChargingJobService.scheduleChargingJob(context)

                // Query current hardware battery status after system startup
                val status = app.repository.queryCurrentBatteryStatus()
                if (status.isCharging) {
                    app.repository.onPowerConnected()
                    ChargingAlarmScheduler.scheduleNextCheckpoint(context, 15_000L)
                    val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (_: Exception) {}
                } else {
                    // On battery: schedule periodic widget refresh so it stays current on home screen
                    ChargingAlarmScheduler.scheduleWidgetPeriodicRefresh(context, 15 * 60_000L)
                }
                BatteryWidgetProvider.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e("PowerConnectionReceiver", "Error during boot handling", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    private fun handleConnected(context: Context, app: BatteryApplication) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:PowerConnectedWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ChargingJobService.scheduleChargingJob(context)
                app.repository.onPowerConnected()
                BatteryWidgetProvider.updateAllWidgets(context)
                // Ensure periodic background checkpoint sampling begins right away
                ChargingAlarmScheduler.scheduleNextCheckpoint(context, 15_000L)
            } catch (e: Exception) {
                Log.e("PowerConnectionReceiver", "Error during handleConnected", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }

        // Start active foreground monitor service if allowable by OS
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Foreground service restrictions on Android 12+ background starts are expected
        }
    }

    private fun handleDisconnected(context: Context, app: BatteryApplication) {
        val prefs = context.getSharedPreferences("widget_battery_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_plugged", false).apply()

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:PowerDisconnectedWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.onPowerDisconnected()
                BatteryWidgetProvider.updateAllWidgets(context)
                ChargingAlarmScheduler.cancelCheckpoints(context)
                // Keep widget updating while on battery
                ChargingAlarmScheduler.scheduleWidgetPeriodicRefresh(context, 15 * 60_000L)
            } catch (e: Exception) {
                Log.e("PowerConnectionReceiver", "Error during handleDisconnected", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
