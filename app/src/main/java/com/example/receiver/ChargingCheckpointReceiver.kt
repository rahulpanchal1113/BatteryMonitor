package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.BatteryApplication
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChargingCheckpointReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BatteryApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val status = app.repository.queryCurrentBatteryStatus()
                if (status.isCharging) {
                    app.repository.logBatterySample()
                    BatteryWidgetProvider.updateAllWidgets(context)
                    // Schedule next checkpoint in 60s
                    ChargingAlarmScheduler.scheduleNextCheckpoint(context, 60_000L)
                } else {
                    // Stopped charging; ensure disconnection is logged and cancel alarms
                    app.repository.onPowerDisconnected()
                    BatteryWidgetProvider.updateAllWidgets(context)
                    ChargingAlarmScheduler.cancelCheckpoints(context)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
