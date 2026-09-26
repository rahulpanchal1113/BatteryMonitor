package com.example.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import com.example.data.local.DischargingSessionEntity
import com.example.data.model.AppBackgroundBatteryUsage
import com.example.data.model.AppDischargeConsumption
import com.example.data.model.BatteryHealthInfo
import com.example.data.model.BatteryStatus
import com.example.data.model.ChargingInsightSummary
import com.example.data.model.ConnectionDiagnosticIssue
import com.example.data.model.DailyBatteryStats
import com.example.data.model.DailyDischargeStats
import com.example.data.repository.BatteryRepository
import com.example.data.sync.GoogleDriveBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryTab {
    CHARGING,
    DISCHARGING
}

class BatteryViewModel(
    private val repository: BatteryRepository,
    private val backupManager: GoogleDriveBackupManager
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayKey: String = dateFormat.format(Date())

    val liveStatus: StateFlow<BatteryStatus> = repository.liveBatteryStatus
    val batteryHealthInfo: StateFlow<BatteryHealthInfo> = repository.batteryHealthInfo

    val allSessions: StateFlow<List<ChargingSessionEntity>> = repository.displaySessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDiagnosticIssue: StateFlow<ConnectionDiagnosticIssue?> = repository.activeDiagnosticIssue
    val filteredJitterCount: StateFlow<Int> = repository.filteredJitterCount

    val recentEvents: StateFlow<List<BatteryEventEntity>> = repository.recentEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableDates: StateFlow<List<String>> = repository.availableDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(todayKey))

    private val _selectedDate = MutableStateFlow(todayKey)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedHistoryTab = MutableStateFlow(HistoryTab.CHARGING)
    val selectedHistoryTab: StateFlow<HistoryTab> = _selectedHistoryTab.asStateFlow()

    fun setHistoryTab(tab: HistoryTab) {
        _selectedHistoryTab.value = tab
    }

    val sessionsForSelectedDate: StateFlow<List<ChargingSessionEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getSessionsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dischargeSessionsForSelectedDate: StateFlow<List<DischargingSessionEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getDischargeSessionsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dailyStats = MutableStateFlow<DailyBatteryStats?>(null)
    val dailyStats: StateFlow<DailyBatteryStats?> = _dailyStats.asStateFlow()

    private val _dailyDischargeStats = MutableStateFlow<DailyDischargeStats?>(null)
    val dailyDischargeStats: StateFlow<DailyDischargeStats?> = _dailyDischargeStats.asStateFlow()

    private val _insights = MutableStateFlow<ChargingInsightSummary?>(null)
    val insights: StateFlow<ChargingInsightSummary?> = _insights.asStateFlow()

    private val _useFahrenheit = MutableStateFlow(repository.isFahrenheit())
    val useFahrenheit: StateFlow<Boolean> = _useFahrenheit.asStateFlow()

    private val _adapterRatingWatts = MutableStateFlow(repository.getAdapterRatingWatts())
    val adapterRatingWatts: StateFlow<Int> = _adapterRatingWatts.asStateFlow()

    val appBackgroundBatteryUsage: StateFlow<AppBackgroundBatteryUsage> = repository.appBackgroundUsage

    private val _darkTheme = MutableStateFlow<Boolean?>(null) // null = system
    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()

    init {
        refreshInsights()
        loadDailyStats(todayKey)
        loadDailyDischargeStats(todayKey)

        // Whenever sessions change, update stats and insights
        viewModelScope.launch {
            repository.allSessions.collect {
                loadDailyStats(_selectedDate.value)
                refreshInsights()
            }
        }

        viewModelScope.launch {
            repository.allDischargeSessions.collect {
                loadDailyDischargeStats(_selectedDate.value)
            }
        }

        // Periodically refresh real-time wattage and battery metrics while app is active
        viewModelScope.launch {
            while (true) {
                repository.updateLiveStatus()
                kotlinx.coroutines.delay(2000L)
            }
        }
    }

    fun refreshLiveStatus() {
        repository.updateLiveStatus()
    }

    fun onPowerConnected() {
        viewModelScope.launch {
            repository.onPowerConnected()
            repository.updateLiveStatus()
            refreshInsights()
        }
    }

    fun onPowerDisconnected() {
        viewModelScope.launch {
            repository.onPowerDisconnected()
            repository.updateLiveStatus()
            refreshInsights()
        }
    }

    fun getEventsForSession(sessionId: Long): kotlinx.coroutines.flow.Flow<List<BatteryEventEntity>> {
        return repository.getEventsForSession(sessionId)
    }

    fun getEventsForTimeRange(startTime: Long, endTime: Long?): kotlinx.coroutines.flow.Flow<List<BatteryEventEntity>> {
        return repository.getEventsForTimeRange(startTime, endTime)
    }

    suspend fun getTopAppsForDischarge(session: DischargingSessionEntity): List<AppDischargeConsumption> {
        return repository.getTopAppsForDischargeSession(session)
    }

    fun selectDate(dateKey: String) {
        _selectedDate.value = dateKey
        loadDailyStats(dateKey)
        loadDailyDischargeStats(dateKey)
    }

    fun toggleTempUnit() {
        val nextVal = !_useFahrenheit.value
        _useFahrenheit.value = nextVal
        repository.setFahrenheit(nextVal)
    }

    fun setAdapterRatingWatts(watts: Int) {
        _adapterRatingWatts.value = watts
        repository.setAdapterRatingWatts(watts)
    }

    fun setDarkTheme(isDark: Boolean?) {
        _darkTheme.value = isDark
    }

    fun dismissDiagnosticIssue() {
        repository.dismissDiagnosticIssue()
    }

    fun refreshInsights() {
        viewModelScope.launch {
            _insights.value = repository.getInsightsSummary()
        }
    }

    private fun loadDailyStats(dateKey: String) {
        viewModelScope.launch {
            _dailyStats.value = repository.getDailyStats(dateKey)
        }
    }

    private fun loadDailyDischargeStats(dateKey: String) {
        viewModelScope.launch {
            _dailyDischargeStats.value = repository.getDailyDischargeStats(dateKey)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            loadDailyStats(_selectedDate.value)
            loadDailyDischargeStats(_selectedDate.value)
            refreshInsights()
        }
    }

    fun exportDataToUri(contentResolver: ContentResolver, uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        backupManager.exportToJsonStream(stream)
                    } ?: Result.failure(IOException("Failed to open destination stream"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            onResult(result)
        }
    }

    fun importDataFromUri(contentResolver: ContentResolver, uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        backupManager.importFromJsonStream(stream)
                    } ?: Result.failure(IOException("Failed to open source stream"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                loadDailyStats(_selectedDate.value)
                refreshInsights()
            }
            onResult(result)
        }
    }

    fun exportData(outputStream: OutputStream, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.exportToJsonStream(outputStream)
            }
            onResult(result)
        }
    }

    fun importData(inputStream: InputStream, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.importFromJsonStream(inputStream)
            }
            if (result.isSuccess) {
                loadDailyStats(_selectedDate.value)
                refreshInsights()
            }
            onResult(result)
        }
    }

    class Factory(
        private val repository: BatteryRepository,
        private val backupManager: GoogleDriveBackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BatteryViewModel(repository, backupManager) as T
        }
    }
}
