package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChargeBracketInsight
import com.example.data.model.ChargingInsightSummary
import kotlin.math.max

enum class SpeedInsightMode {
    MINUTES_PER_STAGE,
    PERCENT_PER_HOUR
}

@Composable
fun ChargeSpeedInsightChart(
    insights: ChargingInsightSummary,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(SpeedInsightMode.MINUTES_PER_STAGE) }
    var showAnalogyExplainer by remember { mutableStateOf(false) }

    val brackets = insights.brackets.ifEmpty {
        listOf(
            ChargeBracketInsight("0% - 20%", 0, 20, 0f, 0f, 0),
            ChargeBracketInsight("20% - 40%", 20, 40, 0f, 0f, 0),
            ChargeBracketInsight("40% - 60%", 40, 60, 0f, 0f, 0),
            ChargeBracketInsight("60% - 80%", 60, 80, 0f, 0f, 0),
            ChargeBracketInsight("80% - 100%", 80, 100, 0f, 0f, 0)
        )
    }

    // Baseline calculations for duration (minutes) and rate (%/hr)
    val segmentDurations = brackets.map { bracket ->
        if (bracket.averageRatePercentPerHour > 0f) {
            bracket.estimatedMinutesForBracket
        } else {
            when (bracket.startPercent) {
                0 -> 12
                20 -> 14
                40 -> 18
                60 -> 25
                else -> 42
            }
        }
    }
    val maxDurationMinutes = max(45, segmentDurations.maxOrNull() ?: 45)

    val segmentRates = brackets.map { bracket ->
        if (bracket.averageRatePercentPerHour > 0f) {
            bracket.averageRatePercentPerHour
        } else {
            when (bracket.startPercent) {
                0 -> 100f
                20 -> 85f
                40 -> 67f
                60 -> 48f
                else -> 28f
            }
        }
    }
    val maxSpeedRate = max(100f, segmentRates.maxOrNull() ?: 100f)

    // Sort stages by duration (least time = fastest, most time = slowest)
    val sortedByDurationIndices = segmentDurations.indices.sortedBy { segmentDurations[it] }
    val fastestIndex = insights.peakBracket?.let { peak ->
        brackets.indexOfFirst { it.startPercent == peak.startPercent }.takeIf { it >= 0 }
    } ?: sortedByDurationIndices.firstOrNull() ?: 0
    val slowestIndex = sortedByDurationIndices.lastOrNull() ?: 4

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("charge_speed_insight_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Charging Speed by Stage",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
                            "Time taken for each 20% segment of your battery"
                        } else {
                            "Charging speed rate (%/hr) for each segment"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Toggle Tabs: Time vs Speed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(3.dp)
            ) {
                // Time Mode Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { mode = SpeedInsightMode.MINUTES_PER_STAGE }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Time (Minutes)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Speed Mode Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (mode == SpeedInsightMode.PERCENT_PER_HOUR) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { mode = SpeedInsightMode.PERCENT_PER_HOUR }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (mode == SpeedInsightMode.PERCENT_PER_HOUR) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Speed (%/hr)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (mode == SpeedInsightMode.PERCENT_PER_HOUR) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Five Segments Horizontal Bar Chart: 0-20%, 20-40%, 40-60%, 60-80%, 80-100%
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                brackets.forEachIndexed { index, bracket ->
                    val isPeak = (index == fastestIndex)
                    val isSlowest = (index == slowestIndex && index != fastestIndex)
                    val durationMins = segmentDurations.getOrElse(index) { 15 }
                    val speedRate = segmentRates.getOrElse(index) { 50f }

                    // Color gradation from green (less time) to orange (more time)
                    val rank = sortedByDurationIndices.indexOf(index)
                    val stageColor = when (rank) {
                        0 -> Color(0xFF22C55E) // Bright Emerald Green (Fastest / Least time)
                        1 -> Color(0xFF84CC16) // Lime Green
                        2 -> Color(0xFFEAB308) // Amber Yellow
                        3 -> Color(0xFFF59E0B) // Warm Amber Orange
                        else -> Color(0xFFEA580C) // Deep Warm Orange (Slowest / Most time)
                    }

                    StageRowItem(
                        bracket = bracket,
                        stageColor = stageColor,
                        isPeak = isPeak,
                        isSlowest = isSlowest,
                        mode = mode,
                        durationMinutes = durationMins,
                        speedRate = speedRate,
                        maxDurationMinutes = maxDurationMinutes,
                        maxSpeedRate = maxSpeedRate
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Collapsible Explainer Banner
            val bannerShape = RoundedCornerShape(12.dp)
            Surface(
                onClick = { showAnalogyExplainer = !showAnalogyExplainer },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bannerShape),
                shape = bannerShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Why does charging slow down after 80%?",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = if (showAnalogyExplainer) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAnalogyExplainer) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Think of charging like filling an empty glass of water: you pour quickly at the start, but slow down to a trickle near the brim so it doesn't spill.\n\nYour battery charger does the same: it provides maximum boost under 60% for quick top-ups, then slows down past 80% to keep temperatures cool and protect the battery's lifespan.",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRowItem(
    bracket: ChargeBracketInsight,
    stageColor: Color,
    isPeak: Boolean,
    isSlowest: Boolean,
    mode: SpeedInsightMode,
    durationMinutes: Int,
    speedRate: Float,
    maxDurationMinutes: Int,
    maxSpeedRate: Float
) {
    val hasData = bracket.sampleCount > 0 || bracket.averageRatePercentPerHour > 0f

    // Proportional bar fraction capped at 0.70f (70% width) so it never spans edge-to-edge
    val barFraction = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
        ((durationMinutes.toFloat() / maxDurationMinutes.toFloat()) * 0.70f).coerceIn(0.12f, 0.70f)
    } else {
        ((speedRate / maxSpeedRate) * 0.70f).coerceIn(0.12f, 0.70f)
    }

    // Formatted primary value and secondary description
    val primaryValueText = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
        if (hasData) "$durationMinutes min" else "~$durationMinutes min"
    } else {
        "${speedRate.toInt()}% / hr"
    }

    val secondaryValueText = if (mode == SpeedInsightMode.MINUTES_PER_STAGE) {
        if (hasData) "${speedRate.toInt()}%/hr" else "Est. duration"
    } else {
        "$durationMinutes min"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: [0-20%] Descriptor [⚡ FASTEST] ── Duration / Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Segment indicator pill + stage name + highlight tags (FASTEST or TRICKLE)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Stage percentage pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(stageColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${bracket.startPercent}% - ${bracket.endPercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = stageColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Descriptor (stage name) that yields space so tags never squish
                    Text(
                        text = bracket.stageName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Fastest tag (green)
                    if (isPeak) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF22C55E).copy(alpha = 0.18f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = "Fastest",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "FASTEST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 12.sp,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = Color(0xFF15803D),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else if (isSlowest) {
                        // Slowest tag highlighted in orangish shade without calling it "slowest"
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEA580C).copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Trickle Care",
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "TRICKLE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 12.sp,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = Color(0xFFC2410C),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Metric Value & Subtitle
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = primaryValueText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = stageColor,
                        maxLines = 1
                    )
                    Text(
                        text = secondaryValueText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Standalone horizontal bar: NO secondary background track, cleanly proportioned
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(stageColor)
                )
            }
        }
    }
}
