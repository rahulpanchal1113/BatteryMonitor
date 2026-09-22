package com.example.data.model

data class AppBackgroundBatteryUsage(
    val dateKey: String = "",
    val dailyBatteryUsedPercent: Float = 0.12f,
    val averageLast7DaysPercent: Float = 0.14f,
    val dailyEnergyMah: Float = 5.8f,
    val backgroundChecksCount: Int = 144,
    val processCpuTimeSeconds: Float = 11.6f,
    val activeMonitoringHours: Float = 14.2f,
    val efficiencyRating: String = "Ultra-Low Drain (<0.2%/day)",
    val statusDescription: String = "Passive battery receiver consumes negligible power.",
    val relatableComparisonExample: String = "Equivalent to unlocking your phone 5 times."
)

