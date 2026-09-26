package com.example.data.model

data class BatteryHealthInfo(
    val healthPercentage: Int = 100,
    val designCapacityMah: Int = 5000,
    val estimatedCapacityMah: Int = 5000,
    val conditionLabel: String = "Good",
    val totalCyclesCount: Float = 0f,
    val totalSessionsAnalyzed: Int = 0,
    val avgOperatingTempCelsius: Float = 31f,
    val isEstimatedFromData: Boolean = true
)
