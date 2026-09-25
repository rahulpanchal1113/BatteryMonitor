package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.BatteryApplication
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChargingCheckpointReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BatteryApplication ?: return
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:CheckpointWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(10_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val action = intent.action
                if (action == ChargingAlarmScheduler.ACTION_WIDGET_REFRESH) {
                    app.repository.updateLiveStatus()
                    app.repository.logBatterySample()
                    BatteryWidgetProvider.updateAllWidgets(context)
                    ChargingAlarmScheduler.scheduleWidgetPeriodicRefresh(context, 15 * 60_000L)
                    return@launch
                }

                // Action is ACTION_CHARGING_CHECKPOINT
                val status = app.repository.queryCurrentBatteryStatus()
                if (status.isCharging) {
                    app.repository.logBatterySample()
                    BatteryWidgetProvider.updateAllWidgets(context)
                    // Continue periodic sampling while charging every 30s
                    ChargingAlarmScheduler.scheduleNextCheckpoint(context, 30_000L)
                } else {
                    // Not charging: cancel checkpoint alarms and keep widget updated
                    ChargingAlarmScheduler.cancelCheckpoints(context)
                    ChargingAlarmScheduler.scheduleWidgetPeriodicRefresh(context, 15 * 60_000L)
                }
            } catch (e: Exception) {
                Log.e("ChargingCheckpointReceiver", "Error during checkpoint execution", e)
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
