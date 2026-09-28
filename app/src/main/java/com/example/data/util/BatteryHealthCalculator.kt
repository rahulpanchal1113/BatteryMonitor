package com.example.data.util

import android.content.Context
import android.os.BatteryManager
import com.example.data.local.ChargingSessionEntity
import com.example.data.local.DischargingSessionEntity
import com.example.data.model.BatteryHealthInfo
import com.example.data.model.BatteryStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object BatteryHealthCalculator {

    private const val PREFS_NAME = "battery_health_prefs"
    private const val KEY_CACHED_DESIGN_CAPACITY = "cached_design_capacity_mah"
    private const val KEY_SMOOTHED_CAPACITY = "smoothed_capacity_mah"
    private const val KEY_LOCKED_HEALTH_PERCENT = "locked_health_percent"
    private const val KEY_LAST_LOCKED_DATE = "last_locked_date"
    private const val KEY_LAST_FULL_RECALIBRATION_TIME = "last_full_recalibration_time"
    private const val KEY_RECALIBRATION_CYCLE_START_TIME = "recalibration_cycle_start_time"
    private const val KEY_IS_RECALIBRATING = "is_recalibrating"
    private const val KEY_LAST_CALIBRATED_DATE = "last_calibrated_date"

    private const val DEFAULT_CAPACITY_MAH = 5000

    // Stricter, high-confidence calibration parameters:
    // Requires ~3 to 5 days of normal charging (deep sessions of 20%+ gain, accumulating 150% delta)
    private const val REQUIRED_LONG_CHARGE_DELTA = 150 // 150% cumulative deep charge gain
    private const val REQUIRED_LONG_SESSIONS = 3       // At least 3 deep charging sessions
    private const val MIN_LONG_CHARGE_DELTA = 20       // Exclude short top-ups < 20% gain
    private const val MIN_LONG_CHARGE_DURATION_SEC = 900L // 15 minutes minimum duration
    private const val ESTIMATED_CALIBRATION_DAYS = 4   // ~3-5 days average (4 days nominal)
    private const val ONE_MONTH_MS = 30L * 24 * 3600 * 1000L // 30-day recalibration interval

    /**
     * Resolves the factory rated design capacity (when the phone was brand new) in mAh.
     * Uses Android's internal PowerProfile via reflection, fallback to sysfs or default.
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

        // 2. Try sysfs power supply design capacity
        if (detectedCapacity <= 0) {
            detectedCapacity = readSysfsDesignCapacity()
        }

        val finalCapacity = if (detectedCapacity in 1800..15000) detectedCapacity else DEFAULT_CAPACITY_MAH
        prefs.edit().putInt(KEY_CACHED_DESIGN_CAPACITY, finalCapacity).apply()
        return finalCapacity
    }

    private fun readSysfsDesignCapacity(): Int {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val uah = content.toLongOrNull() ?: 0L
                    if (uah in 1_800_000L..15_000_000L) {
                        return (uah / 1000L).toInt()
                    }
                }
            } catch (_: Exception) {}
        }
        return 0
    }

    /**
     * Reads actual full charge capacity reported by hardware battery fuel gauge IC in mAh, if available.
     */
    private fun readHardwareFuelGaugeCapacity(context: Context, currentLevel: Int): Int? {
        // 1. Check sysfs charge_full
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val uah = file.readText().trim().toLongOrNull() ?: 0L
                    if (uah in 1_500_000L..15_000_000L) {
                        return (uah / 1000L).toInt()
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Check BatteryManager charge counter if level is substantial
        if (currentLevel in 15..95) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
                if (chargeCounterUah > 500_000) {
                    val fullEstMah = ((chargeCounterUah / 1000.0) / (currentLevel / 100.0)).roundToInt()
                    if (fullEstMah in 1800..15000) {
                        return fullEstMah
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Triggers a manual or scheduled full battery recalibration cycle.
     */
    fun triggerFullRecalibration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_RECALIBRATING, true)
            .putLong(KEY_RECALIBRATION_CYCLE_START_TIME, System.currentTimeMillis())
            .remove(KEY_SMOOTHED_CAPACITY)
            .remove(KEY_LOCKED_HEALTH_PERCENT)
            .remove(KEY_LAST_LOCKED_DATE)
            .apply()
    }

    /**
     * Estimates battery health dynamically from empirical charging sessions.
     *
     * Key Principles:
     * 1. Long Charging Sessions Only: Discard short top-ups (< 20% gain or < 15 min) and discharging
     *    data from capacity calculations to eliminate noise and erratic swings.
     * 2. Real-time Calibration Progress: Ongoing live charging delta is combined in real-time so
     *    the collection progress percentage updates live while plugged in.
     * 3. Explains strictly in Days: Calibration ETA is communicated in days (~3 to 5 days) without
     *    confusing cycle jargon.
     * 4. Rock-Solid Intra-Day Stability: Damped exponential moving average with deadband hysteresis
     *    and intra-day health locking prevents unrealistic daily jumping (e.g. 92% -> 91% -> 93%).
     */
    fun calculateHealth(
        context: Context,
        chargingSessions: List<ChargingSessionEntity>,
        dischargingSessions: List<DischargingSessionEntity>,
        liveStatus: BatteryStatus? = null
    ): BatteryHealthInfo {
        val designCapacity = getDesignCapacityMah(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val lastRecalibrationTime = prefs.getLong(KEY_LAST_FULL_RECALIBRATION_TIME, 0L)
        var isRecalibrating = prefs.getBoolean(KEY_IS_RECALIBRATING, false)
        var recalibrationStartTime = prefs.getLong(KEY_RECALIBRATION_CYCLE_START_TIME, 0L)

        // Check if 1 month has elapsed since last full recalibration
        val isMonthElapsed = lastRecalibrationTime == 0L || (now - lastRecalibrationTime) >= ONE_MONTH_MS
        if (isMonthElapsed && !isRecalibrating) {
            isRecalibrating = true
            recalibrationStartTime = now
            prefs.edit()
                .putBoolean(KEY_IS_RECALIBRATING, true)
                .putLong(KEY_RECALIBRATION_CYCLE_START_TIME, recalibrationStartTime)
                .apply()
        }

        val effectiveCalStartTime = if (recalibrationStartTime > 0L) recalibrationStartTime else 0L

        // 1. Filter ONLY completed LONG CHARGING SESSIONS (Exclude short top-ups < 20% gain or < 15 min)
        val completedLongCharges = chargingSessions.filter {
            it.isCompleted && (it.endLevel - it.startLevel) >= MIN_LONG_CHARGE_DELTA && it.durationSeconds >= MIN_LONG_CHARGE_DURATION_SEC
        }

        // 2. Extract active charging session if currently plugged in for real-time progress update
        val activeSession = chargingSessions.firstOrNull { !it.isCompleted }
        val liveChargingDelta = if (liveStatus?.isCharging == true && activeSession != null) {
            val curLevel = max(activeSession.endLevel, liveStatus.level)
            max(0, curLevel - activeSession.startLevel)
        } else if (activeSession != null) {
            max(0, activeSession.endLevel - activeSession.startLevel)
        } else {
            0
        }

        // Compute cumulative long charge delta during the current calibration period
        val calCompletedDelta = completedLongCharges
            .filter { it.startTime >= effectiveCalStartTime }
            .sumOf { max(0, it.endLevel - it.startLevel) }

        // Include the active live charging delta in real-time!
        val currentCalibrationDelta = calCompletedDelta + liveChargingDelta
        val calLongSessionsCount = completedLongCharges.count { it.startTime >= effectiveCalStartTime } + (if (liveChargingDelta >= MIN_LONG_CHARGE_DELTA) 1 else 0)

        // Check if recalibration threshold is achieved (~150% deep charge delta or >= 3 deep sessions)
        val reachedCalibrationGoal = currentCalibrationDelta >= REQUIRED_LONG_CHARGE_DELTA || (calLongSessionsCount >= REQUIRED_LONG_SESSIONS && currentCalibrationDelta >= 100)

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayKey = dateFmt.format(Date(now))

        if (isRecalibrating && reachedCalibrationGoal) {
            isRecalibrating = false
            val displayDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val dateStr = displayDateFmt.format(Date(now))
            prefs.edit()
                .putBoolean(KEY_IS_RECALIBRATING, false)
                .putLong(KEY_LAST_FULL_RECALIBRATION_TIME, now)
                .putString(KEY_LAST_CALIBRATED_DATE, dateStr)
                .apply()
        }

        val totalLongSessionsCount = completedLongCharges.size
        val totalDeltaAllLongCharges = completedLongCharges.sumOf { max(0, it.endLevel - it.startLevel) }
        val isCalibrated = !isRecalibrating && (totalDeltaAllLongCharges >= 100 || lastRecalibrationTime > 0L)

        val progressPercent = if (isCalibrated) {
            100
        } else {
            ((currentCalibrationDelta.toFloat() / REQUIRED_LONG_CHARGE_DELTA.toFloat()) * 100f).roundToInt().coerceIn(0, 99)
        }

        // Calculate remaining calibration days based strictly on days (not cycles)
        val calibrationDaysRemaining = if (isCalibrated) {
            0
        } else {
            max(1, ceil((1.0 - (progressPercent / 100.0)) * ESTIMATED_CALIBRATION_DAYS.toDouble()).toInt())
        }

        val avgTemp = if (chargingSessions.isNotEmpty() || dischargingSessions.isNotEmpty()) {
            val allTemps = chargingSessions.map { it.avgTemp } + dischargingSessions.map { it.avgTemp }
            if (allTemps.isNotEmpty()) allTemps.average().toFloat() else 31.5f
        } else {
            31.5f
        }

        val daysSinceLastCal = if (lastRecalibrationTime > 0L) ((now - lastRecalibrationTime) / (24 * 3600 * 1000L)).toInt() else 0
        val daysUntilNextRecalibration = max(0, 30 - daysSinceLastCal)
        val lastCalDate = prefs.getString(KEY_LAST_CALIBRATED_DATE, "") ?: ""

        val totalEquivalentCycles = (chargingSessions.filter { it.isCompleted }.sumOf { max(0, it.endLevel - it.startLevel) } +
                dischargingSessions.filter { it.isCompleted }.sumOf { max(0, it.startLevel - it.endLevel) }) / 100f

        if (!isCalibrated) {
            return BatteryHealthInfo(
                healthPercentage = null,
                designCapacityMah = designCapacity,
                estimatedCapacityMah = null,
                conditionLabel = if (lastRecalibrationTime > 0L) "Monthly Recalibrating" else "Calibrating",
                totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
                totalSessionsAnalyzed = chargingSessions.size + dischargingSessions.size,
                longSessionsCount = totalLongSessionsCount,
                minSessionsRequired = REQUIRED_LONG_SESSIONS,
                avgOperatingTempCelsius = avgTemp,
                isCalibrated = false,
                progressPercent = progressPercent,
                calibrationDaysRemaining = calibrationDaysRemaining,
                totalDaysRequired = ESTIMATED_CALIBRATION_DAYS,
                nextRecalibrationDaysRemaining = daysUntilNextRecalibration,
                isMonthlyRecalibrating = isRecalibrating,
                lastCalibratedDate = lastCalDate
            )
        }

        // -------------------------------------------------------------
        // CALIBRATED STATE: Calculate Stable Empirical Capacity
        // -------------------------------------------------------------
        var weightedCapacitySum = 0.0
        var totalWeight = 0.0

        for (session in completedLongCharges) {
            val deltaLevel = max(1, session.endLevel - session.startLevel).toFloat()
            val durHours = max(0.15f, session.durationSeconds / 3600f)
            val speedPercentPerHour = deltaLevel / durHours

            val estimatedCurrentMa = when {
                session.plugType.contains("Wireless", ignoreCase = true) -> 1050f
                session.plugType.contains("USB", ignoreCase = true) -> 800f
                speedPercentPerHour > 55f -> 2300f
                speedPercentPerHour > 35f -> 1850f
                speedPercentPerHour > 20f -> 1500f
                else -> 1200f
            }

            val deliveredMah = estimatedCurrentMa * durHours
            val capacityFromSession = (deliveredMah / (deltaLevel / 100f)).toDouble()

            if (capacityFromSession in (designCapacity * 0.55)..(designCapacity * 1.08)) {
                // Weight quadratically by deltaLevel: a 60% session has 4x the weight of a 30% session
                val weight = (deltaLevel / 10f).toDouble().pow(2.0)
                weightedCapacitySum += capacityFromSession * weight
                totalWeight += weight
            }
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        val hwGaugeMah = readHardwareFuelGaugeCapacity(context, currentLevel)

        // Baseline degradation model based on cycles & thermal stress
        val hotSessions = chargingSessions.count { it.maxTemp >= 40f }
        val thermalPenaltyPercent = (hotSessions * 0.20f).coerceAtMost(5.0f)
        val cyclePenaltyPercent = (totalEquivalentCycles * 0.08f).coerceAtMost(15.0f)
        val baselineWearPercent = (4.0f + cyclePenaltyPercent + thermalPenaltyPercent).coerceIn(3.0f, 35.0f)
        val wearBaselineCapacity = designCapacity * (1.0 - (baselineWearPercent / 100.0))

        val currentEmpiricalCapacity: Double = when {
            hwGaugeMah != null && hwGaugeMah.toDouble() in (designCapacity * 0.6)..(designCapacity * 1.05) -> {
                val hwVal = hwGaugeMah.toDouble()
                if (totalWeight > 0.0) {
                    val sessionVal = weightedCapacitySum / totalWeight
                    (hwVal * 0.70) + (sessionVal * 0.30)
                } else {
                    hwVal
                }
            }
            totalWeight > 0.0 -> {
                val sessionVal = weightedCapacitySum / totalWeight
                val confidence = (completedLongCharges.size / 5.0).coerceIn(0.60, 0.90)
                (sessionVal * confidence) + (wearBaselineCapacity * (1.0 - confidence))
            }
            else -> {
                wearBaselineCapacity
            }
        }

        // -------------------------------------------------------------
        // Intra-Day Stabilization & Anti-Fluctuation Logic
        // -------------------------------------------------------------
        val lastLockedDate = prefs.getString(KEY_LAST_LOCKED_DATE, "") ?: ""
        val lockedHealth = prefs.getInt(KEY_LOCKED_HEALTH_PERCENT, 0)
        val prevSmoothed = prefs.getFloat(KEY_SMOOTHED_CAPACITY, 0f).toDouble()

        val finalHealthPercent: Int
        val finalEstimatedCapacity: Int

        if (lockedHealth in 60..98 && lastLockedDate == todayKey) {
            // Already computed and locked for today: maintain rock-solid value throughout the day
            finalHealthPercent = lockedHealth
            finalEstimatedCapacity = if (prevSmoothed > 0) prevSmoothed.roundToInt() else (designCapacity * (lockedHealth / 100.0)).roundToInt()
        } else {
            // New day or first calibration: compute smoothed capacity with high damping
            val stabilizedCapacity = if (prevSmoothed in (designCapacity * 0.50)..(designCapacity * 1.05)) {
                val alpha = 0.06 // High inertia to prevent rapid jumps
                var smoothed = (prevSmoothed * (1.0 - alpha)) + (currentEmpiricalCapacity * alpha)
                val maxDelta = designCapacity * 0.005 // Maximum 0.5% drift per day
                if (smoothed > prevSmoothed + maxDelta) smoothed = prevSmoothed + maxDelta
                if (smoothed < prevSmoothed - maxDelta) smoothed = prevSmoothed - maxDelta
                smoothed
            } else {
                currentEmpiricalCapacity
            }

            prefs.edit().putFloat(KEY_SMOOTHED_CAPACITY, stabilizedCapacity.toFloat()).apply()

            val rawHealthRatio = (stabilizedCapacity / designCapacity.toDouble()) * 100.0
            val targetHealth = rawHealthRatio.roundToInt().coerceIn(60, 98)

            // Deadband / Hysteresis check against previously locked health
            val resolvedHealth = if (lockedHealth in 60..98) {
                val diff = rawHealthRatio - lockedHealth.toDouble()
                when {
                    diff > 0.70 -> min(lockedHealth + 1, targetHealth) // only move +1% if clearly crossed deadband
                    diff < -0.70 -> max(lockedHealth - 1, targetHealth) // only move -1% if clearly dropped
                    else -> lockedHealth // preserve current reading, do not wobble
                }
            } else {
                targetHealth
            }

            finalHealthPercent = resolvedHealth
            finalEstimatedCapacity = stabilizedCapacity.roundToInt()

            prefs.edit()
                .putInt(KEY_LOCKED_HEALTH_PERCENT, finalHealthPercent)
                .putString(KEY_LAST_LOCKED_DATE, todayKey)
                .apply()
        }

        val condition = when {
            finalHealthPercent >= 90 -> "Excellent"
            finalHealthPercent >= 82 -> "Good"
            finalHealthPercent >= 75 -> "Fair"
            else -> "Degraded"
        }

        return BatteryHealthInfo(
            healthPercentage = finalHealthPercent,
            designCapacityMah = designCapacity,
            estimatedCapacityMah = finalEstimatedCapacity,
            conditionLabel = condition,
            totalCyclesCount = (totalEquivalentCycles * 10f).roundToInt() / 10f,
            totalSessionsAnalyzed = chargingSessions.size + dischargingSessions.size,
            longSessionsCount = totalLongSessionsCount,
            minSessionsRequired = REQUIRED_LONG_SESSIONS,
            avgOperatingTempCelsius = avgTemp,
            isCalibrated = true,
            progressPercent = 100,
            calibrationDaysRemaining = 0,
            totalDaysRequired = ESTIMATED_CALIBRATION_DAYS,
            nextRecalibrationDaysRemaining = daysUntilNextRecalibration,
            isMonthlyRecalibrating = false,
            lastCalibratedDate = lastCalDate
        )
    }
}
