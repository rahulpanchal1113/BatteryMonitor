package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.GoogleDriveBackupManager
import com.example.ui.BatteryViewModel
import com.example.ui.screens.DailyHistoryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class BatteryNavTab(val labelRes: Int, val icon: ImageVector, val tag: String) {
    MONITOR(R.string.tab_dashboard, Icons.Default.BatteryChargingFull, "nav_monitor"),
    HISTORY(R.string.tab_daily, Icons.Default.DateRange, "nav_history"),
    INSIGHTS(R.string.tab_insights, Icons.Default.Insights, "nav_insights"),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BatteryViewModel

    private val powerStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (::viewModel.isInitialized) {
                when (intent?.action) {
                    android.content.Intent.ACTION_POWER_CONNECTED -> {
                        viewModel.onPowerConnected()
                        com.example.widget.BatteryWidgetProvider.updateAllWidgets(this@MainActivity)
                    }
                    android.content.Intent.ACTION_POWER_DISCONNECTED -> {
                        viewModel.onPowerDisconnected()
                        com.example.widget.BatteryWidgetProvider.updateAllWidgets(this@MainActivity)
                    }
                    else -> {
                        viewModel.refreshLiveStatus()
                        com.example.widget.BatteryWidgetProvider.updateAllWidgets(this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BatteryApplication
        val backupManager = GoogleDriveBackupManager(app.database.batteryDao(), this)
        val factory = BatteryViewModel.Factory(app.repository, backupManager)
        viewModel = ViewModelProvider(this, factory)[BatteryViewModel::class.java]

        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_POWER_CONNECTED)
            addAction(android.content.Intent.ACTION_POWER_DISCONNECTED)
            addAction(android.content.Intent.ACTION_BATTERY_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerStateReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(powerStateReceiver, filter)
        }

        startMonitorService()

        setContent {
            val darkThemePref by viewModel.darkTheme.collectAsStateWithLifecycle()
            val isDark = darkThemePref ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = isDark) {
                BatteryAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startMonitorService()
        if (::viewModel.isInitialized) {
            viewModel.refreshLiveStatus()
        }
    }

    private fun startMonitorService() {
        try {
            val serviceIntent = android.content.Intent(this, com.example.service.BatteryMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to start BatteryMonitorService", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun BatteryAppContent(viewModel: BatteryViewModel) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = BatteryNavTab.entries

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main_navigation_bar")) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTabIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabTransition",
            modifier = Modifier.padding(innerPadding)
        ) { targetIndex ->
            when (tabs[targetIndex]) {
                BatteryNavTab.MONITOR -> DashboardScreen(viewModel = viewModel)
                BatteryNavTab.HISTORY -> DailyHistoryScreen(viewModel = viewModel)
                BatteryNavTab.INSIGHTS -> InsightsScreen(viewModel = viewModel)
                BatteryNavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
