package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ChargingSessionEntity
import com.example.data.model.DailyBatteryStats
import com.example.ui.BatteryViewModel
import com.example.ui.components.ChargingSessionDetailSheet
import com.example.ui.components.DailyChargeChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyHistoryScreen(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val availableDates by viewModel.availableDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionsForSelectedDate.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    var selectedSessionForDetail by remember { mutableStateOf<ChargingSessionEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Daily Performance History",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Analyze charging events and battery cycles day-by-day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // Horizontal Date Chips Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("date_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableDates.forEach { dateKey ->
                    val isSelected = dateKey == selectedDate
                    val label = when (dateKey) {
                        viewModel.todayKey -> "Today"
                        else -> formatDateLabel(dateKey)
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectDate(dateKey) },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        item {
            // Daily Summary Cards
            DailyMetricsSummaryCard(stats = dailyStats, useFahrenheit = useFahrenheit)
        }

        item {
            // Daily Chart & Timeline
            DailyChargeChart(
                sessions = sessions,
                stats = dailyStats,
                onSessionClick = { session ->
                    selectedSessionForDetail = session
                }
            )
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
fun DailyMetricsSummaryCard(
    stats: DailyBatteryStats?,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val durationMinutes = (stats?.totalChargeDurationSeconds ?: 0L) / 60
    val hours = durationMinutes / 60
    val remainingMins = durationMinutes % 60
    val durationStr = if (hours > 0) "${hours}h ${remainingMins}m" else "${remainingMins}m"

    val tempStr = if (stats != null && stats.avgTemperature > 0f) {
        if (useFahrenheit) {
            String.format(Locale.US, "%.1f°F", (stats.avgTemperature * 9f / 5f) + 32f)
        } else {
            String.format(Locale.US, "%.1f°C", stats.avgTemperature)
        }
    } else "—"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_metrics_summary_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Day Overview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatItem(
                    label = "Charge Time",
                    value = durationStr,
                    icon = Icons.Default.Schedule,
                    tint = MaterialTheme.colorScheme.primary
                )

                SummaryStatItem(
                    label = "Gained",
                    value = "+${stats?.totalPercentGained ?: 0}%",
                    icon = Icons.Default.ElectricBolt,
                    tint = Color(0xFF22C55E)
                )

                SummaryStatItem(
                    label = "Avg Temp",
                    value = tempStr,
                    icon = Icons.Default.Thermostat,
                    tint = Color(0xFFF97316)
                )

                SummaryStatItem(
                    label = "Sessions",
                    value = "${stats?.sessionsCount ?: 0}",
                    icon = Icons.Default.ElectricBolt,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun SummaryStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatDateLabel(dateKey: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        val date = parser.parse(dateKey)
        if (date != null) formatter.format(date) else dateKey
    } catch (_: Exception) {
        dateKey
    }
}
