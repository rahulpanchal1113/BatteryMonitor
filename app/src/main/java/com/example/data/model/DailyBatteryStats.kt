package com.example.data.model

data class DailyBatteryStats(
    val dateKey: String,
    val sessionsCount: Int,
    val totalChargeDurationSeconds: Long,
    val totalPercentGained: Int,
    val avgTemperature: Float,
    val maxTemperature: Float,
    val fastestSessionRate: Float
)
