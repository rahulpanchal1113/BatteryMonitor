package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.data.local.ChargingSessionEntity
import com.example.data.model.DailyBatteryStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun DailyChargeChart(
    sessions: List<ChargingSessionEntity>,
    stats: DailyBatteryStats?,
    modifier: Modifier = Modifier,
    onSessionClick: ((ChargingSessionEntity) -> Unit)? = null
) {
    val emeraldColor = Color(0xFF22C55E)
    val tealColor = Color(0xFF10B981)
    val sortedSessions = remember(sessions) { sessions.sortedBy { it.startTime } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_charge_chart_card"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = emeraldColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Charging Timeline Curve",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${sortedSessions.size} session${if (sortedSessions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = emeraldColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (sortedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No charging sessions recorded for this day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidthDp = maxWidth
                    val yAxisWidthDp = 34.dp
                    val count = sortedSessions.size

                    // Provide generous slot width per session so curves and badges NEVER superimpose
                    val slotWidthDp = if (count <= 2) {
                        (availableWidthDp - yAxisWidthDp - 8.dp) / count
                    } else {
                        140.dp
                    }

                    val totalCanvasWidthDp = if (count <= 2) {
                        availableWidthDp
                    } else {
                        yAxisWidthDp + (slotWidthDp * count) + 16.dp
                    }

                    val scrollState = rememberScrollState()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (count > 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👉 Scroll timeline horizontally",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (count > 2) Modifier.horizontalScroll(scrollState)
                                    else Modifier
                                )
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(totalCanvasWidthDp)
                                    .height(160.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val leftPadding = yAxisWidthDp.toPx()
                                val rightPadding = 16.dp.toPx()
                                val topPadding = 24.dp.toPx()
                                val bottomPadding = 30.dp.toPx()

                                val chartWidth = w - leftPadding - rightPadding
                                val chartHeight = h - topPadding - bottomPadding
                                val slotWidthPx = (chartWidth / count)

                                val labelPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 9.sp.toPx()
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                }

                                val timePaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 8.5.sp.toPx()
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }

                                val badgeTextPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 8.5.sp.toPx()
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }

                                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

                                // 1. Draw horizontal level grid lines (0%, 25%, 50%, 75%, 100%)
                                val gridSteps = 4
                                for (i in 0..gridSteps) {
                                    val frac = i.toFloat() / gridSteps
                                    val y = topPadding + (chartHeight * frac)
                                    val levelVal = (100 - (100 * frac)).toInt()

                                    drawLine(
                                        color = Color.Gray.copy(alpha = 0.15f),
                                        start = Offset(leftPadding, y),
                                        end = Offset(w - rightPadding, y),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        "$levelVal%",
                                        leftPadding - 5.dp.toPx(),
                                        y + 3.5.dp.toPx(),
                                        labelPaint
                                    )
                                }

                                fun getYForLevel(level: Float): Float {
                                    val frac = (level / 100f).coerceIn(0f, 1f)
                                    return topPadding + (chartHeight * (1f - frac))
                                }

                                // 2. Draw each charging session in its own isolated lane
                                sortedSessions.forEachIndexed { index, session ->
                                    val laneLeft = leftPadding + (index * slotWidthPx)
                                    val laneRight = laneLeft + slotWidthPx

                                    val startX = laneLeft + 14.dp.toPx()
                                    val endX = laneRight - 14.dp.toPx()

                                    val safeEndLevel = max(session.startLevel, session.endLevel).toFloat()
                                    val startLevel = session.startLevel.toFloat()
                                    val deltaGain = max(0, (safeEndLevel - startLevel).toInt())

                                    val startY = getYForLevel(startLevel)
                                    val endY = getYForLevel(safeEndLevel)

                                    // Smooth upward Bezier curve
                                    val cX1 = startX + (endX - startX) * 0.35f
                                    val cY1 = startY - (startY - endY) * 0.65f
                                    val cX2 = startX + (endX - startX) * 0.75f
                                    val cY2 = endY

                                    val fillPath = Path().apply {
                                        moveTo(startX, topPadding + chartHeight)
                                        lineTo(startX, startY)
                                        cubicTo(cX1, cY1, cX2, cY2, endX, endY)
                                        lineTo(endX, topPadding + chartHeight)
                                        close()
                                    }

                                    // Gradient fill below the curve
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                emeraldColor.copy(alpha = 0.35f),
                                                tealColor.copy(alpha = 0.03f)
                                            ),
                                            startY = min(startY, endY),
                                            endY = topPadding + chartHeight
                                        )
                                    )

                                    // Upward Stroke
                                    val strokePath = Path().apply {
                                        moveTo(startX, startY)
                                        cubicTo(cX1, cY1, cX2, cY2, endX, endY)
                                    }

                                    drawPath(
                                        path = strokePath,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(emeraldColor, tealColor),
                                            startX = startX,
                                            endX = endX
                                        ),
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                    )

                                    // Start Node
                                    drawCircle(
                                        color = emeraldColor.copy(alpha = 0.3f),
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(startX, startY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(startX, startY)
                                    )

                                    // End Node
                                    drawCircle(
                                        color = emeraldColor.copy(alpha = 0.45f),
                                        radius = 5.5.dp.toPx(),
                                        center = Offset(endX, endY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.dp.toPx(),
                                        center = Offset(endX, endY)
                                    )
                                    drawCircle(
                                        color = emeraldColor,
                                        radius = 2.dp.toPx(),
                                        center = Offset(endX, endY)
                                    )

                                    // Connecting bridge line to next session
                                    if (index < count - 1) {
                                        val nextSession = sortedSessions[index + 1]
                                        val nextLaneLeft = leftPadding + ((index + 1) * slotWidthPx)
                                        val nextStartX = nextLaneLeft + 14.dp.toPx()
                                        val nextStartY = getYForLevel(nextSession.startLevel.toFloat())

                                        drawLine(
                                            color = Color.Gray.copy(alpha = 0.28f),
                                            start = Offset(endX, endY),
                                            end = Offset(nextStartX, nextStartY),
                                            strokeWidth = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                                        )
                                    }

                                    // Draw Gain Badge (+X%) positioned cleanly above this session
                                    val badgeW = 32.dp.toPx()
                                    val badgeH = 15.dp.toPx()
                                    val midX = (startX + endX) / 2f
                                    val badgeX = (midX - badgeW / 2f).coerceIn(laneLeft + 2.dp.toPx(), laneRight - badgeW - 2.dp.toPx())
                                    val badgeY = (min(startY, endY) - badgeH - 6.dp.toPx()).coerceAtLeast(2.dp.toPx())

                                    drawRoundRect(
                                        color = Color(0xFF15803D),
                                        topLeft = Offset(badgeX, badgeY),
                                        size = Size(badgeW, badgeH),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        "+$deltaGain%",
                                        badgeX + badgeW / 2f,
                                        badgeY + badgeH - 4.dp.toPx(),
                                        badgeTextPaint
                                    )

                                    // Time labels below this specific session
                                    val startFormatted = timeFormat.format(Date(session.startTime))
                                    val endFormatted = session.endTime?.let { timeFormat.format(Date(it)) } ?: "Now"
                                    val durationMins = max(1L, session.durationSeconds / 60)

                                    val labelY = h - 6.dp.toPx()
                                    val durationText = "${session.startLevel}%→${safeEndLevel.toInt()}% ($durationMins m)"
                                    val timeRangeText = "$startFormatted – $endFormatted"

                                    drawContext.canvas.nativeCanvas.drawText(
                                        durationText,
                                        midX,
                                        labelY - 10.dp.toPx(),
                                        timePaint
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(
                                        timeRangeText,
                                        midX,
                                        labelY,
                                        timePaint
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed sessions list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedSessions.forEach { session ->
                        SessionItemRow(
                            session = session,
                            onClick = onSessionClick?.let { { it(session) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItemRow(
    session: ChargingSessionEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startTimeStr = timeFormat.format(Date(session.startTime))
    val endTimeStr = session.endTime?.let { timeFormat.format(Date(it)) } ?: "Active"
    val safeEnd = max(session.startLevel, session.endLevel)
    val deltaPercent = max(0, safeEnd - session.startLevel)
    val durationMinutes = session.durationSeconds / 60

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Charge Session",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "$startTimeStr – $endTimeStr",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${session.plugType} • $durationMinutes mins",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+$deltaPercent%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF22C55E)
                    )
                )
                Text(
                    text = "${session.startLevel}% → $safeEnd%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
