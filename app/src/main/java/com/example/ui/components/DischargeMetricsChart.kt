package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BatteryEventEntity
import com.example.data.local.DischargingSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class DischargeChartDataPoint(
    val timestamp: Long,
    val batteryPercent: Float,
    val tempDisplay: Float,
    val tempCelsius: Float
)

@Composable
fun DischargeMetricsChart(
    session: DischargingSessionEntity,
    events: List<BatteryEventEntity>,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val batteryDrainColor = Color(0xFF0284C7) // Vibrant sky/ocean blue for discharge
    val tempColor = Color(0xFFF97316)         // Orange
    val overheatColor = Color(0xFFDC2626)     // Crimson red

    // Build data points for the discharge curve
    val points = remember(session, events, useFahrenheit) {
        val rawPoints = mutableListOf<DischargeChartDataPoint>()

        fun toDisplayTemp(celsius: Float): Float {
            val safeC = if (celsius > 0f) celsius else 30f
            return if (useFahrenheit) (safeC * 9f / 5f) + 32f else safeC
        }

        if (events.isNotEmpty()) {
            val sorted = events.sortedBy { it.timestamp }
            sorted.forEach { ev ->
                val safeC = if (ev.temperatureCelsius > 0f) ev.temperatureCelsius else 30f
                rawPoints.add(
                    DischargeChartDataPoint(
                        timestamp = ev.timestamp,
                        batteryPercent = ev.batteryLevel.toFloat(),
                        tempDisplay = toDisplayTemp(safeC),
                        tempCelsius = safeC
                    )
                )
            }
        }

        // If fewer than 2 points, synthesize start and end points
        if (rawPoints.isEmpty()) {
            val startT = session.startTime
            val endT = session.endTime ?: (session.startTime + max(60_000L, session.durationSeconds * 1000L))
            val startC = if (session.startTemp > 0f) session.startTemp else 30f
            val endC = if (session.maxTemp > 0f) session.maxTemp else startC

            rawPoints.add(
                DischargeChartDataPoint(
                    timestamp = startT,
                    batteryPercent = session.startLevel.toFloat(),
                    tempDisplay = toDisplayTemp(startC),
                    tempCelsius = startC
                )
            )
            rawPoints.add(
                DischargeChartDataPoint(
                    timestamp = endT,
                    batteryPercent = session.endLevel.toFloat(),
                    tempDisplay = toDisplayTemp(endC),
                    tempCelsius = endC
                )
            )
        } else if (rawPoints.size == 1) {
            val first = rawPoints[0]
            val endT = session.endTime ?: (first.timestamp + max(60_000L, session.durationSeconds * 1000L))
            rawPoints.add(
                DischargeChartDataPoint(
                    timestamp = endT,
                    batteryPercent = session.endLevel.toFloat(),
                    tempDisplay = first.tempDisplay,
                    tempCelsius = first.tempCelsius
                )
            )
        }

        rawPoints
    }

    val maxTempC = points.maxOfOrNull { it.tempCelsius } ?: session.maxTemp
    val isOverheatedAbove42 = maxTempC >= 42.0f
    val isSevereOverheat = maxTempC >= 45.0f

    val minDisplayTemp = (points.minOfOrNull { it.tempDisplay } ?: 25f).coerceAtLeast(15f)
    val maxDisplayTemp = (points.maxOfOrNull { it.tempDisplay } ?: 45f).coerceAtLeast(minDisplayTemp + 10f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discharge_metrics_chart_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Chart Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discharge & Thermal Curve",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Battery level drop vs thermal fluctuation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSevereOverheat) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "High Heat Drain",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(batteryDrainColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Battery Level (Left %)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tempColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Temp (Right ${if (useFahrenheit) "°F" else "°C"})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dual-Curve Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val leftPadding = 34.dp.toPx()
                    val rightPadding = 34.dp.toPx()
                    val topPadding = 16.dp.toPx()
                    val bottomPadding = 26.dp.toPx()

                    val chartWidth = canvasWidth - leftPadding - rightPadding
                    val chartHeight = canvasHeight - topPadding - bottomPadding

                    val minTime = points.first().timestamp
                    val maxTime = max(minTime + 60_000L, points.last().timestamp)
                    val timeSpan = max(1L, maxTime - minTime)

                    fun getX(timestamp: Long): Float {
                        val fraction = (timestamp - minTime).toFloat() / timeSpan.toFloat()
                        return leftPadding + (fraction * chartWidth)
                    }

                    fun getYBattery(percent: Float): Float {
                        val fraction = (percent.coerceIn(0f, 100f)) / 100f
                        return topPadding + (chartHeight * (1f - fraction))
                    }

                    fun getYTemp(tempDisplay: Float): Float {
                        val range = max(5f, maxDisplayTemp - minDisplayTemp)
                        val fraction = ((tempDisplay - minDisplayTemp) / range).coerceIn(0f, 1f)
                        return topPadding + (chartHeight * (1f - fraction))
                    }

                    val gridColor = Color.Gray.copy(alpha = 0.16f)
                    val axisTextColor = android.graphics.Color.GRAY

                    val textPaintLeft = android.graphics.Paint().apply {
                        color = axisTextColor
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    val textPaintRight = android.graphics.Paint().apply {
                        color = axisTextColor
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                    }
                    val timeLabelPaint = android.graphics.Paint().apply {
                        color = axisTextColor
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    // Draw 4 Horizontal Grid Lines
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val fraction = i.toFloat() / gridSteps.toFloat()
                        val y = topPadding + (chartHeight * fraction)
                        val battLevel = 100 - (i * 25)
                        val tempVal = maxDisplayTemp - (fraction * (maxDisplayTemp - minDisplayTemp))

                        drawLine(
                            color = gridColor,
                            start = Offset(leftPadding, y),
                            end = Offset(canvasWidth - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Left Axis (% Battery)
                        drawContext.canvas.nativeCanvas.drawText(
                            "$battLevel%",
                            leftPadding - 4.dp.toPx(),
                            y + 3.dp.toPx(),
                            textPaintLeft
                        )

                        // Right Axis (Temp)
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(Locale.US, "%.0f°", tempVal),
                            canvasWidth - rightPadding + 4.dp.toPx(),
                            y + 3.dp.toPx(),
                            textPaintRight
                        )
                    }

                    // Build Battery Drain Path and Gradient Fill
                    val battPath = Path()
                    val battFillPath = Path()
                    val battOffsets = mutableListOf<Offset>()

                    points.forEachIndexed { index, p ->
                        val x = getX(p.timestamp)
                        val y = getYBattery(p.batteryPercent)
                        battOffsets.add(Offset(x, y))

                        if (index == 0) {
                            battPath.moveTo(x, y)
                            battFillPath.moveTo(x, y)
                        } else {
                            val prev = battOffsets[index - 1]
                            val midX = (prev.x + x) / 2f
                            battPath.cubicTo(midX, prev.y, midX, y, x, y)
                            battFillPath.cubicTo(midX, prev.y, midX, y, x, y)
                        }
                    }

                    // Complete battery fill
                    if (battOffsets.isNotEmpty()) {
                        val lastPoint = battOffsets.last()
                        val firstPoint = battOffsets.first()
                        val bottomY = topPadding + chartHeight
                        battFillPath.lineTo(lastPoint.x, bottomY)
                        battFillPath.lineTo(firstPoint.x, bottomY)
                        battFillPath.close()

                        drawPath(
                            path = battFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    batteryDrainColor.copy(alpha = 0.28f),
                                    batteryDrainColor.copy(alpha = 0.03f)
                                ),
                                startY = topPadding,
                                endY = bottomY
                            )
                        )

                        drawPath(
                            path = battPath,
                            color = batteryDrainColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Build Temperature Path
                    val tempPath = Path()
                    val tempOffsets = mutableListOf<Offset>()

                    points.forEachIndexed { index, p ->
                        val x = getX(p.timestamp)
                        val y = getYTemp(p.tempDisplay)
                        tempOffsets.add(Offset(x, y))

                        if (index == 0) {
                            tempPath.moveTo(x, y)
                        } else {
                            val prev = tempOffsets[index - 1]
                            val midX = (prev.x + x) / 2f
                            tempPath.cubicTo(midX, prev.y, midX, y, x, y)
                        }
                    }

                    if (tempOffsets.isNotEmpty()) {
                        drawPath(
                            path = tempPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF59E0B),
                                    if (isSevereOverheat) Color(0xFFDC2626) else Color(0xFFF97316)
                                )
                            ),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Draw Node points
                    battOffsets.forEach { offset ->
                        drawCircle(color = batteryDrainColor.copy(alpha = 0.25f), radius = 5.5.dp.toPx(), center = offset)
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = offset)
                        drawCircle(color = batteryDrainColor, radius = 2.dp.toPx(), center = offset)
                    }

                    tempOffsets.forEachIndexed { index, offset ->
                        val isOverheatNode = points.getOrNull(index)?.tempCelsius?.let { it >= 42.0f } ?: false
                        val nodeColor = if (isOverheatNode) Color(0xFFDC2626) else tempColor
                        drawCircle(color = nodeColor.copy(alpha = 0.35f), radius = 5.dp.toPx(), center = offset)
                        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = offset)
                        drawCircle(color = nodeColor, radius = 1.8.dp.toPx(), center = offset)
                    }

                    // Draw Timestamps on bottom
                    val startStr = timeFormat.format(Date(minTime))
                    val endStr = timeFormat.format(Date(maxTime))
                    val yTimePos = canvasHeight - 4.dp.toPx()

                    drawContext.canvas.nativeCanvas.drawText(startStr, leftPadding + 8.dp.toPx(), yTimePos, timeLabelPaint)
                    if (points.size >= 3) {
                        val midP = points[points.size / 2]
                        drawContext.canvas.nativeCanvas.drawText(timeFormat.format(Date(midP.timestamp)), getX(midP.timestamp), yTimePos, timeLabelPaint)
                    }
                    drawContext.canvas.nativeCanvas.drawText(endStr, canvasWidth - rightPadding - 8.dp.toPx(), yTimePos, timeLabelPaint)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Thermal Impact Panel ("How Overheating Accelerates Battery Drain & Degrades Cell Health")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSevereOverheat) Color(0xFFFEF2F2)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSevereOverheat) Icons.Default.LocalFireDepartment else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isSevereOverheat) Color(0xFFDC2626) else Color(0xFFF97316),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "How Overheating Accelerates Battery Drain",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSevereOverheat) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isSevereOverheat) {
                            "🔥 Severe Heat Stress: Battery temperatures exceeded 45°C. At these extreme levels, electrochemical internal resistance spikes and parasitic self-discharge wastes up to ~45% more energy as heat rather than useful screen-on time, while CPU throttling causes sluggishness."
                        } else if (isOverheatedAbove42) {
                            "⚠️ Thermal Drain Warning: Temperatures climbed above 42°C during use. Heat accelerates electrochemical self-discharge and forces internal thermal management to consume extra cooling power, increasing battery drain rate."
                        } else {
                            "🟢 Optimal Discharge Efficiency: Battery temperature remained cool (well below 40°C threshold). Lithium-ion cells operate at maximum energetic efficiency without accelerated self-discharge or CPU thermal throttling."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp
                        ),
                        color = if (isSevereOverheat) Color(0xFF7F1D1D) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3-Stage thermal drain breakdown bar (equal height cards)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("<35°C Cool", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = Color(0xFF15803D))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("1.0x Normal Drain", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF16A34A))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("36–42°C Warm", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = Color(0xFFB45309))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("~1.25x Elevated", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFFD97706))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDC2626).copy(alpha = 0.12f))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("≥43°C High Heat", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = Color(0xFFB91C1C))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("~1.5x Accelerated", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }
}
