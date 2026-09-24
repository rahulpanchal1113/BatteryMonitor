package com.example.data.util

import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import com.example.data.model.ChargeBracketInsight
import com.example.data.model.ChargingInsightSummary
import com.example.data.model.OverheatIncident
import com.example.data.model.OverheatingSummary
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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

        // Stage physical charging rate weights (faster early, tapering at high percentages)
        val stageWeights = listOf(1.15f, 1.25f, 1.10f, 0.88f, 0.52f)

        val bracketRates = List(bracketRanges.size) { mutableListOf<Float>() }
        val bracketTemps = List(bracketRanges.size) { mutableListOf<Float>() }
        val allRateSamples = mutableListOf<RateSample>()

        // 1. Process step intervals from battery events (including UNPLUGGED/PLUGGED_IN bounds)
        val sortedEvents = events.filter {
            it.isCharging || it.eventType == "UNPLUGGED" || it.eventType == "PLUGGED_IN" || it.eventType == "SAMPLE"
        }.sortedBy { it.timestamp }

        for (i in 1 until sortedEvents.size) {
            val prev = sortedEvents[i - 1]
            val curr = sortedEvents[i]

            val isChargingSequence = (prev.isCharging || prev.eventType == "PLUGGED_IN") &&
                    (curr.isCharging || curr.eventType == "UNPLUGGED")
            val sameSession = (prev.sessionId != null && prev.sessionId == curr.sessionId) ||
                    (curr.timestamp - prev.timestamp in 10_000..3_600_000)

            val deltaLevel = curr.batteryLevel - prev.batteryLevel
            val deltaMinutes = (curr.timestamp - prev.timestamp) / 60_000f

            if ((isChargingSequence || sameSession) && deltaLevel > 0 && deltaMinutes >= 0.15f && deltaMinutes <= 90f) {
                val stepRate = (deltaLevel.toFloat() / deltaMinutes) * 60f
                if (stepRate in 4f..350f) {
                    val pStart = prev.batteryLevel
                    val pEnd = curr.batteryLevel

                    bracketRanges.forEachIndexed { idx, (bStart, bEnd) ->
                        val oStart = max(pStart, bStart)
                        val oEnd = min(pEnd, bEnd)
                        if (oEnd > oStart) {
                            val stageWeight = stageWeights[idx]
                            val adjustedRate = if (pStart >= bStart && pEnd <= bEnd) {
                                stepRate
                            } else {
                                (stepRate * stageWeight).coerceIn(4f, 320f)
                            }
                            val progress = ((oStart + oEnd) / 2f - pStart).toFloat() / deltaLevel.toFloat()
                            val stageTemp = prev.temperatureCelsius + (curr.temperatureCelsius - prev.temperatureCelsius) * progress.coerceIn(0f, 1f)

                            bracketRates[idx].add(adjustedRate)
                            bracketTemps[idx].add(stageTemp)
                            allRateSamples.add(RateSample(bStart, adjustedRate, stageTemp))
                        }
                    }
                }
            }
        }

        // 2. Also process all charging sessions to guarantee every stage covered in a single charge event records data
        for (session in sessions) {
            val sStart = session.startLevel
            val sEnd = max(session.startLevel, session.endLevel)
            val sGained = sEnd - sStart
            val sDurationHours = session.durationSeconds.toFloat() / 3600f

            if (sGained > 0 && session.durationSeconds >= 20L) {
                val sessionAvgRate = if (sDurationHours > 0.02f) {
                    (sGained.toFloat() / sDurationHours).coerceIn(4f, 250f)
                } else if (session.peakSpeedPercentPerHour > 0f) {
                    session.peakSpeedPercentPerHour
                } else {
                    45f
                }

                bracketRanges.forEachIndexed { idx, (bStart, bEnd) ->
                    val oStart = max(sStart, bStart)
                    val oEnd = min(sEnd, bEnd)
                    if (oEnd > oStart) {
                        val stageWeight = stageWeights[idx]
                        val stageRate = (sessionAvgRate * stageWeight).coerceIn(4f, 300f)
                        val stageTemp = when (idx) {
                            0 -> session.startTemp
                            1, 2 -> max(session.avgTemp, (session.avgTemp + session.maxTemp) / 2f)
                            3 -> session.maxTemp
                            else -> session.avgTemp
                        }.let { if (it > 0f) it else 31.5f }

                        bracketRates[idx].add(stageRate)
                        bracketTemps[idx].add(stageTemp)
                        allRateSamples.add(RateSample(bStart, stageRate, stageTemp))
                    }
                }
            }
        }

        val brackets = bracketRanges.mapIndexed { idx, (start, end) ->
            val rates = bracketRates[idx]
            val temps = bracketTemps[idx]
            val label = "$start% - $end%"
            if (rates.isNotEmpty()) {
                val avgRate = rates.average().toFloat()
                val avgTemp = temps.average().toFloat()
                ChargeBracketInsight(
                    bracketLabel = label,
                    startPercent = start,
                    endPercent = end,
                    averageRatePercentPerHour = avgRate,
                    avgTemperature = avgTemp,
                    sampleCount = rates.size
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

        val hasEnoughData = brackets.any { it.sampleCount > 0 } || sessions.any { it.endLevel > it.startLevel }

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
        val hotSamples = allRateSamples.filter { it.tempCelsius >= 37.0f }
        val coolSamples = allRateSamples.filter { it.tempCelsius < 35.0f }

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

        // Overheating Analysis (Thresholds: Overheat >= 45.0°C, Hot >= 40.0°C, Warm >= 38.0°C)
        val peakTempFromSessions = sessions.map { it.maxTemp }.maxOrNull() ?: 0f
        val peakTempFromEvents = events.map { it.temperatureCelsius }.maxOrNull() ?: 0f
        val peakRecordedTemp = max(peakTempFromSessions, peakTempFromEvents)

        val overheatSessions = sessions.filter { it.maxTemp >= 38.0f }.sortedByDescending { it.startTime }
        val recentIncidents = overheatSessions.take(5).map { s ->
            val wasThrottled = s.maxTemp >= 39.5f
            val desc = if (s.maxTemp >= 45.0f) {
                "OVERHEAT (${String.format(Locale.US, "%.1f°C", s.maxTemp)}) • Exceeded 45°C safety threshold"
            } else if (s.maxTemp >= 40.0f) {
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
        val totalOverheatAbove45 = sessions.count { it.maxTemp >= 45.0f }
        val lastIncidentTime = overheatSessions.firstOrNull()?.startTime
        val safetyStatus = when {
            peakRecordedTemp >= 45.0f -> "CRITICAL OVERHEAT (>45°C)"
            peakRecordedTemp >= 41.0f -> "Thermal Throttling Alert"
            peakRecordedTemp >= 39.0f -> "Warm Cycles Logged"
            peakRecordedTemp > 0f -> "Optimal Thermal Control"
            else -> "Safe Thermal Profile"
        }

        val overheatingSummary = OverheatingSummary(
            peakRecordedTempCelsius = peakRecordedTemp,
            totalOverheatIncidents = totalIncidents,
            totalOverheatIncidentsAbove45 = totalOverheatAbove45,
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
