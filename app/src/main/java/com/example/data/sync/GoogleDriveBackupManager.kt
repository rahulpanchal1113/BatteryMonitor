package com.example.data.sync

import android.content.Context
import com.example.data.local.BatteryDao
import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleDriveBackupManager(
    private val dao: BatteryDao,
    private val context: Context
) {

    /**
     * Exports full local Room database (sessions and events) to JSON stream.
     * Compatible with Google Drive storage files and local storage.
     */
    suspend fun exportToJsonStream(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sessions = dao.getAllSessions().first()
            val events = dao.getRecentEvents(500).first()

            val rootJson = JSONObject()
            rootJson.put("version", 1)
            rootJson.put("appName", "Battery Monitor")
            rootJson.put("exportTimestamp", System.currentTimeMillis())
            rootJson.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

            val sessionsArray = JSONArray()
            for (s in sessions) {
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("startTime", s.startTime)
                    put("endTime", s.endTime ?: JSONObject.NULL)
                    put("startLevel", s.startLevel)
                    put("endLevel", s.endLevel)
                    put("plugType", s.plugType)
                    put("startTemp", s.startTemp)
                    put("maxTemp", s.maxTemp)
                    put("avgTemp", s.avgTemp)
                    put("durationSeconds", s.durationSeconds)
                    put("peakSpeedPercentPerHour", s.peakSpeedPercentPerHour)
                    put("isCompleted", s.isCompleted)
                    put("dateKey", s.dateKey)
                }
                sessionsArray.put(sObj)
            }
            rootJson.put("sessions", sessionsArray)

            val eventsArray = JSONArray()
            for (e in events) {
                val eObj = JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("eventType", e.eventType)
                    put("batteryLevel", e.batteryLevel)
                    put("isCharging", e.isCharging)
                    put("plugType", e.plugType)
                    put("temperatureCelsius", e.temperatureCelsius)
                    put("voltageMilliVolts", e.voltageMilliVolts)
                    put("batteryHealth", e.batteryHealth)
                    put("sessionId", e.sessionId ?: JSONObject.NULL)
                }
                eventsArray.put(eObj)
            }
            rootJson.put("events", eventsArray)

            val jsonBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
            outputStream.write(jsonBytes)
            outputStream.flush()
            Result.success(sessions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports sessions and events from a JSON stream (Google Drive file or local backup).
     */
    suspend fun importFromJsonStream(inputStream: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonText = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootJson = JSONObject(jsonText)

            val sessionsArray = rootJson.optJSONArray("sessions") ?: JSONArray()
            var importedSessionsCount = 0
            for (i in 0 until sessionsArray.length()) {
                val sObj = sessionsArray.getJSONObject(i)
                val session = ChargingSessionEntity(
                    startTime = sObj.getLong("startTime"),
                    endTime = if (sObj.isNull("endTime")) null else sObj.getLong("endTime"),
                    startLevel = sObj.getInt("startLevel"),
                    endLevel = sObj.getInt("endLevel"),
                    plugType = sObj.getString("plugType"),
                    startTemp = sObj.getDouble("startTemp").toFloat(),
                    maxTemp = sObj.getDouble("maxTemp").toFloat(),
                    avgTemp = sObj.getDouble("avgTemp").toFloat(),
                    durationSeconds = sObj.getLong("durationSeconds"),
                    peakSpeedPercentPerHour = sObj.getDouble("peakSpeedPercentPerHour").toFloat(),
                    isCompleted = sObj.getBoolean("isCompleted"),
                    dateKey = sObj.getString("dateKey")
                )
                dao.insertSession(session)
                importedSessionsCount++
            }

            val eventsArray = rootJson.optJSONArray("events") ?: JSONArray()
            for (i in 0 until eventsArray.length()) {
                val eObj = eventsArray.getJSONObject(i)
                val event = BatteryEventEntity(
                    timestamp = eObj.getLong("timestamp"),
                    eventType = eObj.getString("eventType"),
                    batteryLevel = eObj.getInt("batteryLevel"),
                    isCharging = eObj.getBoolean("isCharging"),
                    plugType = eObj.getString("plugType"),
                    temperatureCelsius = eObj.getDouble("temperatureCelsius").toFloat(),
                    voltageMilliVolts = eObj.getInt("voltageMilliVolts"),
                    batteryHealth = eObj.getString("batteryHealth"),
                    sessionId = if (eObj.isNull("sessionId")) null else eObj.getLong("sessionId")
                )
                dao.insertEvent(event)
            }

            Result.success(importedSessionsCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
