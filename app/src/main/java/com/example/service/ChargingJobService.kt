package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.BatteryApplication
import com.example.receiver.ChargingAlarmScheduler
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChargingJobService : JobService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = applicationContext as? BatteryApplication ?: return false

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:JobStartWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        serviceScope.launch {
            try {
                // Ensure repository records power connection and starts charging session
                app.repository.onPowerConnected()
                BatteryWidgetProvider.updateAllWidgets(applicationContext)

                // Schedule recurring checkpoint alarms for background sampling
                ChargingAlarmScheduler.scheduleNextCheckpoint(applicationContext, 20_000L)

                // Attempt to launch foreground monitoring service if allowable
                val serviceIntent = Intent(applicationContext, BatteryMonitorService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(serviceIntent)
                    } else {
                        applicationContext.startService(serviceIntent)
                    }
                } catch (_: Exception) {
                    // Handled gracefully when background execution constraints apply
                }
            } catch (e: Exception) {
                Log.e("ChargingJobService", "Error during onStartJob", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
            }

            // Periodic sampling loop while job remains active
            while (isActive) {
                delay(30_000L)
                try {
                    val status = app.repository.queryCurrentBatteryStatus()
                    if (status.isCharging) {
                        app.repository.logBatterySample()
                        BatteryWidgetProvider.updateAllWidgets(applicationContext)
                    } else {
                        break
                    }
                } catch (_: Exception) {}
            }
        }
        // Return true to indicate long-running job monitoring active charging
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Invoked by Android OS when power is disconnected or charging constraint ends
        val app = applicationContext as? BatteryApplication

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryApp:JobStopWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        serviceScope.launch {
            try {
                app?.repository?.onPowerDisconnected()
                BatteryWidgetProvider.updateAllWidgets(applicationContext)
            } finally {
                ChargingAlarmScheduler.cancelCheckpoints(applicationContext)
                ChargingAlarmScheduler.scheduleWidgetPeriodicRefresh(applicationContext, 15 * 60_000L)

                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
            }
        }
        serviceJob.cancel()
        // Reschedule to continuously monitor subsequent charging events
        return true
    }

    companion object {
        const val CHARGING_JOB_ID = 5001

        fun scheduleChargingJob(context: Context) {
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val component = ComponentName(context, ChargingJobService::class.java)
                val builder = JobInfo.Builder(CHARGING_JOB_ID, component)
                    .setRequiresCharging(true)
                    .setPersisted(true) // Ensures registration persists across device restarts

                jobScheduler.schedule(builder.build())
            } catch (e: Exception) {
                Log.w("ChargingJobService", "Failed to schedule persisted charging job", e)
            }
        }
    }
}
