package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charging_sessions",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["dateKey"]),
        Index(value = ["isCompleted"])
    ]
)
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startLevel: Int,
    val endLevel: Int,
    val plugType: String,
    val startTemp: Float,
    val maxTemp: Float,
    val avgTemp: Float,
    val durationSeconds: Long = 0,
    val peakSpeedPercentPerHour: Float = 0f,
    val isCompleted: Boolean = false,
    val dateKey: String // YYYY-MM-DD
) {
    val isDisplayable: Boolean
        get() {
            if (!isCompleted) return true
            val percentGained = kotlin.math.abs(endLevel - startLevel)
            return durationSeconds >= 25L || percentGained >= 1 || plugType.contains("USB", ignoreCase = true) || plugType.contains("Car", ignoreCase = true)
        }
}
