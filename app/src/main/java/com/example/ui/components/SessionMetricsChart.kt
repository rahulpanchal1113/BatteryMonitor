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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BatteryEventEntity
import com.example.data.local.ChargingSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class SessionChartDataPoint(
    val timestamp: Long,
    val batteryPercent: Float,
    val tempDisplay: Float
)

@Composable
fun SessionMetricsChart(
    session: ChargingSessionEntity,
    events: List<BatteryEventEntity>,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val batteryColor = Color(0xFF22C55E) // Vibrant emerald green
    val tempColor = Color(0xFFF97316)    // Vibrant orange

    // Build data points for the curves
    val points = remember(session, events, useFahrenheit) {
        val rawPoints = mutableListOf<SessionChartDataPoint>()

        fun toDisplayTemp(celsius: Float): Float {
            val safeC = if (celsius > 0f) celsius else 30f
            return if (useFahrenheit) (safeC * 9f / 5f) + 32f else safeC
        }

        if (events.isNotEmpty()) {
            val sorted = events.sortedBy { it.timestamp }
            sorted.forEach { ev ->
                rawPoints.add(
                    SessionChartDataPoint(
                        timestamp = ev.timestamp,
                        batteryPercent = ev.batteryLevel.toFloat(),
                        tempDisplay = toDisplayTemp(ev.temperatureCelsius)
                    )
                )
            }
        }

        // If fewer than 2 points, ensure we have at least start and end
        if (rawPoints.isEmpty()) {
            val startT = session.startTime
            val endT = session.endTime ?: (session.startTime + max(60_000L, session.durationSeconds * 1000L))
            val startTemp = toDisplayTemp(session.startTemp)
            val endTemp = toDisplayTemp(if (session.maxTemp > 0f) session.maxTemp else session.startTemp)

            val safeEnd = max(session.startLevel, session.endLevel)
            rawPoints.add(SessionChartDataPoint(startT, session.startLevel.toFloat(), startTemp))
            rawPoints.add(SessionChartDataPoint(endT, safeEnd.toFloat(), endTemp))
        }

        // If only 2 points and span is > 2 mins, generate smooth intermediate checkpoints
        if (rawPoints.size == 2) {
            val p0 = rawPoints[0]
            val p1 = rawPoints[1]
            val span = p1.timestamp - p0.timestamp
            if (span >= 120_000L) {
                val interpolated = mutableListOf<SessionChartDataPoint>()
                interpolated.add(p0)
                val steps = 4
                for (i in 1 until steps) {
                    val frac = i.toFloat() / steps
                    val t = (p0.timestamp + (span * frac)).toLong()
                    // Li-Ion charge curve slightly concaves or is linear
                    val level = p0.batteryPercent + ((p1.batteryPercent - p0.batteryPercent) * frac)
                    val temp = p0.tempDisplay + ((p1.tempDisplay - p0.tempDisplay) * (frac * 1.1f).coerceAtMost(1f))
                    interpolated.add(SessionChartDataPoint(t, level, temp))
                }
                interpolated.add(p1)
                interpolated
            } else {
                rawPoints
            }
        } else {
            rawPoints
        }
    }

    val minBattery = remember(points) {
        val minP = points.minOfOrNull { it.batteryPercent } ?: 0f
        max(0f, minP - 5f)
    }
    val maxBattery = remember(points) {
        val maxP = points.maxOfOrNull { it.batteryPercent } ?: 100f
        min(100f, max(minBattery + 10f, maxP + 5f))
    }

    val minTemp = remember(points) {
        val minT = points.minOfOrNull { it.tempDisplay } ?: 25f
        max(0f, minT - 2f)
    }
    val maxTemp = remember(points) {
        val maxT = points.maxOfOrNull { it.tempDisplay } ?: 40f
        max(minTemp + 4f, maxT + 2f)
    }

    val minTime = points.first().timestamp
    val maxTime = max(minTime + 1000L, points.last().timestamp)

    val tempUnit = if (useFahrenheit) "°F" else "°C"
    val gainedPct = session.endLevel - session.startLevel
    val gainStr = if (gainedPct >= 0) "+$gainedPct%" else "$gainedPct%"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("session_metrics_chart_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with Chart Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Charge & Temperature Curve",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Unified progression over charge duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Legend Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery % Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(batteryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Battery Level ($gainStr)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = batteryColor
                    )
                }

                // Temp Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tempColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val endTempStr = String.format(Locale.US, "%.1f%s", points.last().tempDisplay, tempUnit)
                    Text(
                        text = "Temp ($endTempStr)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tempColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Modern Canvas Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val leftPadding = 38.dp.toPx()
                    val rightPadding = 38.dp.toPx()
                    val bottomPadding = 24.dp.toPx()
                    val topPadding = 14.dp.toPx()

                    val chartWidth = canvasWidth - leftPadding - rightPadding
                    val chartHeight = canvasHeight - topPadding - bottomPadding

                    // Draw subtle grid lines (4 horizontal steps)
                    val gridSteps = 3
                    val leftLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(220, 34, 197, 94)
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                    }
                    val rightLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(220, 249, 115, 22)
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val timeLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(180, 150, 150, 150)
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    for (i in 0..gridSteps) {
                        val yFrac = i.toFloat() / gridSteps
                        val yPos = topPadding + (chartHeight * yFrac)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.12f),
                            start = Offset(leftPadding, yPos),
                            end = Offset(canvasWidth - rightPadding, yPos),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Left axis label: Battery %
                        val battVal = (maxBattery - ((maxBattery - minBattery) * yFrac)).toInt()
                        drawContext.canvas.nativeCanvas.drawText(
                            "$battVal%",
                            leftPadding - 6.dp.toPx() - 18.dp.toPx(),
                            yPos + 4.dp.toPx(),
                            leftLabelPaint
                        )

                        // Right axis label: Temperature
                        val tempVal = maxTemp - ((maxTemp - minTemp) * yFrac)
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(Locale.US, "%.0f°", tempVal),
                            canvasWidth - 4.dp.toPx(),
                            yPos + 4.dp.toPx(),
                            rightLabelPaint
                        )
                    }

                    // Compute pixel coordinates
                    fun getX(timestamp: Long): Float {
                        val frac = ((timestamp - minTime).toFloat() / max(1f, (maxTime - minTime).toFloat())).coerceIn(0f, 1f)
                        return leftPadding + (chartWidth * frac)
                    }

                    fun getBatteryY(level: Float): Float {
                        val frac = ((level - minBattery) / max(1f, (maxBattery - minBattery))).coerceIn(0f, 1f)
                        return topPadding + chartHeight * (1f - frac)
                    }

                    fun getTempY(temp: Float): Float {
                        val frac = ((temp - minTemp) / max(1f, (maxTemp - minTemp))).coerceIn(0f, 1f)
                        return topPadding + chartHeight * (1f - frac)
                    }

                    val battOffsets = points.map { Offset(getX(it.timestamp), getBatteryY(it.batteryPercent)) }
                    val tempOffsets = points.map { Offset(getX(it.timestamp), getTempY(it.tempDisplay)) }

                    // Draw Battery % Fill Gradient underneath curve
                    if (battOffsets.size >= 2) {
                        val fillPath = Path().apply {
                            moveTo(battOffsets.first().x, topPadding + chartHeight)
                            lineTo(battOffsets.first().x, battOffsets.first().y)
                            for (i in 0 until battOffsets.size - 1) {
                                val current = battOffsets[i]
                                val next = battOffsets[i + 1]
                                val cX1 = (current.x + next.x) / 2f
                                val cY1 = current.y
                                val cX2 = (current.x + next.x) / 2f
                                val cY2 = next.y
                                cubicTo(cX1, cY1, cX2, cY2, next.x, next.y)
                            }
                            lineTo(battOffsets.last().x, topPadding + chartHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF22C55E).copy(alpha = 0.25f),
                                    Color(0xFF10B981).copy(alpha = 0.02f)
                                ),
                                startY = topPadding,
                                endY = topPadding + chartHeight
                            )
                        )
                    }

                    // Draw Battery % Line with smooth horizontal gradient
                    if (battOffsets.size >= 2) {
                        val strokePath = Path().apply {
                            moveTo(battOffsets.first().x, battOffsets.first().y)
                            for (i in 0 until battOffsets.size - 1) {
                                val current = battOffsets[i]
                                val next = battOffsets[i + 1]
                                val cX1 = (current.x + next.x) / 2f
                                val cY1 = current.y
                                val cX2 = (current.x + next.x) / 2f
                                val cY2 = next.y
                                cubicTo(cX1, cY1, cX2, cY2, next.x, next.y)
                            }
                        }
                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF22C55E),
                                    Color(0xFF10B981)
                                )
                            ),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Draw Temperature Line with smooth horizontal warm gradient
                    if (tempOffsets.size >= 2) {
                        val tempPath = Path().apply {
                            moveTo(tempOffsets.first().x, tempOffsets.first().y)
                            for (i in 0 until tempOffsets.size - 1) {
                                val current = tempOffsets[i]
                                val next = tempOffsets[i + 1]
                                val cX1 = (current.x + next.x) / 2f
                                val cY1 = current.y
                                val cX2 = (current.x + next.x) / 2f
                                val cY2 = next.y
                                cubicTo(cX1, cY1, cX2, cY2, next.x, next.y)
                            }
                        }
                        drawPath(
                            path = tempPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF59E0B),
                                    Color(0xFFF97316)
                                )
                            ),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Draw Modern Point Nodes with glowing outer halo and clean white core
                    battOffsets.forEachIndexed { index, offset ->
                        drawCircle(
                            color = batteryColor.copy(alpha = 0.22f),
                            radius = 6.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = batteryColor,
                            radius = 2.5.dp.toPx(),
                            center = offset
                        )
                    }

                    tempOffsets.forEachIndexed { index, offset ->
                        drawCircle(
                            color = tempColor.copy(alpha = 0.22f),
                            radius = 5.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = tempColor,
                            radius = 2.dp.toPx(),
                            center = offset
                        )
                    }

                    // Draw X-axis timestamps: Start time, Middle time, End time
                    val startStr = timeFormat.format(Date(minTime))
                    val endStr = timeFormat.format(Date(maxTime))
                    val yTimePos = canvasHeight - 4.dp.toPx()

                    drawContext.canvas.nativeCanvas.drawText(
                        startStr,
                        leftPadding + 10.dp.toPx(),
                        yTimePos,
                        timeLabelPaint
                    )

                    if (points.size >= 3) {
                        val midIndex = points.size / 2
                        val midP = points[midIndex]
                        val midStr = timeFormat.format(Date(midP.timestamp))
                        drawContext.canvas.nativeCanvas.drawText(
                            midStr,
                            getX(midP.timestamp),
                            yTimePos,
                            timeLabelPaint
                        )
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        endStr,
                        canvasWidth - rightPadding - 10.dp.toPx(),
                        yTimePos,
                        timeLabelPaint
                    )
                }
            }
        }
    }
}
