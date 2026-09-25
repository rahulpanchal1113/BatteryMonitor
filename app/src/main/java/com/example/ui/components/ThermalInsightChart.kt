package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChargingInsightSummary
import java.util.Locale
import kotlin.math.max

@Composable
fun ThermalInsightChart(
    insights: ChargingInsightSummary,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val brackets = insights.brackets
    val validStageTemps = brackets.filter { it.avgTemperature > 0f && it.sampleCount > 0 }
    val avgTempC = when {
        insights.avgBatteryTempOverall > 0f -> insights.avgBatteryTempOverall
        validStageTemps.isNotEmpty() -> validStageTemps.map { it.avgTemperature }.average().toFloat()
        else -> null
    }

    val isOverheatedAbove45 = (avgTempC ?: 0f) >= 45.0f || brackets.any { it.avgTemperature >= 45.0f }

    val displayAvg = if (avgTempC != null) {
        if (useFahrenheit) {
            String.format(Locale.US, "%.1f°F", (avgTempC * 9f / 5f) + 32f)
        } else {
            String.format(Locale.US, "%.1f°C", avgTempC)
        }
    } else {
        "--"
    }

    val headerColor = when {
        isOverheatedAbove45 -> Color(0xFFDC2626)
        avgTempC == null -> MaterialTheme.colorScheme.primary
        avgTempC >= 38f -> Color(0xFFEF4444)
        avgTempC >= 35f -> Color(0xFFF97316)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("thermal_insight_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverheatedAbove45) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Modern Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(headerColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOverheatedAbove45) Icons.Default.LocalFireDepartment else Icons.Default.Thermostat,
                            contentDescription = "Thermal Curve",
                            tint = headerColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Thermal Progression Curve",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isOverheatedAbove45) "CRITICAL OVERHEAT (>45°C) DETECTED" else "Continuous cell temperature curve",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isOverheatedAbove45) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isOverheatedAbove45) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(headerColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isOverheatedAbove45) "OVERHEAT $displayAvg" else "Avg $displayAvg",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = headerColor,
                        softWrap = false
                    )
                }
            }

            if (isOverheatedAbove45) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔥", fontSize = 14.sp)
                        Text(
                            text = "OVERHEAT DETECTED: Stage temperature exceeded 45°C limit. Avoid heavy usage during fast charging.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Modern Smooth Bezier Curve Canvas with Ambient Glow Fill
            val minTemp = 24f
            val maxBracketTemp = brackets.map { it.avgTemperature }.maxOrNull() ?: 36f
            val maxTemp = max(48f, maxBracketTemp + 2f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(91.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val count = brackets.size
                    if (count < 2) return@Canvas

                    // Draw subtle 35°C reference line (ideal ceiling)
                    val normSafeY = h - (((35f - minTemp) / (maxTemp - minTemp)).coerceIn(0.05f, 0.95f) * h)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.25f),
                        start = Offset(0f, normSafeY),
                        end = Offset(w, normSafeY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )

                    // Draw prominent 45°C OVERHEAT threshold reference line
                    val normOverheatY = h - (((45f - minTemp) / (maxTemp - minTemp)).coerceIn(0.04f, 0.96f) * h)
                    drawLine(
                        color = Color(0xFFDC2626).copy(alpha = 0.75f),
                        start = Offset(0f, normOverheatY),
                        end = Offset(w, normOverheatY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    val stepX = w / (count - 1)
                    val points = mutableListOf<Offset>()
                    val baselineTemp = avgTempC ?: 31f

                    brackets.forEachIndexed { i, bracket ->
                        val tempVal = if (bracket.avgTemperature > 0f) bracket.avgTemperature else baselineTemp
                        val normTemp = (tempVal - minTemp) / (maxTemp - minTemp)
                        val y = h - (normTemp.coerceIn(0.08f, 0.92f) * h)
                        val x = i * stepX
                        points.add(Offset(x, y))
                    }

                    // Build smooth cubic bezier curve
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val ctrl1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                            val ctrl2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                            cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p1.x, p1.y)
                        }
                    }

                    // Under-curve gradient fill
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(points.last().x, h)
                        lineTo(points.first().x, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isOverheatedAbove45) Color(0xFFDC2626).copy(alpha = 0.35f) else Color(0xFFF97316).copy(alpha = 0.25f),
                                Color(0xFFF97316).copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw smooth curve stroke with warm gradient
                    drawPath(
                        path = strokePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF10B981), // Emerald at start
                                Color(0xFFF59E0B), // Amber mid
                                if (isOverheatedAbove45) Color(0xFFDC2626) else Color(0xFFF97316)  // Crimson red if overheated
                            )
                        ),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // Draw glowing node circles
                    points.forEachIndexed { idx, pt ->
                        val hasData = brackets.getOrNull(idx)?.let { it.avgTemperature > 0f && it.sampleCount > 0 } ?: false
                        val tempVal = brackets.getOrNull(idx)?.avgTemperature ?: baselineTemp
                        val isNodeOverheat = tempVal >= 45f
                        val nodeColor = if (!hasData) {
                            Color.Gray.copy(alpha = 0.6f)
                        } else if (isNodeOverheat) {
                            Color(0xFFDC2626)
                        } else if (tempVal >= 38f) {
                            Color(0xFFEF4444)
                        } else if (tempVal >= 35f) {
                            Color(0xFFF97316)
                        } else {
                            Color(0xFF10B981)
                        }

                        // Outer soft aura
                        drawCircle(
                            color = if (isNodeOverheat) Color(0xFFDC2626).copy(alpha = 0.45f) else nodeColor.copy(alpha = 0.22f),
                            radius = if (isNodeOverheat) 8.5.dp.toPx() else 6.dp.toPx(),
                            center = pt
                        )
                        // Inner solid node
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = nodeColor,
                            radius = 2.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Modern Bracket Pills with sleek styling & Overheat labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                brackets.forEach { bracket ->
                    val hasData = bracket.avgTemperature > 0f && bracket.sampleCount > 0
                    val isBracketOverheat = bracket.avgTemperature >= 45.0f
                    val tempStr = if (!hasData) {
                        "--"
                    } else if (useFahrenheit) {
                        String.format(Locale.US, "%.0f°", (bracket.avgTemperature * 9f / 5f) + 32f)
                    } else {
                        String.format(Locale.US, "%.1f°", bracket.avgTemperature)
                    }

                    val pillColor = if (!hasData) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else if (isBracketOverheat) {
                        Color(0xFFDC2626)
                    } else if (bracket.avgTemperature >= 38f) {
                        Color(0xFFEF4444)
                    } else if (bracket.avgTemperature >= 35f) {
                        Color(0xFFF97316)
                    } else {
                        Color(0xFF10B981)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        if (isBracketOverheat) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFDC2626))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "OVERHEAT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 7.5.sp
                                    ),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Text(
                            text = tempStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = pillColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${bracket.startPercent}-${bracket.endPercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
