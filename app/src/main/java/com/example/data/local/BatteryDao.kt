package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    // --- Events ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BatteryEventEntity): Long

    @Query("SELECT * FROM battery_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<BatteryEventEntity>>

    @Query("SELECT * FROM battery_events WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getEventsSince(sinceTimestamp: Long): List<BatteryEventEntity>

    @Query("SELECT * FROM battery_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsForSession(sessionId: Long): Flow<List<BatteryEventEntity>>

    @Query("SELECT * FROM battery_events WHERE isCharging = 1 AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getChargingSamplesSince(sinceTimestamp: Long): List<BatteryEventEntity>

    @Query("SELECT * FROM battery_events WHERE eventType = 'UNPLUGGED' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnpluggedEvent(): BatteryEventEntity?

    // --- Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSessionEntity?

    @Query("UPDATE charging_sessions SET isCompleted = 1, endTime = :now WHERE isCompleted = 0")
    suspend fun closeAllActiveSessions(now: Long)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE dateKey = :dateKey ORDER BY startTime DESC")
    fun getSessionsForDate(dateKey: String): Flow<List<ChargingSessionEntity>>

    @Query("SELECT DISTINCT dateKey FROM charging_sessions ORDER BY dateKey DESC")
    fun getAvailableDates(): Flow<List<String>>

    @Query("SELECT * FROM charging_sessions WHERE isCompleted = 1 ORDER BY startTime DESC LIMIT :limit")
    suspend fun getCompletedSessionsList(limit: Int = 50): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE isCompleted = 1 AND (durationSeconds >= 120 OR endLevel > startLevel) ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestValidCompletedSession(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): ChargingSessionEntity?

    @Query("SELECT COUNT(*) FROM charging_sessions")
    suspend fun getSessionsCount(): Int

    @Query("UPDATE charging_sessions SET plugType = 'Wall Charger' WHERE plugType = 'Battery' OR plugType = ''")
    suspend fun fixBatteryPlugTypeSessions()

    @Query("UPDATE charging_sessions SET endLevel = startLevel WHERE endLevel < startLevel")
    suspend fun fixNegativeEndLevelSessions()

    @Query("SELECT * FROM battery_events WHERE timestamp >= :startTime AND (:endTime IS NULL OR timestamp <= :endTime) ORDER BY timestamp ASC")
    fun getEventsInTimeRange(startTime: Long, endTime: Long?): Flow<List<BatteryEventEntity>>

    // --- Discharging Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDischargeSession(session: DischargingSessionEntity): Long

    @Update
    suspend fun updateDischargeSession(session: DischargingSessionEntity)

    @Query("SELECT * FROM discharging_sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveDischargeSession(): DischargingSessionEntity?

    @Query("UPDATE discharging_sessions SET isCompleted = 1, endTime = :now WHERE isCompleted = 0")
    suspend fun closeAllActiveDischargeSessions(now: Long)

    @Query("SELECT * FROM discharging_sessions ORDER BY startTime DESC")
    fun getAllDischargeSessions(): Flow<List<DischargingSessionEntity>>

    @Query("SELECT * FROM discharging_sessions WHERE dateKey = :dateKey ORDER BY startTime DESC")
    fun getDischargeSessionsForDate(dateKey: String): Flow<List<DischargingSessionEntity>>

    @Query("SELECT DISTINCT dateKey FROM discharging_sessions ORDER BY dateKey DESC")
    fun getDischargeAvailableDates(): Flow<List<String>>

    @Query("SELECT * FROM discharging_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestDischargeSession(): DischargingSessionEntity?

    @Query("DELETE FROM battery_events")
    suspend fun clearEvents()

    @Query("DELETE FROM charging_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM discharging_sessions")
    suspend fun clearDischargeSessions()
}
