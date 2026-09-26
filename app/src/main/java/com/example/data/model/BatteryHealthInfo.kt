package com.example.data.model

data class BatteryHealthInfo(
    val healthPercentage: Int? = null,
    val designCapacityMah: Int = 5000,
    val estimatedCapacityMah: Int? = null,
    val conditionLabel: String = "Calibrating",
    val totalCyclesCount: Float = 0f,
    val totalSessionsAnalyzed: Int = 0,
    val minSessionsRequired: Int = 2,
    val avgOperatingTempCelsius: Float = 31f,
    val isCalibrated: Boolean = false,
    val progressPercent: Int = 0
)
