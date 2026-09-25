package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discharging_sessions",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["dateKey"]),
        Index(value = ["isCompleted"])
    ]
)
data class DischargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startLevel: Int,
    val endLevel: Int,
    val startTemp: Float,
    val maxTemp: Float,
    val avgTemp: Float,
    val durationSeconds: Long = 0,
    val isCompleted: Boolean = false,
    val dateKey: String, // "yyyy-MM-dd"
    val drainSpeedPercentPerHour: Float = 0f,
    val screenOnSeconds: Long = 0L
) {
    val isDisplayable: Boolean
        get() {
            if (!isCompleted) return true
            val percentDrained = kotlin.math.max(0, startLevel - endLevel)
            return durationSeconds >= 120L || percentDrained >= 1
        }
}
