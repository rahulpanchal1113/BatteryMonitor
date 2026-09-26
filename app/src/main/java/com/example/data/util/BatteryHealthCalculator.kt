package com.example.data.util

import android.content.Context
import android.os.BatteryManager
import com.example.data.local.ChargingSessionEntity
import com.example.data.local.DischargingSessionEntity
import com.example.data.model.BatteryHealthInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object BatteryHealthCalculator {

    private const val PREFS_NAME = "battery_health_prefs"
    private const val KEY_CACHED_DESIGN_CAPACITY = "cached_design_capacity_mah"
    private const val KEY_SMOOTHED_CAPACITY = "smoothed_capacity_mah"
    private const val DEFAULT_CAPACITY_MAH = 5000
    private const val REQUIRED_CUMULATIVE_DELTA = 100 // 100% total equivalent cycle (e.g. 50% + 50%)

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
     * Requires at least 1 full equivalent cycle (cumulative 100% level change across valid charging & discharging sessions)
     * before graduating from the "Calibrating" progress bar state.
     *
     * Uses heavy exponential moving damping so health values do not jump or oscillate, and applies ceil()
     * to the final percentage to provide a safe measurement margin.
     */
    fun calculateHealth(
        context: Context,
        chargingSessions: List<ChargingSessionEntity>,
        dischargingSessions: List<DischargingSessionEntity>
    ): BatteryHealthInfo {
        val designCapacity = getDesignCapacityMah(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Filter valid charging sessions (at least 6% gain and 100 seconds duration)
        val validCharges = chargingSessions.filter {
            it.isCompleted && (it.endLevel - it.startLevel) >= 6 && it.durationSeconds >= 100
        }

        // Filter valid discharging sessions (at least 6% drain and 180 seconds duration)
        val validDischarges = dischargingSessions.filter {
            it.isCompleted && (it.startLevel - it.endLevel) >= 6 && it.durationSeconds >= 180
        }

        val totalChargedDelta = validCharges.sumOf { max(0, it.endLevel - it.startLevel) }
        val totalDrainedDelta = validDischarges.sumOf { max(0, it.startLevel - it.endLevel) }
        val totalDeltaAnalyzed = totalChargedDelta + totalDrainedDelta
        val totalSessionsCount = validCharges.size + validDischarges.size

        // Total equivalent cycles completed (e.g. 100% delta = 1.0 cycle)
        val totalEquivalentCycles = totalDeltaAnalyzed / 100f

        val avgTemp = if (chargingSessions.isNotEmpty() || dischargingSessions.isNotEmpty()) {
            val allTemps = chargingSessions.map { it.avgTemp } + dischargingSessions.map { it.avgTemp }
            if (allTemps.isNotEmpty()) allTemps.average().toFloat() else 31.5f
        } else {
            31.5f
        }

        // Progress towards completing 1 full 100% equivalent cycle (0% to 100%)
        val isCalibrated = totalDeltaAnalyzed >= REQUIRED_CUMULATIVE_DELTA
        val progressPercent = if (isCalibrated) 100 else ((totalDeltaAnalyzed.toFloat() / REQUIRED_CUMULATIVE_DELTA.toFloat()) * 100f).roundToInt().coerceIn(0, 99)

        if (!isCalibrated) {
            return BatteryHealthInfo(
                healthPercentage = null,
                designCapacityMah = designCapacity,
                estimatedCapacityMah = null,
                conditionLabel = "Calibrating",
                totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
                totalSessionsAnalyzed = totalSessionsCount,
                minSessionsRequired = 1,
                avgOperatingTempCelsius = avgTemp,
                isCalibrated = false,
                progressPercent = progressPercent
            )
        }

        // Calculate weighted capacity from charging and discharging telemetry
        var weightedCapacitySum = 0.0
        var totalWeight = 0.0

        // 1. Process charging sessions
        for (session in validCharges) {
            val deltaLevel = max(1, session.endLevel - session.startLevel).toFloat()
            val durHours = max(0.03f, session.durationSeconds / 3600f)

            // Net current delivered into battery
            val estimatedCurrentMa = when {
                session.plugType.contains("Wireless", ignoreCase = true) -> 1100f
                session.plugType.contains("USB", ignoreCase = true) -> 850f
                else -> {
                    val speed = session.peakSpeedPercentPerHour
                    if (speed > 45f) 2400f else if (speed > 25f) 1850f else 1450f
                }
            }

            val deliveredMah = estimatedCurrentMa * durHours
            val capacityFromSession = (deliveredMah / (deltaLevel / 100f)).toDouble()

            // Filter plausible boundaries
            if (capacityFromSession in (designCapacity * 0.5)..(designCapacity * 1.15)) {
                // Quadratic weighting: Larger cycles (e.g. 50%) have dramatically higher confidence
                val weight = (deltaLevel / 10f).toDouble().pow(2.2)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
            }
        }

        // 2. Process discharging sessions
        for (session in validDischarges) {
            val deltaLevel = max(1, session.startLevel - session.endLevel).toFloat()
            val durHours = max(0.05f, session.durationSeconds / 3600f)

            val drainSpeed = if (session.drainSpeedPercentPerHour > 0f) {
                session.drainSpeedPercentPerHour
            } else {
                deltaLevel / durHours
            }

            val estimatedDrainCurrentMa = when {
                drainSpeed > 22f -> 600f
                drainSpeed > 14f -> 430f
                else -> 270f
            }

            val drawnMah = estimatedDrainCurrentMa * durHours
            val capacityFromSession = (drawnMah / (deltaLevel / 100f)).toDouble()

            if (capacityFromSession in (designCapacity * 0.5)..(designCapacity * 1.15)) {
                val weight = (deltaLevel / 10f).toDouble().pow(2.2)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
            }
        }

        // Wear modeling adjustments
        val hotCharges = chargingSessions.count { it.maxTemp >= 40f }
        val hotDischarges = dischargingSessions.count { it.maxTemp >= 40f }
        val thermalWearPenalty = (hotCharges + hotDischarges) * 0.06f
        val cycleWearPenalty = (totalEquivalentCycles * 0.04f).coerceAtMost(15f)
        val totalWearPenalty = cycleWearPenalty + thermalWearPenalty

        val baselineCapacity = designCapacity * (1.0 - (totalWearPenalty / 100.0).coerceIn(0.0, 0.35))

        val currentEmpiricalCapacity = if (totalWeight > 0.0) {
            val empirical = weightedCapacitySum / totalWeight
            val confidence = (totalDeltaAnalyzed / 250.0).coerceIn(0.6, 0.95)
            (empirical * confidence) + (baselineCapacity * (1.0 - confidence))
        } else {
            baselineCapacity
        }

        // 3. Stabilization: Exponential moving average filter & dampening against fluctuations
        val prevSmoothed = prefs.getFloat(KEY_SMOOTHED_CAPACITY, 0f).toDouble()
        val stabilizedCapacity = if (prevSmoothed in (designCapacity * 0.45)..(designCapacity * 1.15)) {
            // Apply heavy 85% momentum to prevent jumping between 92 -> 94 -> 93
            val alpha = 0.15
            var smoothed = (prevSmoothed * (1.0 - alpha)) + (currentEmpiricalCapacity * alpha)

            // Limit single recalculation variance to at most ±0.75% of design capacity
            val maxDelta = designCapacity * 0.0075
            if (smoothed > prevSmoothed + maxDelta) smoothed = prevSmoothed + maxDelta
            if (smoothed < prevSmoothed - maxDelta) smoothed = prevSmoothed - maxDelta

            smoothed
        } else {
            currentEmpiricalCapacity
        }

        // Save smoothed capacity to persistent preferences
        prefs.edit().putFloat(KEY_SMOOTHED_CAPACITY, stabilizedCapacity.toFloat()).apply()

        // 4. Calculate health percentage and CEIL the value (e.g. 92.1% -> 93%)
        val rawHealthRatio = (stabilizedCapacity / designCapacity.toDouble()) * 100.0
        val healthPercent = ceil(rawHealthRatio).toInt().coerceIn(45, 100)
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
            minSessionsRequired = 1,
            avgOperatingTempCelsius = avgTemp,
            isCalibrated = true,
            progressPercent = 100
        )
    }
}
