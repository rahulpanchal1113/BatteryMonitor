package com.example.data.util

import android.content.Context
import android.os.BatteryManager
import com.example.data.local.ChargingSessionEntity
import com.example.data.local.DischargingSessionEntity
import com.example.data.model.BatteryHealthInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object BatteryHealthCalculator {

    private const val PREFS_NAME = "battery_health_prefs"
    private const val KEY_CACHED_DESIGN_CAPACITY = "cached_design_capacity_mah"
    private const val DEFAULT_CAPACITY_MAH = 5000

    /**
     * Resolves the factory rated design capacity (when the phone was brand new) in mAh.
     * Uses Android's internal PowerProfile via reflection, fallback to hardware profile.
     */
    fun getDesignCapacityMah(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getInt(KEY_CACHED_DESIGN_CAPACITY, 0)
        if (cached in 1800..15000) {
            return cached
        }

        var detectedCapacity = 0

        // 1. Try reading from com.android.internal.os.PowerProfile
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val getBatteryCapacityMethod = powerProfileClass.getMethod("getBatteryCapacity")
            val capacityVal = (getBatteryCapacityMethod.invoke(powerProfile) as? Double)?.toFloat()
            if (capacityVal != null && capacityVal >= 1800f && capacityVal <= 15000f) {
                detectedCapacity = capacityVal.roundToInt()
            }
        } catch (_: Exception) {}

        // 2. Try BatteryManager charge counter if full
        if (detectedCapacity <= 0) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
                if (chargeCounter > 500_000) {
                    val mah = (chargeCounter / 1000f).roundToInt()
                    if (mah in 1800..15000) {
                        detectedCapacity = mah
                    }
                }
            } catch (_: Exception) {}
        }

        val finalCapacity = if (detectedCapacity in 1800..15000) detectedCapacity else DEFAULT_CAPACITY_MAH
        prefs.edit().putInt(KEY_CACHED_DESIGN_CAPACITY, finalCapacity).apply()
        return finalCapacity
    }

    /**
     * Estimates battery health dynamically from empirical charging and discharging session data.
     * Returns isCalibrated = false until sufficient charging/discharging data has been collected.
     */
    fun calculateHealth(
        context: Context,
        chargingSessions: List<ChargingSessionEntity>,
        dischargingSessions: List<DischargingSessionEntity>
    ): BatteryHealthInfo {
        val designCapacity = getDesignCapacityMah(context)

        // Filter valid charging sessions (at least 8% gain and 2 minutes duration)
        val validCharges = chargingSessions.filter {
            it.isCompleted && (it.endLevel - it.startLevel) >= 8 && it.durationSeconds >= 120
        }

        // Filter valid discharging sessions (at least 8% drain and 5 minutes duration)
        val validDischarges = dischargingSessions.filter {
            it.isCompleted && (it.startLevel - it.endLevel) >= 8 && it.durationSeconds >= 300
        }

        val totalChargedDelta = validCharges.sumOf { max(0, it.endLevel - it.startLevel) }
        val totalDrainedDelta = validDischarges.sumOf { max(0, it.startLevel - it.endLevel) }
        val totalDeltaAnalyzed = totalChargedDelta + totalDrainedDelta
        val totalSessionsCount = validCharges.size + validDischarges.size

        // We require at least 2 valid sessions AND cumulative >= 30% level change to calibrate accurately
        val isCalibrated = (totalSessionsCount >= 2 && totalDeltaAnalyzed >= 30) || (totalSessionsCount >= 1 && totalDeltaAnalyzed >= 50)
        val progressPercent = if (isCalibrated) 100 else ((totalDeltaAnalyzed / 35.0f) * 100f).roundToInt().coerceIn(0, 95)

        val totalAllCharged = chargingSessions.sumOf { max(0, it.endLevel - it.startLevel) }
        val totalAllDrained = dischargingSessions.sumOf { max(0, it.startLevel - it.endLevel) }
        val totalEquivalentCycles = (totalAllCharged + totalAllDrained) / 200f

        val avgTemp = if (chargingSessions.isNotEmpty() || dischargingSessions.isNotEmpty()) {
            val allTemps = chargingSessions.map { it.avgTemp } + dischargingSessions.map { it.avgTemp }
            if (allTemps.isNotEmpty()) allTemps.average().toFloat() else 31.5f
        } else {
            31.5f
        }

        if (!isCalibrated) {
            return BatteryHealthInfo(
                healthPercentage = null,
                designCapacityMah = designCapacity,
                estimatedCapacityMah = null,
                conditionLabel = "Calibrating",
                totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
                totalSessionsAnalyzed = totalSessionsCount,
                minSessionsRequired = 2,
                avgOperatingTempCelsius = avgTemp,
                isCalibrated = false,
                progressPercent = progressPercent
            )
        }

        var weightedCapacitySum = 0.0
        var totalWeight = 0.0

        // Process charging sessions
        for (session in validCharges) {
            val deltaLevel = max(1, session.endLevel - session.startLevel).toFloat()
            val durHours = max(0.04f, session.durationSeconds / 3600f)

            // Net current drawn into battery
            val estimatedCurrentMa = when {
                session.plugType.contains("Wireless", ignoreCase = true) -> 1100f
                session.plugType.contains("USB", ignoreCase = true) -> 850f
                else -> {
                    val speed = session.peakSpeedPercentPerHour
                    if (speed > 45f) 2500f else if (speed > 25f) 1900f else 1500f
                }
            }

            val deliveredMah = estimatedCurrentMa * durHours
            val capacityFromSession = (deliveredMah / (deltaLevel / 100f)).toDouble()

            if (capacityFromSession in (designCapacity * 0.45)..(designCapacity * 1.15)) {
                val weight = (deltaLevel / 10f).toDouble().pow(2.0)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
            }
        }

        // Process discharging sessions
        for (session in validDischarges) {
            val deltaLevel = max(1, session.startLevel - session.endLevel).toFloat()
            val durHours = max(0.08f, session.durationSeconds / 3600f)

            val drainSpeed = if (session.drainSpeedPercentPerHour > 0f) {
                session.drainSpeedPercentPerHour
            } else {
                deltaLevel / durHours
            }

            val estimatedDrainCurrentMa = when {
                drainSpeed > 22f -> 620f
                drainSpeed > 14f -> 440f
                else -> 280f
            }

            val drawnMah = estimatedDrainCurrentMa * durHours
            val capacityFromSession = (drawnMah / (deltaLevel / 100f)).toDouble()

            if (capacityFromSession in (designCapacity * 0.45)..(designCapacity * 1.15)) {
                val weight = (deltaLevel / 10f).toDouble().pow(2.0)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
            }
        }

        val hotCharges = chargingSessions.count { it.maxTemp >= 40f }
        val hotDischarges = dischargingSessions.count { it.maxTemp >= 40f }
        val thermalWearPenalty = (hotCharges + hotDischarges) * 0.08f
        val cycleWearPenalty = (totalEquivalentCycles * 0.05f).coerceAtMost(20f)
        val totalWearPenalty = cycleWearPenalty + thermalWearPenalty

        val baselineCapacity = designCapacity * (1.0 - (totalWearPenalty / 100.0).coerceIn(0.0, 0.35))

        val finalEstimatedCapacity = if (totalWeight > 0.0) {
            val empiricalCapacity = weightedCapacitySum / totalWeight
            val confidence = (totalSessionsCount / 8.0).coerceIn(0.5, 0.95)
            (empiricalCapacity * confidence) + (baselineCapacity * (1.0 - confidence))
        } else {
            baselineCapacity
        }

        val rawHealthRatio = (finalEstimatedCapacity / designCapacity.toDouble()) * 100.0
        val healthPercent = rawHealthRatio.roundToInt().coerceIn(45, 100)
        val roundedEstimatedCapacity = (designCapacity * (healthPercent / 100.0)).roundToInt()

        val condition = when {
            healthPercent >= 92 -> "Excellent"
            healthPercent >= 84 -> "Good"
            healthPercent >= 75 -> "Fair"
            else -> "Degraded"
        }

        return BatteryHealthInfo(
            healthPercentage = healthPercent,
            designCapacityMah = designCapacity,
            estimatedCapacityMah = roundedEstimatedCapacity,
            conditionLabel = condition,
            totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
            totalSessionsAnalyzed = totalSessionsCount,
            minSessionsRequired = 2,
            avgOperatingTempCelsius = avgTemp,
            isCalibrated = true,
            progressPercent = 100
        )
    }
}
