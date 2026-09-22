package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.BatteryApplication
import com.example.service.BatteryMonitorService
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? BatteryApplication ?: return

        when (action) {
            Intent.ACTION_POWER_CONNECTED,
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
            "android.hardware.usb.action.USB_DEVICE_ATTACHED",
            "android.app.action.ENTER_CAR_MODE" -> {
                handleConnected(context, app)
            }

            "android.hardware.usb.action.USB_STATE" -> {
                val isUsbConnected = intent.getBooleanExtra("connected", false)
                val status = app.repository.queryCurrentBatteryStatus()
                if (isUsbConnected || status.isCharging) {
                    handleConnected(context, app)
                } else {
                    handleDisconnected(context, app)
                }
            }

            Intent.ACTION_POWER_DISCONNECTED,
            "android.hardware.usb.action.USB_ACCESSORY_DETACHED",
            "android.hardware.usb.action.USB_DEVICE_DETACHED",
            "android.app.action.EXIT_CAR_MODE" -> {
                handleDisconnected(context, app)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                BatteryWidgetProvider.updateAllWidgets(context)
                val status = app.repository.liveBatteryStatus.value
                if (status.isCharging) {
                    ChargingAlarmScheduler.scheduleNextCheckpoint(context, 30_000L)
                }
            }
        }
    }

    private fun handleConnected(context: Context, app: BatteryApplication) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.onPowerConnected()
                BatteryWidgetProvider.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }

        // Start active foreground monitor service if allowable
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Ignored on background execution limits
        }

        // Ensure reliable periodic background sampling begins right away
        ChargingAlarmScheduler.scheduleNextCheckpoint(context, 20_000L)
    }

    private fun handleDisconnected(context: Context, app: BatteryApplication) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.onPowerDisconnected()
                BatteryWidgetProvider.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }

        // Stop active monitoring service and cancel recurring alarms
        val serviceIntent = Intent(context, BatteryMonitorService::class.java)
        try {
            context.stopService(serviceIntent)
        } catch (_: Exception) {}

        ChargingAlarmScheduler.cancelCheckpoints(context)
    }
}
