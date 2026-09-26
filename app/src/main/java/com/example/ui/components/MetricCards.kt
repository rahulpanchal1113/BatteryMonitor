package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BatteryHealthInfo
import com.example.data.model.BatteryStatus
import java.util.Locale

@Composable
fun BatteryMetricGrid(
    status: BatteryStatus,
    healthInfo: BatteryHealthInfo? = null,
    useFahrenheit: Boolean,
    onToggleTempUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPowerBreakdown by remember { mutableStateOf(false) }
    var showHealthDetailDialog by remember { mutableStateOf(false) }

    val tempDisplay = if (useFahrenheit) {
        String.format(Locale.US, "%.1f°F", status.tempFahrenheit)
    } else {
        String.format(Locale.US, "%.1f°C", status.tempCelsius)
    }

    val tempColor = when {
        status.tempCelsius >= 40f -> Color(0xFFEF4444) // Hot
        status.tempCelsius >= 37.5f -> Color(0xFFF59E0B) // Warm
        else -> Color(0xFF10B981) // Normal / Cool
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_temperature),
                value = tempDisplay,
                subtitle = if (status.tempCelsius >= 38f) "High Temp • Tap unit" else "Optimal • Tap unit",
                icon = Icons.Default.Thermostat,
                iconColor = tempColor,
                onClick = onToggleTempUnit,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_temperature_card")
            )

            MetricCard(
                title = stringResource(R.string.metric_voltage),
                value = String.format(Locale.US, "%.3f V", status.voltageVolts),
                subtitle = "${status.voltageMilliVolts} mV",
                icon = Icons.Default.ElectricBolt,
                iconColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_voltage_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isCalibrated = healthInfo?.isCalibrated == true && healthInfo.healthPercentage != null
            val healthPercent = healthInfo?.healthPercentage ?: status.healthPercentage
            val designCap = healthInfo?.designCapacityMah ?: status.designCapacityMah
            val estCap = healthInfo?.estimatedCapacityMah ?: status.estimatedCapacityMah

            val healthValue = if (isCalibrated && healthPercent != null) {
                "$healthPercent%"
            } else {
                "Calibrating..."
            }

            val healthSubtitle = if (isCalibrated && estCap != null) {
                "$estCap / $designCap mAh"
            } else {
                val progress = healthInfo?.progressPercent ?: 0
                if (progress > 0) "Collecting data ($progress%)" else "Need 1–2 charge cycles"
            }

            val healthColor = if (isCalibrated && healthPercent != null) {
                when {
                    healthPercent >= 90 -> Color(0xFF22C55E)
                    healthPercent >= 80 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
            } else {
                Color(0xFF38BDF8) // Sky blue for calibrating state
            }

            MetricCard(
                title = stringResource(R.string.metric_health),
                value = healthValue,
                subtitle = healthSubtitle,
                icon = Icons.Default.HealthAndSafety,
                iconColor = healthColor,
                onClick = { showHealthDetailDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_health_card")
            )

            MetricCard(
                title = stringResource(R.string.metric_power_source),
                value = status.plugType,
                subtitle = if (status.isCharging) {
                    if (status.chargingSpeedType.isNotEmpty() && !status.chargingSpeedType.equals(status.plugType, ignoreCase = true)) {
                        status.chargingSpeedType
                    } else {
                        "Active Connection"
                    }
                } else {
                    "Unplugged"
                },
                icon = Icons.Default.Power,
                iconColor = if (status.isCharging) Color(0xFF22C55E) else MaterialTheme.colorScheme.secondary,
                onClick = { showPowerBreakdown = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_power_source_card")
            )
        }

        // Full-width 2-column spanning Live Power Flow card
        LiveChargingPowerFlowCard(
            status = status,
            onOpenDetails = { showPowerBreakdown = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showPowerBreakdown && status.isCharging) {
        AlertDialog(
            onDismissRequest = { showPowerBreakdown = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Charging Power Flow", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Wall Charger Power
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Wall Charger Power",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f W", status.grossWattageWatts),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF15803D)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Total electrical power supplied from your wall outlet through the charging cable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Net battery intake & Active device load
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Battery Intake",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f W", status.netWattageWatts),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Power filling the battery",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Phone Usage",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "~%.1f W", status.activeDeviceDrawWatts),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "Screen & open apps",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Explanatory note
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Why does charging speed change? When your screen is ON, fast chargers automatically reduce battery intake power to keep your phone cool. Once you turn the screen off, full fast charging resumes.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPowerBreakdown = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showHealthDetailDialog) {
        val isCalibrated = healthInfo?.isCalibrated == true && healthInfo.healthPercentage != null
        val healthPercent = healthInfo?.healthPercentage ?: status.healthPercentage
        val designCap = healthInfo?.designCapacityMah ?: status.designCapacityMah
        val estCap = healthInfo?.estimatedCapacityMah ?: status.estimatedCapacityMah
        val condition = healthInfo?.conditionLabel ?: if (isCalibrated) "Good" else "Calibrating"
        val cycles = healthInfo?.totalCyclesCount ?: 0f
        val sessionsAnalyzed = healthInfo?.totalSessionsAnalyzed ?: 0
        val minRequired = healthInfo?.minSessionsRequired ?: 2
        val progress = healthInfo?.progressPercent ?: 0
        val avgTempCelsius = healthInfo?.avgOperatingTempCelsius ?: status.tempCelsius
        val avgTempStr = if (useFahrenheit) {
            String.format(Locale.US, "%.1f°F", (avgTempCelsius * 9f / 5f) + 32f)
        } else {
            String.format(Locale.US, "%.1f°C", avgTempCelsius)
        }

        val healthColor = if (isCalibrated && healthPercent != null) {
            when {
                healthPercent >= 90 -> Color(0xFF22C55E)
                healthPercent >= 80 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
        } else {
            Color(0xFF38BDF8)
        }

        AlertDialog(
            onDismissRequest = { showHealthDetailDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = healthColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Battery Health Diagnostics", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isCalibrated || healthPercent == null) {
                        // Calibrating Progress Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Calibration Status",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Collecting Data ($progress%)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0284C7))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Learning",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { (progress / 100f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF0284C7),
                                    trackColor = Color(0xFF0284C7).copy(alpha = 0.2f)
                                )
                            }
                        }

                        // Info Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Factory Rated Capacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$designCap mAh (When New)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Valid Cycles Recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$sessionsAnalyzed / $minRequired cycles", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Avg Operating Temp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(avgTempStr, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Helpful Instructions
                        Text(
                            text = "To measure your battery's true degraded health accurately relative to its original factory capacity, Battery Monitor analyzes live energy intake and discharge flow.\n\nCharge your device (e.g. from <20% to >80%) and use it normally on battery. Your absolute health percentage and usable mAh capacity will appear here automatically once 1–2 cycles are recorded.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Calibrated State: Large Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(healthColor.copy(alpha = 0.12f))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Absolute Health",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$healthPercent%",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = healthColor
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(healthColor)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = condition,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                            }
                        }

                        // Key Specs Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Current Usable Capacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${estCap ?: (designCap * healthPercent / 100)} mAh", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Factory Rated Capacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$designCap mAh (When New)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Capacity Degradation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val usable = estCap ?: (designCap * healthPercent / 100)
                                    val lostMah = kotlin.math.max(0, designCap - usable)
                                    Text("-$lostMah mAh (${100 - healthPercent}%)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = if (lostMah > 300) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Equivalent Cycles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "%.1f cycles", cycles), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Avg Operating Temp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(avgTempStr, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Explanation Note
                        Text(
                            text = "Calculated continuously from your device's actual charging energy intake and on-battery discharge cycles relative to original factory specifications.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHealthDetailDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun LiveChargingPowerFlowCard(
    status: BatteryStatus,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(18.dp)
    Card(
        modifier = modifier
            .clip(cardShape)
            .testTag("charging_power_flow_card"),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        if (status.isCharging) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Power,
                                contentDescription = "Power Flow",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Live Charging Power Flow",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = onOpenDetails,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("power_flow_info_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Power Details",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "How charger power is shared with battery & screen",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Column layout for Wall Charger and Battery Intake
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Wall Charger Power
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "WALL CHARGER POWER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color(0xFF15803D)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sourceDescriptor = if (status.chargingSpeedType.isNotEmpty() && !status.chargingSpeedType.equals(status.plugType, ignoreCase = true)) {
                                    "${status.plugType} • ${status.chargingSpeedType}"
                                } else {
                                    "${status.plugType} • Cable"
                                }
                                Text(
                                    text = sourceDescriptor,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF166534)
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.wrapContentWidth(Alignment.End)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f W", status.grossWattageWatts),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = Color(0xFF15803D),
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "Supplied Power",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF166534),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    // 2. Battery Intake Power
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BATTERY INTAKE POWER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${status.currentMilliAmps} mA flow • ${String.format(Locale.US, "%.2f V", status.voltageMilliVolts / 1000f)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.wrapContentWidth(Alignment.End)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f W", status.netWattageWatts),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "Filling Battery",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                // Device load info footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone uses ~${String.format(Locale.US, "%.1f W", status.activeDeviceDrawWatts)} (screen & apps). The remaining ~${String.format(Locale.US, "%.1f W", status.netWattageWatts)} charges the battery.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Standby / Unplugged State: sleek, compact, no empty space, no non-functional 'i' button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = "Standby Power",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Power Consumption & Standby",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val dischargeText = if (status.currentMilliAmps != 0) {
                            "Discharging at ${kotlin.math.abs(status.currentMilliAmps)} mA (~${String.format(Locale.US, "%.1f W", status.voltageVolts * kotlin.math.abs(status.currentMilliAmps) / 1000f)})"
                        } else {
                            "Minimal standby draw"
                        }
                        Text(
                            text = "$dischargeText • Running on battery",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Standby",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
