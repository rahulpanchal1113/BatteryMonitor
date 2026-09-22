package com.example.data.diagnostics

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BatteryApplication
import com.example.MainActivity
import com.example.R
import com.example.data.model.ConnectionDiagnosticIssue
import com.example.sensor.DeviceSteadinessDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

data class ConnectionTransitionRecord(
    val timestamp: Long,
    val isConnected: Boolean,
    var isDeviceSteady: Boolean
)

class ConnectionStabilityAnalyzer(
    private val context: Context,
    private val steadinessDetector: DeviceSteadinessDetector
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val recentTransitions = CopyOnWriteArrayList<ConnectionTransitionRecord>()

    private val _activeIssue = MutableStateFlow<ConnectionDiagnosticIssue?>(null)
    val activeIssue: StateFlow<ConnectionDiagnosticIssue?> = _activeIssue.asStateFlow()

    private val _filteredJitterCount = MutableStateFlow(0)
    val filteredJitterCount: StateFlow<Int> = _filteredJitterCount.asStateFlow()

    private var lastNotificationTime = 0L
    private var dismissedUntilTime = 0L

    fun onPowerTransition(isConnected: Boolean) {
        val now = System.currentTimeMillis()

        coroutineScope.launch {
            // Assess steadiness using accelerometer check (zero continuous battery drain)
            val isSteady = steadinessDetector.assessSteadiness(1200L)
            val record = ConnectionTransitionRecord(
                timestamp = now,
                isConnected = isConnected,
                isDeviceSteady = isSteady
            )
            recentTransitions.add(record)

            analyzeTransitions(now, isSteady)
        }
    }

    fun incrementFilteredJitter() {
        _filteredJitterCount.value += 1
    }

    private fun analyzeTransitions(now: Long, isCurrentSteady: Boolean) {
        // If device is currently held in hand or moving, immediately dismiss any alert
        if (!isCurrentSteady) {
            _activeIssue.value = null
            return
        }

        // If user recently dismissed the alert, do not re-trigger
        if (now < dismissedUntilTime) {
            _activeIssue.value = null
            return
        }

        // Prune events older than 45 seconds (focus strictly on rapid electrical jitter)
        val cutoff = now - 45_000L
        recentTransitions.removeAll { it.timestamp < cutoff }

        // Count disconnects in the last 45 seconds
        val disconnectEvents = recentTransitions.filter { !it.isConnected }
        val disconnectCount = disconnectEvents.size

        // Diagnostic alert should ONLY show up if:
        // 1. At least 4 rapid disconnects within 45s (indicative of true intermittent cable/port contact)
        // 2. The device is confirmed currently stationary (resting on a flat surface)
        // 3. EVERY disconnect occurred while the device was confirmed steady (not in hand)
        // 4. Absolutely ZERO motion was detected across the entire window
        val allDisconnectsWereSteady = disconnectEvents.isNotEmpty() && disconnectEvents.all { it.isDeviceSteady }
        val anyMotionDetectedInWindow = recentTransitions.any { !it.isDeviceSteady }

        if (disconnectCount >= 4 && isCurrentSteady && allDisconnectsWereSteady && !anyMotionDetectedInWindow) {
            val oldestTime = disconnectEvents.minOfOrNull { it.timestamp } ?: cutoff
            val windowSecs = max(5, ((now - oldestTime) / 1000L).toInt())

            val issue = ConnectionDiagnosticIssue(
                disconnectCount = disconnectCount,
                windowSeconds = windowSecs,
                isDeviceSteady = true,
                title = "Possible Cable, Port, or Adapter Issue",
                summary = "Device is resting steady, but charging disconnected $disconnectCount times in ${windowSecs}s.",
                details = "Your device is stationary and not in motion, yet charging is repeatedly disconnecting and reconnecting. This behavior strongly suggests an intermittent electrical connection, such as a loose charging port, accumulated pocket lint, a frayed charging cable, or an unstable power adapter.",
                recommendations = listOf(
                    "Inspect your phone's charging port for lint or debris, and clean gently with a non-conductive pick.",
                    "Verify if the charging cable fits securely without wobbling or loosening.",
                    "Try charging with a different USB cable to isolate whether the cable is frayed.",
                    "Test a different wall adapter or power outlet to ensure stable power delivery."
                )
            )

            _activeIssue.value = issue

            // Post system notification if at least 60 seconds have elapsed since last alert
            if (now - lastNotificationTime > 60_000L) {
                lastNotificationTime = now
                postHardwareAlertNotification(issue)
            }
        } else if (anyMotionDetectedInWindow || !isCurrentSteady) {
            // If device was moved or is held in hand, immediately clear alert
            _activeIssue.value = null
        }
    }

    fun dismissActiveIssue() {
        _activeIssue.value = null
        dismissedUntilTime = System.currentTimeMillis() + 30 * 60 * 1000L // Snooze for 30 minutes
        recentTransitions.clear()
    }

    private fun postHardwareAlertNotification(issue: ConnectionDiagnosticIssue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, BatteryApplication.CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ ${issue.title}")
            .setContentText(issue.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${issue.summary}\n\n${issue.details}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(2001, notification)
    }
}
