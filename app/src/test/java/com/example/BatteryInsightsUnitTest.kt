package com.example

import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import com.example.data.util.InsightsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryInsightsUnitTest {

    @Test
    fun testEmptyDataReturnsCollectingState() {
        val insights = InsightsCalculator.calculateInsights(emptyList(), emptyList())
        assertNotNull(insights)
        assertEquals(5, insights.brackets.size)
        org.junit.Assert.assertFalse(insights.hasEnoughData)
        org.junit.Assert.assertNull(insights.peakBracket)
        assertTrue(insights.totalRecordedSessions == 0)
        assertTrue(insights.thermalImpactNote.contains("Collecting battery data"))
    }

    @Test
    fun testCalculatesFastestChargingBracketAndDropOffPoint() {
        val now = 1700000000000L
        val events = listOf(
            // 20% to 30% in 5 minutes -> 120%/hr (fast)
            BatteryEventEntity(
                id = 1,
                timestamp = now,
                eventType = "PLUGGED_IN",
                batteryLevel = 20,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 31.0f,
                voltageMilliVolts = 3800,
                batteryHealth = "Good",
                sessionId = 100L
            ),
            BatteryEventEntity(
                id = 2,
                timestamp = now + (5 * 60 * 1000L),
                eventType = "SAMPLE",
                batteryLevel = 30,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 33.0f,
                voltageMilliVolts = 3950,
                batteryHealth = "Good",
                sessionId = 100L
            ),
            // 30% to 40% in 5 minutes -> 120%/hr (fast)
            BatteryEventEntity(
                id = 3,
                timestamp = now + (10 * 60 * 1000L),
                eventType = "SAMPLE",
                batteryLevel = 40,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 35.0f,
                voltageMilliVolts = 4100,
                batteryHealth = "Good",
                sessionId = 100L
            ),
            // 80% to 85% in 15 minutes -> 20%/hr (tapered)
            BatteryEventEntity(
                id = 4,
                timestamp = now + (35 * 60 * 1000L),
                eventType = "SAMPLE",
                batteryLevel = 80,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 34.0f,
                voltageMilliVolts = 4300,
                batteryHealth = "Good",
                sessionId = 100L
            ),
            BatteryEventEntity(
                id = 5,
                timestamp = now + (50 * 60 * 1000L),
                eventType = "SAMPLE",
                batteryLevel = 85,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 33.0f,
                voltageMilliVolts = 4320,
                batteryHealth = "Good",
                sessionId = 100L
            ),
            BatteryEventEntity(
                id = 6,
                timestamp = now + (51 * 60 * 1000L),
                eventType = "UNPLUGGED",
                batteryLevel = 85,
                isCharging = false,
                plugType = "Battery",
                temperatureCelsius = 32.0f,
                voltageMilliVolts = 4320,
                batteryHealth = "Good",
                sessionId = 100L
            )
        )

        val sessions = listOf(
            ChargingSessionEntity(
                id = 100L,
                startTime = now,
                endTime = now + (50 * 60 * 1000L),
                startLevel = 20,
                endLevel = 85,
                plugType = "AC Adapter",
                startTemp = 31.0f,
                maxTemp = 35.0f,
                avgTemp = 33.0f,
                durationSeconds = 3000L,
                peakSpeedPercentPerHour = 120f,
                isCompleted = true,
                dateKey = "2026-09-20"
            )
        )

        val insights = InsightsCalculator.calculateInsights(events, sessions)

        assertNotNull(insights.peakBracket)
        assertEquals("20% - 40%", insights.peakBracket?.bracketLabel)
        assertNotNull(insights.slowestBracket)
        assertEquals("80% - 100%", insights.slowestBracket?.bracketLabel)
        assertEquals(1, insights.totalRecordedSessions)
        assertTrue(insights.dropOffPointPercent != null)
    }
}
