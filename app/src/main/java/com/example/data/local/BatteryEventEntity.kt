package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sessionId"]),
        Index(value = ["eventType"])
    ]
)
data class BatteryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String, // "PLUGGED_IN", "UNPLUGGED", "LEVEL_CHANGE", "SAMPLE"
    val batteryLevel: Int,
    val isCharging: Boolean,
    val plugType: String, // "AC", "USB", "WIRELESS", "BATTERY"
    val temperatureCelsius: Float,
    val voltageMilliVolts: Int,
    val batteryHealth: String,
    val sessionId: Long? = null
)
