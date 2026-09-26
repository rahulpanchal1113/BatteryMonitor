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
     * Resolves the factory rated design capacity (when the phone was new) in mAh.
     * Uses Android's internal PowerProfile via reflection, fallback to hardware charge counter,
     * or standard modern smartphone capacity.
     */
    fun getDesignCapacityMah(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getInt(KEY_CACHED_DESIGN_CAPACITY, 0)
        if (cached in 2000..12000) {
            return cached
        }

        var detectedCapacity = 0

        // 1. Try reading from com.android.internal.os.PowerProfile
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val getBatteryCapacityMethod = powerProfileClass.getMethod("getBatteryCapacity")
            val capacityVal = (getBatteryCapacityMethod.invoke(powerProfile) as? Double)?.toFloat()
            if (capacityVal != null && capacityVal >= 2000f && capacityVal <= 12000f) {
                detectedCapacity = capacityVal.roundToInt()
            }
        } catch (_: Exception) {}

        // 2. Try BatteryManager charge counter if available
        if (detectedCapacity <= 0) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
                if (chargeCounter > 500_000) {
                    val mah = (chargeCounter / 1000f).roundToInt()
                    if (mah in 2000..12000) {
                        detectedCapacity = mah
                    }
                }
            } catch (_: Exception) {}
        }

        val finalCapacity = if (detectedCapacity in 2000..12000) detectedCapacity else DEFAULT_CAPACITY_MAH
        prefs.edit().putInt(KEY_CACHED_DESIGN_CAPACITY, finalCapacity).apply()
        return finalCapacity
    }

    /**
     * Computes absolute battery health percentage and current usable capacity
     * using the app's own recorded charging and discharging telemetry data.
     */
    fun calculateHealth(
        context: Context,
        chargingSessions: List<ChargingSessionEntity>,
        dischargingSessions: List<DischargingSessionEntity>
    ): BatteryHealthInfo {
        val designCapacity = getDesignCapacityMah(context)

        // 1. Filter valid charging sessions (sufficient delta level and duration for reliable integration)
        val validCharges = chargingSessions.filter {
            it.isCompleted && (it.endLevel - it.startLevel) >= 5 && it.durationSeconds >= 120
        }

        // 2. Filter valid discharging sessions
        val validDischarges = dischargingSessions.filter {
            it.isCompleted && (it.startLevel - it.endLevel) >= 5 && it.durationSeconds >= 300
        }

        var weightedCapacitySum = 0.0
        var totalWeight = 0.0
        var totalSessionsCount = 0

        // Process charging sessions:
        // When charging, energy delivered is proportional to duration, voltage, and plug type intake rate.
        for (session in validCharges) {
            val deltaLevel = max(1, session.endLevel - session.startLevel).toFloat()
            val durHours = max(0.05f, session.durationSeconds / 3600f)

            // Net charging current into the cell based on plug type and speed
            val estimatedCurrentMa = when {
                session.plugType.contains("Wireless", ignoreCase = true) -> 1200f
                session.plugType.contains("USB", ignoreCase = true) -> 900f
                else -> {
                    // Standard / Fast AC adapter: typical net cell intake is 1800 - 3200 mA
                    val speed = session.peakSpeedPercentPerHour
                    if (speed > 40f) 2600f else if (speed > 25f) 2100f else 1700f
                }
            }

            val deliveredMah = estimatedCurrentMa * durHours
            val capacityFromSession = (deliveredMah / (deltaLevel / 100f)).toDouble()

            // Discard extreme outlier glitches
            if (capacityFromSession in (designCapacity * 0.55)..(designCapacity * 1.15)) {
                // Weight quadratically by delta level (a 50% charge is far more statistically significant than 5%)
                val weight = (deltaLevel / 10f).toDouble().pow(2.0)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
                totalSessionsCount++
            }
        }

        // Process discharging sessions:
        // When discharging, energy drawn is measured from duration and discharge speed.
        for (session in validDischarges) {
            val deltaLevel = max(1, session.startLevel - session.endLevel).toFloat()
            val durHours = max(0.08f, session.durationSeconds / 3600f)

            // Discharging current on battery: typical active smartphone load ~ 320 - 580 mA
            val drainSpeed = if (session.drainSpeedPercentPerHour > 0f) {
                session.drainSpeedPercentPerHour
            } else {
                deltaLevel / durHours
            }

            val estimatedDrainCurrentMa = when {
                drainSpeed > 20f -> 650f // heavy usage / gaming / camera
                drainSpeed > 12f -> 450f // moderate active use
                else -> 280f             // light / mixed standby
            }

            val drawnMah = estimatedDrainCurrentMa * durHours
            val capacityFromSession = (drawnMah / (deltaLevel / 100f)).toDouble()

            if (capacityFromSession in (designCapacity * 0.55)..(designCapacity * 1.15)) {
                val weight = (deltaLevel / 10f).toDouble().pow(2.0)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
                totalSessionsCount++
            }
        }

        // Cycle and thermal wear accumulation from our app's historical records:
        val totalChargedPercent = chargingSessions.sumOf { max(0, it.endLevel - it.startLevel) }
        val totalDrainedPercent = dischargingSessions.sumOf { max(0, it.startLevel - it.endLevel) }
        val totalEquivalentCycles = (totalChargedPercent + totalDrainedPercent) / 200f

        val hotCharges = chargingSessions.count { it.maxTemp >= 40f }
        val hotDischarges = dischargingSessions.count { it.maxTemp >= 40f }
        val thermalWearPenaltyPercent = (hotCharges + hotDischarges) * 0.05f

        val cycleWearPenaltyPercent = (totalEquivalentCycles * 0.04f).coerceAtMost(15f)
        val totalWearPenalty = cycleWearPenaltyPercent + thermalWearPenaltyPercent

        // Blend empirical session estimates with initial baseline:
        val baselineCapacity = designCapacity * (1.0 - (totalWearPenalty / 100.0).coerceIn(0.0, 0.25))

        val finalEstimatedCapacity = if (totalWeight > 0.0 && totalSessionsCount >= 2) {
            val empiricalCapacity = weightedCapacitySum / totalWeight
            // Confidence factor ramps up as more sessions are analyzed
            val confidence = (totalSessionsCount / 10.0).coerceIn(0.4, 0.95)
            (empiricalCapacity * confidence) + (baselineCapacity * (1.0 - confidence))
        } else {
            baselineCapacity
        }

        val rawHealthRatio = (finalEstimatedCapacity / designCapacity.toDouble()) * 100.0
        val healthPercent = rawHealthRatio.roundToInt().coerceIn(50, 100)
        val roundedEstimatedCapacity = (designCapacity * (healthPercent / 100.0)).roundToInt()

        val avgTemp = if (chargingSessions.isNotEmpty() || dischargingSessions.isNotEmpty()) {
            val allTemps = chargingSessions.map { it.avgTemp } + dischargingSessions.map { it.avgTemp }
            if (allTemps.isNotEmpty()) allTemps.average().toFloat() else 31.5f
        } else {
            31.5f
        }

        val condition = when {
            healthPercent >= 95 -> "Excellent"
            healthPercent >= 88 -> "Good"
            healthPercent >= 80 -> "Fair"
            else -> "Degraded"
        }

        return BatteryHealthInfo(
            healthPercentage = healthPercent,
            designCapacityMah = designCapacity,
            estimatedCapacityMah = roundedEstimatedCapacity,
            conditionLabel = condition,
            totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
            totalSessionsAnalyzed = totalSessionsCount,
            avgOperatingTempCelsius = avgTemp,
            isEstimatedFromData = totalSessionsCount > 0
        )
    }
}
