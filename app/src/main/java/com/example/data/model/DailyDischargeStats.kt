package com.example.data.model

data class DailyDischargeStats(
    val dateKey: String,
    val sessionsCount: Int,
    val totalDischargeDurationSeconds: Long,
    val totalPercentDrained: Int,
    val avgTemperature: Float,
    val maxTemperature: Float,
    val avgDrainRatePercentPerHour: Float
)
