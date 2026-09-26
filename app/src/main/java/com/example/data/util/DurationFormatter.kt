package com.example.data.util

import kotlin.math.max

object DurationFormatter {

    /**
     * Formats duration into user-friendly "4h 12mins", "45mins", or "30s".
     * Whenever there are more than 60 mins, shows it in "Xh Ymins" (e.g. "4h 12mins").
     */
    fun formatHourMinutes(seconds: Long): String {
        val totalSecs = max(0L, seconds)
        val totalMins = totalSecs / 60L
        val hours = totalMins / 60L
        val remainingMins = totalMins % 60L

        return when {
            hours > 0L && remainingMins > 0L -> "${hours}h ${remainingMins}mins"
            hours > 0L -> "${hours}h"
            totalMins > 0L -> "${totalMins}mins"
            else -> "${totalSecs}s"
        }
    }

    /**
     * Formats duration into short compact format "4h 12m", "45m", "30s".
     */
    fun formatHourMinutesShort(seconds: Long): String {
        val totalSecs = max(0L, seconds)
        val totalMins = totalSecs / 60L
        val hours = totalMins / 60L
        val remainingMins = totalMins % 60L

        return when {
            hours > 0L && remainingMins > 0L -> "${hours}h ${remainingMins}m"
            hours > 0L -> "${hours}h"
            totalMins > 0L -> "${totalMins}m"
            else -> "${totalSecs}s"
        }
    }

    /**
     * Formats milliseconds into duration string (e.g. "4h 12mins", "14m 20s", "45s").
     */
    fun formatMillisDuration(millis: Long): String {
        val totalSecs = max(0L, millis / 1000L)
        val hours = totalSecs / 3600L
        val minutes = (totalSecs % 3600L) / 60L
        val seconds = totalSecs % 60L

        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}mins"
            hours > 0L -> "${hours}h"
            minutes > 0L && seconds > 0L -> "${minutes}m ${seconds}s"
            minutes > 0L -> "${minutes}mins"
            else -> "${max(1L, seconds)}s"
        }
    }
}
