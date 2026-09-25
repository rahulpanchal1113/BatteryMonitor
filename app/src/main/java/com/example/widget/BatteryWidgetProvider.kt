package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.BatteryDatabase
import com.example.data.local.ChargingSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_POWER_DISCONNECTED -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_plugged", false).apply()
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? com.example.BatteryApplication
                        app?.repository?.onPowerDisconnected()
                        updateAllWidgetsDirect(context)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (_: Exception) {}
                    }
                }
            }
            ACTION_UPDATE_WIDGET,
            ACTION_WIDGET_REFRESH,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAllWidgetsDirect(context)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.ACTION_BATTERY_WIDGET_UPDATE"
        const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"
        private const val PREFS_NAME = "widget_battery_prefs"

        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                updateAllWidgetsDirect(context)
            }
        }

        private suspend fun updateAllWidgetsDirect(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, BatteryWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    val dao = try {
                        BatteryDatabase.getDatabase(context).batteryDao()
                    } catch (e: Exception) {
                        null
                    }
                    val latestCompleted = try {
                        dao?.getLatestValidCompletedSession() ?: dao?.getLatestSession()
                    } catch (e: Exception) {
                        null
                    }
                    val activeSession = try {
                        dao?.getActiveSession()
                    } catch (e: Exception) {
                        null
                    }
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, latestCompleted, activeSession)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient widget update exceptions
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            latestCompletedSession: ChargingSessionEntity? = null,
            activeSession: ChargingSessionEntity? = null
        ) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, filter)

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val directCapacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

            val level = if (directCapacity in 0..100) {
                directCapacity
            } else {
                batteryIntent?.let {
                    val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val rawScale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (rawLevel >= 0 && rawScale > 0) (rawLevel * 100) / rawScale else 75
                } ?: 75
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pluggedInPref = prefs.getBoolean("is_plugged", false)

            val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val isPluggedHardware = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                    plugged > 0
            val isChargingStatus = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                    statusInt == BatteryManager.BATTERY_STATUS_FULL
            val bmCharging = bm?.isCharging ?: false
            val bmStatus = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
            } catch (_: Exception) { -1 }
            val bmChargingStatus = bmStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                    bmStatus == BatteryManager.BATTERY_STATUS_FULL

            // Reliable physical charging state: If hardware explicitly reports unplugged / discharging,
            // never let stale preferences override reality
            val isExplicitlyUnplugged = (plugged == 0 || (batteryIntent != null && !isPluggedHardware)) && !bmCharging && !bmChargingStatus
            val isHardwareCharging = isPluggedHardware || bmCharging || isChargingStatus || bmChargingStatus

            val isCharging = if (isExplicitlyUnplugged) {
                false
            } else {
                isHardwareCharging || (pluggedInPref && activeSession != null)
            }

            if (!isCharging) {
                prefs.edit().putBoolean("is_plugged", false).apply()
                if (activeSession != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val app = context.applicationContext as? com.example.BatteryApplication
                            app?.repository?.onPowerDisconnected()
                        } catch (_: Exception) {}
                    }
                }
            }

            val plugSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
                else -> {
                    if (activeSession != null && activeSession.plugType.isNotBlank() && !activeSession.plugType.equals("Battery", ignoreCase = true)) {
                        activeSession.plugType
                    } else {
                        prefs.getString("plug_type", null) ?: if (isCharging) "Charging" else "On Battery"
                    }
                }
            }

            // Battery temperature reading
            val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
            val tempC = tempRaw / 10f

            val useFahrenheit = prefs.getBoolean("use_fahrenheit", false)
            val tempDisplay = if (useFahrenheit) {
                String.format(Locale.US, "%.1f°F", (tempC * 9f / 5f) + 32f)
            } else {
                String.format(Locale.US, "%.1f°C", tempC)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_battery_layout)

            // Header elements
            views.setTextViewText(R.id.widget_temp, "🌡️ $tempDisplay")

            val now = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val stampFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val updateTimeStr = stampFormat.format(Date(now))

            if (isCharging) {
                // Charging / Plugged in mode
                var pluggedSince = if (activeSession != null && activeSession.startTime > 0L) {
                    activeSession.startTime
                } else {
                    prefs.getLong("plugged_since", 0L)
                }

                var startLevel = if (activeSession != null && activeSession.startLevel in 0..100) {
                    activeSession.startLevel
                } else {
                    prefs.getInt("start_level", -1)
                }

                if (pluggedSince <= 0L || startLevel < 0) {
                    pluggedSince = now
                    startLevel = level
                }

                prefs.edit()
                    .putBoolean("is_plugged", true)
                    .putLong("plugged_since", pluggedSince)
                    .putInt("start_level", startLevel)
                    .putString("plug_type", plugSource)
                    .apply()

                val elapsedMinutes = max(0, ((now - pluggedSince) / (60 * 1000L)).toInt())
                val hours = elapsedMinutes / 60
                val minutes = elapsedMinutes % 60
                val durationText = if (hours > 0) "${hours}h ${minutes}m" else if (minutes > 0) "${minutes}m" else "<1m"

                val gain = max(0, level - startLevel)
                val gainSignStr = "+$gain%"

                val headline = if (gain > 0) {
                    "+$gain% gained since start"
                } else {
                    "Started at $startLevel% • Charging"
                }
                val subheadline = "$plugSource • Battery Healthy"
                val sinceText = "Plugged in at ${timeFormat.format(Date(pluggedSince))}"

                // Texts
                views.setTextViewText(R.id.widget_percent, "$level%")
                views.setTextColor(R.id.widget_percent, Color.parseColor("#22C55E"))

                views.setTextViewText(R.id.widget_status_pill, "⚡ CHARGING")
                views.setInt(R.id.widget_status_pill, "setBackgroundResource", R.drawable.widget_pill_charging)
                views.setTextColor(R.id.widget_status_pill, Color.parseColor("#4ADE80"))

                views.setTextViewText(R.id.widget_headline, headline)
                views.setTextViewText(R.id.widget_subheadline, subheadline)

                views.setProgressBar(R.id.widget_progress, 100, level, false)

                // 3-Metric Boxes
                views.setTextViewText(R.id.widget_metric_start_label, "STARTED")
                views.setTextViewText(R.id.widget_metric_start_val, "$startLevel%")

                views.setTextViewText(R.id.widget_metric_current_label, "CURRENT")
                views.setTextViewText(R.id.widget_metric_current_val, "$level% ($gainSignStr)")
                views.setTextColor(R.id.widget_metric_current_val, Color.parseColor("#22C55E"))

                views.setTextViewText(R.id.widget_metric_duration_label, "PLUGGED FOR")
                views.setTextViewText(R.id.widget_metric_duration_val, durationText)

                views.setTextViewText(R.id.widget_since_time, sinceText)
            } else {
                // Discharging / On Battery mode
                // Prefer latest completed session directly from database, or session that just ended
                val effectiveSession = latestCompletedSession ?: activeSession
                val lastStart: Int
                val lastEnd: Int
                val lastDurationSec: Long
                val lastPlugType: String

                if (effectiveSession != null) {
                    lastStart = effectiveSession.startLevel
                    lastEnd = max(effectiveSession.startLevel, if (effectiveSession == activeSession) level else effectiveSession.endLevel)
                    lastDurationSec = if (effectiveSession.durationSeconds > 0) {
                        effectiveSession.durationSeconds
                    } else {
                        max(1L, ((effectiveSession.endTime ?: now) - effectiveSession.startTime) / 1000L)
                    }
                    lastPlugType = if (effectiveSession.plugType.isNotBlank() && !effectiveSession.plugType.equals("Battery", ignoreCase = true)) {
                        effectiveSession.plugType
                    } else {
                        prefs.getString("last_plug_type", null) ?: "Charger"
                    }

                    // Keep SharedPreferences aligned with the authoritative database record
                    prefs.edit()
                        .putBoolean("is_plugged", false)
                        .putLong("last_duration_seconds", lastDurationSec)
                        .putInt("last_start_level", lastStart)
                        .putInt("last_end_level", lastEnd)
                        .putString("last_plug_type", lastPlugType)
                        .putLong("last_end_time", effectiveSession.endTime ?: now)
                        .apply()
                } else {
                    lastDurationSec = prefs.getLong("last_duration_seconds", 0L)
                    lastStart = prefs.getInt("last_start_level", -1)
                    lastEnd = prefs.getInt("last_end_level", -1)
                    lastPlugType = prefs.getString("last_plug_type", null) ?: "Charger"
                    prefs.edit().putBoolean("is_plugged", false).apply()
                }

                views.setTextViewText(R.id.widget_percent, "$level%")
                views.setTextColor(R.id.widget_percent, Color.parseColor("#38BDF8"))

                views.setTextViewText(R.id.widget_status_pill, "🔋 ON BATTERY")
                views.setInt(R.id.widget_status_pill, "setBackgroundResource", R.drawable.widget_pill_discharging)
                views.setTextColor(R.id.widget_status_pill, Color.parseColor("#94A3B8"))

                views.setProgressBar(R.id.widget_progress, 100, level, false)

                if (lastDurationSec > 0 && lastStart >= 0 && lastEnd >= 0) {
                    val lastMinutes = (lastDurationSec / 60).toInt()
                    val lastHours = lastMinutes / 60
                    val lastRemMin = lastMinutes % 60
                    val durStr = if (lastHours > 0) "${lastHours}h ${lastRemMin}m" else if (lastMinutes > 0) "${lastMinutes}m" else "<1m"
                    val safeEnd = max(lastStart, lastEnd)
                    val gained = max(0, safeEnd - lastStart)
                    val gainedStr = "+$gained%"

                    views.setTextViewText(R.id.widget_headline, "Last session: $gainedStr ($lastStart% → $safeEnd%)")
                    views.setTextViewText(R.id.widget_subheadline, "Discharging on battery power • $lastPlugType")

                    views.setTextViewText(R.id.widget_metric_start_label, "LAST START")
                    views.setTextViewText(R.id.widget_metric_start_val, "$lastStart%")

                    views.setTextViewText(R.id.widget_metric_current_label, "CURRENT")
                    views.setTextViewText(R.id.widget_metric_current_val, "$level%")
                    views.setTextColor(R.id.widget_metric_current_val, Color.parseColor("#38BDF8"))

                    views.setTextViewText(R.id.widget_metric_duration_label, "LAST DURATION")
                    views.setTextViewText(R.id.widget_metric_duration_val, durStr)
                } else {
                    views.setTextViewText(R.id.widget_headline, "Discharging on battery power")
                    views.setTextViewText(R.id.widget_subheadline, "Normal usage • Good Health")

                    views.setTextViewText(R.id.widget_metric_start_label, "STATUS")
                    views.setTextViewText(R.id.widget_metric_start_val, "Unplugged")

                    views.setTextViewText(R.id.widget_metric_current_label, "CURRENT")
                    views.setTextViewText(R.id.widget_metric_current_val, "$level%")
                    views.setTextColor(R.id.widget_metric_current_val, Color.parseColor("#38BDF8"))

                    views.setTextViewText(R.id.widget_metric_duration_label, "TEMP")
                    views.setTextViewText(R.id.widget_metric_duration_val, tempDisplay)
                }

                views.setTextViewText(R.id.widget_since_time, "Tap to open Battery Monitor")
            }

            views.setTextViewText(R.id.widget_updated_time, "Updated $updateTimeStr")

            // Tap on widget opens MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
