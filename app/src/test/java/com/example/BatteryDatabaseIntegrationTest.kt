package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BatteryDao
import com.example.data.local.BatteryDatabase
import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.diagnostics.ConnectionStabilityAnalyzer
import com.example.sensor.DeviceSteadinessDetector

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryDatabaseIntegrationTest {

    private lateinit var database: BatteryDatabase
    private lateinit var dao: BatteryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BatteryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.batteryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveActiveChargingSession() = runBlocking {
        val initialActive = dao.getActiveSession()
        assertNull(initialActive)

        val session = ChargingSessionEntity(
            startTime = 1000L,
            startLevel = 15,
            endLevel = 15,
            plugType = "AC Adapter",
            startTemp = 28.5f,
            maxTemp = 28.5f,
            avgTemp = 28.5f,
            durationSeconds = 0L,
            isCompleted = false,
            dateKey = "2026-09-20"
        )
        val id = dao.insertSession(session)
        val active = dao.getActiveSession()

        assertNotNull(active)
        assertEquals(id, active?.id)
        assertEquals(15, active?.startLevel)
        assertEquals(false, active?.isCompleted)

        // Complete the session
        val completed = active!!.copy(
            endTime = 3600L,
            endLevel = 85,
            durationSeconds = 2600L,
            isCompleted = true
        )
        dao.updateSession(completed)

        val updatedActive = dao.getActiveSession()
        assertNull(updatedActive)

        val allSessions = dao.getAllSessions().first()
        assertEquals(1, allSessions.size)
        assertEquals(85, allSessions[0].endLevel)
    }

    @Test
    fun testInsertAndRetrieveEvents() = runBlocking {
        val event = BatteryEventEntity(
            timestamp = System.currentTimeMillis(),
            eventType = "PLUGGED_IN",
            batteryLevel = 45,
            isCharging = true,
            plugType = "AC Adapter",
            temperatureCelsius = 31.4f,
            voltageMilliVolts = 4050,
            batteryHealth = "Good",
            sessionId = 1L
        )

        dao.insertEvent(event)
        val events = dao.getRecentEvents(10).first()
        assertEquals(1, events.size)
        assertEquals("PLUGGED_IN", events[0].eventType)
        assertEquals(45, events[0].batteryLevel)
    }

    @Test
    fun testSessionEventsAndChargeGainCalculation() = runBlocking {
        val session = ChargingSessionEntity(
            startTime = 10_000L,
            endTime = 70_000L,
            startLevel = 25,
            endLevel = 60,
            plugType = "AC Adapter",
            startTemp = 29.0f,
            maxTemp = 36.5f,
            avgTemp = 32.7f,
            durationSeconds = 60L,
            isCompleted = true,
            dateKey = "2026-09-20"
        )
        val sessionId = dao.insertSession(session)

        dao.insertEvent(
            BatteryEventEntity(
                timestamp = 10_000L,
                eventType = "PLUGGED_IN",
                batteryLevel = 25,
                isCharging = true,
                plugType = "AC Adapter",
                temperatureCelsius = 29.0f,
                voltageMilliVolts = 3900,
                batteryHealth = "Good",
                sessionId = sessionId
            )
        )
        dao.insertEvent(
            BatteryEventEntity(
                timestamp = 70_000L,
                eventType = "UNPLUGGED",
                batteryLevel = 60,
                isCharging = false,
                plugType = "Battery",
                temperatureCelsius = 36.5f,
                voltageMilliVolts = 4200,
                batteryHealth = "Good",
                sessionId = sessionId
            )
        )

        val sessionEvents = dao.getEventsForSession(sessionId).first()
        assertEquals(2, sessionEvents.size)
        assertEquals(25, sessionEvents[0].batteryLevel)
        assertEquals(60, sessionEvents[1].batteryLevel)

        val chargeGained = session.endLevel - session.startLevel
        assertEquals(35, chargeGained)
        assertEquals(60L, session.durationSeconds)
    }

    @Test
    fun testSessionDisplayableClutterFilter() {
        // Completed session with 10s duration and 0% change should be filtered from display
        val shortJitterSession = ChargingSessionEntity(
            startTime = 10_000L,
            endTime = 20_000L,
            startLevel = 50,
            endLevel = 50,
            plugType = "AC Adapter",
            startTemp = 30f,
            maxTemp = 30f,
            avgTemp = 30f,
            durationSeconds = 10L,
            isCompleted = true,
            dateKey = "2026-09-20"
        )
        assertFalse(shortJitterSession.isDisplayable)

        // Session with >= 60 seconds duration should be displayable
        val validDurationSession = shortJitterSession.copy(durationSeconds = 60L)
        assertTrue(validDurationSession.isDisplayable)

        // Session with < 60 seconds but >= 1% gain should be displayable
        val validGainSession = shortJitterSession.copy(durationSeconds = 30L, endLevel = 51)
        assertTrue(validGainSession.isDisplayable)

        // Active ongoing session should always be displayable
        val activeOngoingSession = shortJitterSession.copy(isCompleted = false)
        assertTrue(activeOngoingSession.isDisplayable)
    }

    @Test
    fun testConnectionStabilityAnomalyDetection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = object : DeviceSteadinessDetector(context) {
            override suspend fun assessSteadiness(sampleDurationMs: Long): Boolean = true
        }
        val analyzer = ConnectionStabilityAnalyzer(context, detector)

        assertNull(analyzer.activeIssue.value)

        // Simulate 4 rapid disconnects while stationary (threshold is >= 4 within 45s)
        repeat(4) {
            analyzer.onPowerTransition(isConnected = true)
            analyzer.onPowerTransition(isConnected = false)
        }

        // Wait a short moment for analyzer processing
        kotlinx.coroutines.delay(200L)

        val issue = analyzer.activeIssue.value
        assertNotNull(issue)
        assertTrue(issue!!.disconnectCount >= 4)
        assertTrue(issue.isDeviceSteady)

        // Dismiss the alert
        analyzer.dismissActiveIssue()
        assertNull(analyzer.activeIssue.value)
    }
}
