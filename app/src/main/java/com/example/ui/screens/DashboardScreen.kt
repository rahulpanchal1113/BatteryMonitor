package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.BatteryEventEntity
import kotlin.math.max
import com.example.data.local.ChargingSessionEntity
import com.example.ui.BatteryViewModel
import com.example.ui.components.BatteryMetricGrid
import com.example.ui.components.ChargingSessionCard
import com.example.ui.components.ChargingSessionDetailSheet
import com.example.ui.components.ConnectionDiagnosticAlertCard
import com.example.ui.components.LiveBatteryGauge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val liveStatus by viewModel.liveStatus.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val activeDiagnosticIssue by viewModel.activeDiagnosticIssue.collectAsStateWithLifecycle()
    val filteredJitterCount by viewModel.filteredJitterCount.collectAsStateWithLifecycle()
    var selectedSessionForDetail by remember { mutableStateOf<ChargingSessionEntity?>(null) }

    val todayDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val deduplicatedSessions = remember(allSessions, liveStatus.isCharging) {
        var activeEncountered = false
        allSessions.mapNotNull { session ->
            if (!session.isCompleted) {
                if (!liveStatus.isCharging) {
                    session.copy(
                        isCompleted = true,
                        endTime = session.endTime ?: (session.startTime + max(1L, session.durationSeconds) * 1000L)
                    )
                } else if (!activeEncountered) {
                    activeEncountered = true
                    session
                } else {
                    session.copy(
                        isCompleted = true,
                        endTime = session.endTime ?: (session.startTime + max(1L, session.durationSeconds) * 1000L)
                    )
                }
            } else {
                session
            }
        }
    }
    val todayCyclesCount = remember(deduplicatedSessions, todayDateKey) {
        deduplicatedSessions.count { it.dateKey == todayDateKey }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real-time Battery Performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshLiveStatus() },
                    modifier = Modifier.testTag("refresh_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Hardware / Cable Issue Alert (if device was steady but frequent disconnects occurred)
        if (activeDiagnosticIssue != null) {
            item {
                ConnectionDiagnosticAlertCard(
                    issue = activeDiagnosticIssue!!,
                    filteredJitterCount = filteredJitterCount,
                    onDismiss = { viewModel.dismissDiagnosticIssue() }
                )
            }
        }

        item {
            // Live Circular Battery Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LiveBatteryGauge(status = liveStatus)
            }
        }

        item {
            // Live Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_status_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (liveStatus.isCharging) {
                        Color(0xFF22C55E).copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (liveStatus.isCharging) Color(0xFF22C55E) else MaterialTheme.colorScheme.secondary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (liveStatus.isCharging) Icons.Default.Bolt else Icons.Default.PowerOff,
                            contentDescription = "Status",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (liveStatus.isCharging) {
                                if (liveStatus.grossWattageWatts > 0f) {
                                    "Connected to ${liveStatus.plugType} (${String.format(Locale.US, "%.1f W Gross", liveStatus.grossWattageWatts)})"
                                } else {
                                    "Connected to ${liveStatus.plugType}"
                                }
                            } else {
                                "Running on Battery Power"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (liveStatus.isCharging) {
                                if (liveStatus.netWattageWatts > 0f) {
                                    "Net battery intake: ${String.format(Locale.US, "%.1f W", liveStatus.netWattageWatts)} • ${liveStatus.chargingSpeedType}"
                                } else {
                                    "Recording charge speed & temperature events"
                                }
                            } else {
                                "Plug in an adapter to track charging curve"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Key Metrics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            BatteryMetricGrid(
                status = liveStatus,
                useFahrenheit = useFahrenheit,
                onToggleTempUnit = { viewModel.toggleTempUnit() }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Power Events",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$todayCyclesCount ${if (todayCyclesCount == 1) "session" else "sessions"} today",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (deduplicatedSessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_sessions_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No charging events recorded yet. Connect your adapter to record a charge cycle!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(deduplicatedSessions.take(5), key = { it.id }) { session ->
                ChargingSessionCard(
                    session = session,
                    useFahrenheit = useFahrenheit,
                    onClick = {
                        selectedSessionForDetail = session
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (selectedSessionForDetail != null) {
        ChargingSessionDetailSheet(
            session = selectedSessionForDetail!!,
            eventsFlow = viewModel.getEventsForSession(selectedSessionForDetail!!.id),
            useFahrenheit = useFahrenheit,
            onDismiss = { selectedSessionForDetail = null }
        )
    }
}

@Composable
fun EventListItem(
    event: BatteryEventEntity,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(event.timestamp))

    val isPlugIn = event.eventType == "PLUGGED_IN"
    val isUnplug = event.eventType == "UNPLUGGED"

    val icon = when {
        isPlugIn -> Icons.Default.Bolt
        isUnplug -> Icons.Default.PowerOff
        else -> Icons.Default.Bolt
    }

    val iconTint = when {
        isPlugIn -> Color(0xFF22C55E)
        isUnplug -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.primary
    }

    val tempStr = if (useFahrenheit) {
        String.format(Locale.US, "%.1f°F", (event.temperatureCelsius * 9f / 5f) + 32f)
    } else {
        String.format(Locale.US, "%.1f°C", event.temperatureCelsius)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = event.eventType,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = when (event.eventType) {
                            "PLUGGED_IN" -> "Adapter Connected"
                            "UNPLUGGED" -> "Adapter Disconnected"
                            else -> "Battery Sample"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${event.batteryLevel}%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$tempStr • ${event.voltageMilliVolts}mV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
