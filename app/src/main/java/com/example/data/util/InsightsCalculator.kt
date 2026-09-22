package com.example.data.util

import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import com.example.data.model.ChargeBracketInsight
import com.example.data.model.ChargingInsightSummary
import com.example.data.model.OverheatIncident
import com.example.data.model.OverheatingSummary
import java.util.Locale
import kotlin.math.max

object InsightsCalculator {

    data class RateSample(
        val level: Int,
        val ratePerHour: Float,
        val tempCelsius: Float
    )

    fun calculateInsights(
        events: List<BatteryEventEntity>,
        sessions: List<ChargingSessionEntity>
    ): ChargingInsightSummary {
        val bracketRanges = listOf(
            0 to 20,
            20 to 40,
            40 to 60,
            60 to 80,
            80 to 100
        )

        val rateSamples = mutableListOf<RateSample>()

        // Group charging events by session or consecutive timestamps
        val chargingEvents = events.filter { it.isCharging }.sortedBy { it.timestamp }
        for (i in 1 until chargingEvents.size) {
            val prev = chargingEvents[i - 1]
            val curr = chargingEvents[i]

            // Ensure they belong to same session or close in time (less than 30 mins apart)
            val sameSession = (prev.sessionId != null && prev.sessionId == curr.sessionId) ||
                    (curr.timestamp - prev.timestamp in 15_000..1_800_000)

            val deltaLevel = curr.batteryLevel - prev.batteryLevel
            val deltaMinutes = (curr.timestamp - prev.timestamp) / 60_000f

            if (sameSession && deltaLevel > 0 && deltaMinutes >= 0.2f && deltaMinutes <= 60f) {
                val ratePerHour = (deltaLevel.toFloat() / deltaMinutes) * 60f
                // Cap rate to reasonable physical limits (10% to 300%/hr)
                if (ratePerHour in 5f..350f) {
                    val avgTemp = (prev.temperatureCelsius + curr.temperatureCelsius) / 2f
                    rateSamples.add(RateSample(prev.batteryLevel, ratePerHour, avgTemp))
                }
            }
        }

        val hasEnoughData = rateSamples.size >= 3 || (sessions.isNotEmpty() && chargingEvents.size >= 2)

        val brackets = bracketRanges.map { (start, end) ->
            val samplesInBracket = rateSamples.filter { it.level in start until end }
            val label = "$start% - $end%"
            if (samplesInBracket.isNotEmpty()) {
                val avgRate = samplesInBracket.map { it.ratePerHour }.average().toFloat()
                val avgTemp = samplesInBracket.map { it.tempCelsius }.average().toFloat()
                ChargeBracketInsight(
                    bracketLabel = label,
                    startPercent = start,
                    endPercent = end,
                    averageRatePercentPerHour = avgRate,
                    avgTemperature = avgTemp,
                    sampleCount = samplesInBracket.size
                )
            } else {
                ChargeBracketInsight(
                    bracketLabel = label,
                    startPercent = start,
                    endPercent = end,
                    averageRatePercentPerHour = 0f,
                    avgTemperature = 0f,
                    sampleCount = 0
                )
            }
        }

        // Identify peak and slowest brackets dynamically from real collected samples
        val measuredBrackets = brackets.filter { it.sampleCount > 0 && it.averageRatePercentPerHour > 0f }
        val peakBracket = if (hasEnoughData && measuredBrackets.isNotEmpty()) {
            measuredBrackets.maxByOrNull { it.averageRatePercentPerHour }
        } else null

        val slowestBracket = if (hasEnoughData && measuredBrackets.size >= 2) {
            measuredBrackets.minByOrNull { it.averageRatePercentPerHour }
        } else null

        // Identify drop off point (where charging speed drops notably, usually above 60% or 80%)
        var dropOffLevel: Int? = null
        if (peakBracket != null && peakBracket.averageRatePercentPerHour > 0f) {
            val peakIndex = brackets.indexOf(peakBracket)
            for (i in (peakIndex + 1) until brackets.size) {
                val b = brackets[i]
                if (b.sampleCount > 0 && b.averageRatePercentPerHour < peakBracket.averageRatePercentPerHour * 0.70f) {
                    dropOffLevel = b.startPercent
                    break
                }
            }
        }

        // Thermal correlation analysis
        val hotSamples = rateSamples.filter { it.tempCelsius >= 37.0f }
        val coolSamples = rateSamples.filter { it.tempCelsius < 35.0f }

        val thermalNote = if (!hasEnoughData) {
            "Collecting battery data… Insights, fastest charging zone, and thermal characteristics will automatically show up after a few charging cycles."
        } else if (hotSamples.size >= 2 && coolSamples.size >= 2) {
            val avgHotRate = hotSamples.map { it.ratePerHour }.average().toFloat()
            val avgCoolRate = coolSamples.map { it.ratePerHour }.average().toFloat()
            val diffPct = ((avgCoolRate - avgHotRate) / max(1f, avgCoolRate) * 100f).toInt()
            if (diffPct > 10) {
                "Charging slows by ~$diffPct% when battery temperature rises above 37°C due to thermal throttling protection."
            } else {
                "Charging speeds remain stable across temperatures up to 37.5°C."
            }
        } else if (peakBracket != null) {
            "Phone achieves maximum charging speed between ${peakBracket.startPercent}% and ${peakBracket.endPercent}%.${if (dropOffLevel != null) " Speeds begin tapering after $dropOffLevel% to protect battery cell longevity." else ""}"
        } else {
            "Continue plugging in your adapter to refine charge velocity and temperature correlation."
        }

        val totalHours = sessions.sumOf { it.durationSeconds }.toFloat() / 3600f
        val avgTempOverall = if (sessions.isNotEmpty()) {
            sessions.map { it.avgTemp }.filter { it > 0f }.ifEmpty { listOf(0f) }.average().toFloat()
        } else {
            0f
        }

        // Overheating Analysis (Thresholds: Warm >= 37.5°C, Hot/Overheating >= 39.5°C)
        val peakTempFromSessions = sessions.map { it.maxTemp }.maxOrNull() ?: 0f
        val peakTempFromEvents = events.map { it.temperatureCelsius }.maxOrNull() ?: 0f
        val peakRecordedTemp = max(peakTempFromSessions, peakTempFromEvents)

        val overheatSessions = sessions.filter { it.maxTemp >= 38.0f }.sortedByDescending { it.startTime }
        val recentIncidents = overheatSessions.take(5).map { s ->
            val wasThrottled = s.maxTemp >= 39.5f
            val desc = if (s.maxTemp >= 40.0f) {
                "High thermal spike (${String.format(Locale.US, "%.1f°C", s.maxTemp)}) • Aggressive throttling"
            } else if (s.maxTemp >= 38.5f) {
                "Elevated temperature (${String.format(Locale.US, "%.1f°C", s.maxTemp)}) • Moderate charging taper"
            } else {
                "Warm charging cycle (${String.format(Locale.US, "%.1f°C", s.maxTemp)}) • Normal heat dissipation"
            }
            OverheatIncident(
                timestamp = s.startTime,
                peakTempCelsius = s.maxTemp,
                durationSeconds = s.durationSeconds,
                sessionPlugType = s.plugType,
                wasThrottled = wasThrottled,
                description = desc
            )
        }

        val totalIncidents = overheatSessions.size
        val lastIncidentTime = overheatSessions.firstOrNull()?.startTime
        val safetyStatus = when {
            peakRecordedTemp >= 41.0f -> "Thermal Throttling Alert"
            peakRecordedTemp >= 39.0f -> "Warm Cycles Logged"
            peakRecordedTemp > 0f -> "Optimal Thermal Control"
            else -> "Safe Thermal Profile"
        }

        val overheatingSummary = OverheatingSummary(
            peakRecordedTempCelsius = peakRecordedTemp,
            totalOverheatIncidents = totalIncidents,
            lastIncidentTimestamp = lastIncidentTime,
            thermalSafetyStatus = safetyStatus,
            recentIncidents = recentIncidents
        )

        return ChargingInsightSummary(
            peakBracket = peakBracket,
            slowestBracket = slowestBracket,
            dropOffPointPercent = dropOffLevel,
            thermalImpactNote = thermalNote,
            brackets = brackets,
            totalRecordedSessions = sessions.size,
            totalChargingHours = String.format(Locale.US, "%.1f", totalHours).toFloat(),
            avgBatteryTempOverall = avgTempOverall,
            hasEnoughData = hasEnoughData,
            overheatingSummary = overheatingSummary
        )
    }
}
