package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects whether the device is physically steady (e.g. resting on a desk, stand,
 * or flat surface) versus in motion (held in hand, walking, shaking).
 *
 * To maximize power efficiency, the accelerometer is ONLY active briefly
 * (1.0 to 1.5 seconds) around connection/disconnection events and then immediately
 * unregistered.
 */
open class DeviceSteadinessDetector(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    var lastEvaluatedSteadiness: Boolean = false
        protected set

    @Volatile
    var lastEvaluationTimestamp: Long = 0L
        protected set

    /**
     * Samples accelerometer data for a short window [sampleDurationMs] to determine
     * if the device is stationary/steady (e.g. resting completely undisturbed on a flat surface).
     */
    open suspend fun assessSteadiness(sampleDurationMs: Long = 1000L): Boolean = withContext(Dispatchers.Default) {
        if (sensorManager == null || accelerometer == null) {
            lastEvaluatedSteadiness = false
            lastEvaluationTimestamp = System.currentTimeMillis()
            return@withContext false
        }

        val magnitudes = CopyOnWriteArrayList<Float>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val mag = sqrt((x * x) + (y * y) + (z * z))
                    magnitudes.add(mag)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )

        if (!registered) {
            lastEvaluatedSteadiness = false
            lastEvaluationTimestamp = System.currentTimeMillis()
            return@withContext false
        }

        try {
            delay(sampleDurationMs)
        } finally {
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Exception) {}
        }

        if (magnitudes.size < 6) {
            lastEvaluatedSteadiness = false
            lastEvaluationTimestamp = System.currentTimeMillis()
            return@withContext false
        }

        // Calculate variance and range of magnitude
        val mean = magnitudes.sum() / magnitudes.size
        var sumSquares = 0.0
        var minMag = Float.MAX_VALUE
        var maxMag = Float.MIN_VALUE

        for (m in magnitudes) {
            val diff = m - mean
            sumSquares += (diff * diff)
            if (m < minMag) minMag = m
            if (m > maxMag) maxMag = m
        }

        val variance = sumSquares / magnitudes.size
        val range = maxMag - minMag

        // A truly stationary phone resting undisturbed on a flat surface or desk has near-zero sensor noise (variance < 0.0003, range < 0.035).
        // Any device held in human hand, manipulated, or actively plugged/unplugged will have natural physiological micro-tremor
        // and tactile movement (variance > 0.0004, range > 0.05).
        val isSteady = variance < 0.0003f && range < 0.035f

        lastEvaluatedSteadiness = isSteady
        lastEvaluationTimestamp = System.currentTimeMillis()

        isSteady
    }
}
