package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.local.BatteryDatabase
import com.example.data.repository.BatteryRepository
import com.example.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryApplication : Application() {

    val database: BatteryDatabase by lazy { BatteryDatabase.getDatabase(this) }
    val repository: BatteryRepository by lazy { BatteryRepository(database.batteryDao(), this) }

    private var lastObservedPercent: Int = -1
    private var lastObservedCharging: Boolean? = null

    private val dynamicBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return

            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val percent = if (rawLevel >= 0 && rawScale > 0) (rawLevel * 100) / rawScale else -1

                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                            plugged > 0
                    val isCharging = isPlugged ||
                            statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                            statusInt == BatteryManager.BATTERY_STATUS_FULL

                    if (percent != lastObservedPercent) {
                        lastObservedPercent = percent
                        BatteryWidgetProvider.updateAllWidgets(context)
                    }

                    // Detect charging transitions (e.g. connecting to car / Android Auto / USB dock)
                    val prevCharging = lastObservedCharging
                    lastObservedCharging = isCharging

                    if (prevCharging != null && prevCharging != isCharging) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            if (isCharging) {
                                repository.onPowerConnected()
                            } else {
                                repository.onPowerDisconnected()
                            }
                            BatteryWidgetProvider.updateAllWidgets(context)
                        }
                    }
                }

                Intent.ACTION_POWER_CONNECTED,
                "android.app.action.ENTER_CAR_MODE",
                "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
                "android.hardware.usb.action.USB_DEVICE_ATTACHED" -> {
                    lastObservedCharging = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repository.onPowerConnected()
                        BatteryWidgetProvider.updateAllWidgets(context)
                    }
                }

                Intent.ACTION_POWER_DISCONNECTED,
                "android.app.action.EXIT_CAR_MODE",
                "android.hardware.usb.action.USB_ACCESSORY_DETACHED",
                "android.hardware.usb.action.USB_DEVICE_DETACHED" -> {
                    lastObservedCharging = false
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repository.onPowerDisconnected()
                        BatteryWidgetProvider.updateAllWidgets(context)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Register dynamic battery listener so home screen widget updates in real time
        // and Android Auto / Car connections are reliably captured even in background
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction("android.app.action.ENTER_CAR_MODE")
            addAction("android.app.action.EXIT_CAR_MODE")
            addAction("android.hardware.usb.action.USB_ACCESSORY_ATTACHED")
            addAction("android.hardware.usb.action.USB_ACCESSORY_DETACHED")
            addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
            addAction("android.hardware.usb.action.USB_DEVICE_DETACHED")
        }
        registerReceiver(dynamicBatteryReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                getString(R.string.notification_alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_alerts_channel_desc)
                setShowBadge(true)
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "battery_monitor_channel"
        const val CHANNEL_ALERTS_ID = "battery_hardware_alerts_channel"
        lateinit var instance: BatteryApplication
            private set
    }
}
