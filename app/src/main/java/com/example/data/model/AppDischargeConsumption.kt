package com.example.data.model

data class AppDischargeConsumption(
    val packageName: String,
    val appName: String,
    val totalPercentConsumed: Float,
    val foregroundPercent: Float,
    val backgroundPercent: Float,
    val foregroundTimeMillis: Long = 0L,
    val backgroundTimeMillis: Long = 0L,
    val rank: Int = 1,
    val peakTempCelsius: Float = 0f,
    val avgTempCelsius: Float = 0f
)
