package com.example.data.model

data class BatteryStatus(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val status: String = "Discharging",
    val plugType: String = "Battery",
    val tempCelsius: Float = 0f,
    val voltageMilliVolts: Int = 0,
    val currentMilliAmps: Int = 0,
    val grossWattageWatts: Float = 0f,
    val netWattageWatts: Float = 0f,
    val activeDeviceDrawWatts: Float = 0f,
    val chargingSpeedType: String = "Standard",
    val health: String = "Good",
    val healthPercentage: Int = 100,
    val designCapacityMah: Int = 5000,
    val estimatedCapacityMah: Int = 5000,
    val technology: String = "Li-ion",
    val estimatedMinutesRemaining: Long? = null,
    val liveRatePercentPerHour: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val wattageWatts: Float
        get() = grossWattageWatts

    val tempFahrenheit: Float
        get() = (tempCelsius * 9f / 5f) + 32f

    val voltageVolts: Float
        get() = voltageMilliVolts / 1000f

    val currentAmperes: Float
        get() = currentMilliAmps / 1000f
}
