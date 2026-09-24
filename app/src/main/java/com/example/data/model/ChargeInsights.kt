package com.example.data.model

data class ChargeBracketInsight(
    val bracketLabel: String,
    val startPercent: Int,
    val endPercent: Int,
    val averageRatePercentPerHour: Float,
    val avgTemperature: Float,
    val sampleCount: Int
) {
    val minutesPerOnePercent: Float
        get() = if (averageRatePercentPerHour > 0f) 60f / averageRatePercentPerHour else 0f

    val estimatedMinutesForBracket: Int
        get() {
            val delta = endPercent - startPercent
            return if (averageRatePercentPerHour > 0f) {
                ((delta.toFloat() / averageRatePercentPerHour) * 60f).toInt().coerceAtLeast(1)
            } else 0
        }

    val stageName: String
        get() = when (startPercent) {
            0 -> "Fast Boost"
            20 -> "Peak Velocity"
            40 -> "Rapid Cruise"
            60 -> "Balanced"
            else -> "Trickle Protection"
        }

    val stageDescription: String
        get() = when (startPercent) {
            0 -> "Maximum power intake"
            20 -> "Fastest charging phase"
            40 -> "High speed cruising"
            60 -> "Controller begins balancing"
            else -> "Trickle mode to prevent cell wear"
        }

    val isOverheated: Boolean
        get() = avgTemperature >= 45.0f
}

data class OverheatIncident(
    val timestamp: Long,
    val peakTempCelsius: Float,
    val durationSeconds: Long,
    val sessionPlugType: String,
    val wasThrottled: Boolean,
    val description: String
) {
    val isOverheatAbove45: Boolean
        get() = peakTempCelsius >= 45.0f
}

data class OverheatingSummary(
    val peakRecordedTempCelsius: Float = 0f,
    val totalOverheatIncidents: Int = 0,
    val totalOverheatIncidentsAbove45: Int = 0,
    val lastIncidentTimestamp: Long? = null,
    val thermalSafetyStatus: String = "Normal (<37°C)",
    val recentIncidents: List<OverheatIncident> = emptyList()
) {
    val hasCriticalOverheatAbove45: Boolean
        get() = peakRecordedTempCelsius >= 45.0f
}

data class ChargingInsightSummary(
    val peakBracket: ChargeBracketInsight?,
    val slowestBracket: ChargeBracketInsight? = null,
    val dropOffPointPercent: Int?,
    val thermalImpactNote: String,
    val brackets: List<ChargeBracketInsight>,
    val totalRecordedSessions: Int,
    val totalChargingHours: Float,
    val avgBatteryTempOverall: Float,
    val hasEnoughData: Boolean = false,
    val overheatingSummary: OverheatingSummary = OverheatingSummary()
)

