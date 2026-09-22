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
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_WIDGET,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.ACTION_BATTERY_WIDGET_UPDATE"
        private const val PREFS_NAME = "widget_battery_prefs"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, BatteryWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient widget update exceptions
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
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

            val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                    statusInt == BatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val plugSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
                else -> if (isCharging) "Charging" else "On Battery"
            }

            // Battery temperature reading
            val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
            val tempC = tempRaw / 10f

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                var pluggedSince = prefs.getLong("plugged_since", 0L)
                var startLevel = prefs.getInt("start_level", -1)

                if (pluggedSince <= 0L || startLevel < 0) {
                    pluggedSince = now
                    startLevel = level
                    prefs.edit()
                        .putBoolean("is_plugged", true)
                        .putLong("plugged_since", pluggedSince)
                        .putInt("start_level", startLevel)
                        .putString("plug_type", plugSource)
                        .apply()
                }

                val elapsedMinutes = max(0, ((now - pluggedSince) / (60 * 1000L)).toInt())
                val hours = elapsedMinutes / 60
                val minutes = elapsedMinutes % 60
                val durationText = if (hours > 0) "${hours}h ${minutes}m" else if (minutes > 0) "${minutes}m" else "<1m"

                val gain = level - startLevel
                val gainSignStr = if (gain >= 0) "+$gain%" else "$gain%"

                val headline = if (gain > 0) {
                    "+$gain% gained since start"
                } else if (gain == 0) {
                    "Started at $startLevel% • Charging"
                } else {
                    "$gain% change since start"
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
                val lastDurationSec = prefs.getLong("last_duration_seconds", 0L)
                val lastStart = prefs.getInt("last_start_level", -1)
                val lastEnd = prefs.getInt("last_end_level", -1)

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
                    val gained = lastEnd - lastStart
                    val gainedStr = if (gained >= 0) "+$gained%" else "$gained%"

                    views.setTextViewText(R.id.widget_headline, "Last session: $gainedStr ($lastStart% → $lastEnd%)")
                    views.setTextViewText(R.id.widget_subheadline, "Discharging on battery power")

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
