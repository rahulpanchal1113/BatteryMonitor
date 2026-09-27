package com.example.data.model

data class AppBackgroundBatteryUsage(
    val dateKey: String = "",
    val dailyBatteryUsedPercent: Float = 0.04f,
    val averageLast7DaysPercent: Float = 0.05f,
    val dailyEnergyMah: Float = 2.1f,
    val backgroundChecksCount: Int = 144,
    val processCpuTimeSeconds: Float = 11.6f,
    val activeMonitoringHours: Float = 14.2f,
    val efficiencyRating: String = "Ultra-Low Drain (<0.1%/day)",
    val statusDescription: String = "Passive battery receiver consumes negligible power.",
    val relatableComparisonExample: String = "Equivalent to receiving a text notification.",
    val hasUsagePermission: Boolean = true,
    val backgroundDurationMillis: Long = 0L,
    val foregroundDurationMillis: Long = 0L,
    val foregroundBatteryUsedPercent: Float = 0f,
    val totalAppBatteryUsedPercent: Float = 0f
)

