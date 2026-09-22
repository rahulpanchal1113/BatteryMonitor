package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BatteryDao
import com.example.data.local.BatteryDatabase
import com.example.data.local.ChargingSessionEntity
import com.example.data.sync.GoogleDriveBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveBackupTest {

    private lateinit var database: BatteryDatabase
    private lateinit var dao: BatteryDao
    private lateinit var backupManager: GoogleDriveBackupManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BatteryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.batteryDao()
        backupManager = GoogleDriveBackupManager(dao, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testExportAndImportJsonBackupRoundtrip() = runBlocking {
        // Insert sample session
        val session = ChargingSessionEntity(
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            startLevel = 20,
            endLevel = 80,
            plugType = "AC Adapter",
            startTemp = 29.0f,
            maxTemp = 36.0f,
            avgTemp = 33.0f,
            durationSeconds = 3600L,
            peakSpeedPercentPerHour = 60.0f,
            isCompleted = true,
            dateKey = "2026-09-20"
        )
        dao.insertSession(session)

        // Export to stream
        val outStream = ByteArrayOutputStream()
        val exportResult = backupManager.exportToJsonStream(outStream)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrNull())

        val exportedBytes = outStream.toByteArray()
        assertTrue(exportedBytes.isNotEmpty())

        // Clear database
        dao.clearSessions()
        val emptySessions = dao.getAllSessions().first()
        assertEquals(0, emptySessions.size)

        // Import back from stream
        val inStream = ByteArrayInputStream(exportedBytes)
        val importResult = backupManager.importFromJsonStream(inStream)
        assertTrue(importResult.isSuccess)
        assertEquals(1, importResult.getOrNull())

        val restoredSessions = dao.getAllSessions().first()
        assertEquals(1, restoredSessions.size)
        assertEquals(20, restoredSessions[0].startLevel)
        assertEquals(80, restoredSessions[0].endLevel)
    }
}
