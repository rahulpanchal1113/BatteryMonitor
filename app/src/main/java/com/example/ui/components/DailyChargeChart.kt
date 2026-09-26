package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import java.util.Calendar
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
                    text = "${sessions.size} session${if (sessions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = emeraldColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (sessions.isEmpty()) {
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
                // 24-Hour Level & Charge Increase Curve Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val leftPadding = 32.dp.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 14.dp.toPx()
                    val bottomPadding = 22.dp.toPx()

                    val chartWidth = w - leftPadding - rightPadding
                    val chartHeight = h - topPadding - bottomPadding

                    // Grid text paints
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    val badgeTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 8.5.sp.toPx()
                        isAntiAlias = true
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    // Level Grid lines (0%, 25%, 50%, 75%, 100%)
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val frac = i.toFloat() / gridSteps
                        val y = topPadding + (chartHeight * frac)
                        val levelVal = (100 - (100 * frac)).toInt()

                        drawLine(
                            color = Color.Gray.copy(alpha = 0.12f),
                            start = Offset(leftPadding, y),
                            end = Offset(w - rightPadding, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "$levelVal%",
                            leftPadding - 6.dp.toPx(),
                            y + 3.5.dp.toPx(),
                            labelPaint
                        )
                    }

                    // Time Vertical Guide lines (6h intervals)
                    val timeSteps = 4
                    for (i in 1 until timeSteps) {
                        val frac = i.toFloat() / timeSteps
                        val x = leftPadding + (chartWidth * frac)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.08f),
                            start = Offset(x, topPadding),
                            end = Offset(x, topPadding + chartHeight),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }

                    fun getXForSeconds(secOfDay: Float): Float {
                        val frac = (secOfDay / 86400f).coerceIn(0f, 1f)
                        return leftPadding + (chartWidth * frac)
                    }

                    fun getYForLevel(level: Float): Float {
                        val frac = (level / 100f).coerceIn(0f, 1f)
                        return topPadding + (chartHeight * (1f - frac))
                    }

                    // Draw each charging session as an upward rising curve & fill
                    sessions.forEach { session ->
                        val startCal = Calendar.getInstance().apply { timeInMillis = session.startTime }
                        val startSec = (startCal.get(Calendar.HOUR_OF_DAY) * 3600 + startCal.get(Calendar.MINUTE) * 60 + startCal.get(Calendar.SECOND)).toFloat()

                        val durSec = max(300L, session.durationSeconds).toFloat()
                        val endSec = min(86400f, startSec + durSec)

                        val safeEndLevel = max(session.startLevel, session.endLevel).toFloat()
                        val startLevel = session.startLevel.toFloat()
                        val deltaGain = (safeEndLevel - startLevel).toInt()

                        val startX = getXForSeconds(startSec)
                        val endX = max(startX + 14.dp.toPx(), getXForSeconds(endSec))

                        val startY = getYForLevel(startLevel)
                        val endY = getYForLevel(safeEndLevel)

                        // Smooth curve from start to end (initial fast ramp, taper near top)
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

                        // Gradient fill under the upward charging slope
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    emeraldColor.copy(alpha = 0.32f),
                                    tealColor.copy(alpha = 0.04f)
                                ),
                                startY = min(startY, endY),
                                endY = topPadding + chartHeight
                            )
                        )

                        // Upward Stroke Curve
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

                        // Start node
                        drawCircle(
                            color = emeraldColor.copy(alpha = 0.3f),
                            radius = 4.dp.toPx(),
                            center = Offset(startX, startY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(startX, startY)
                        )

                        // End peak node
                        drawCircle(
                            color = emeraldColor.copy(alpha = 0.4f),
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

                        // Draw Increase Badge "+X%" above end point
                        val badgeW = 28.dp.toPx()
                        val badgeH = 14.dp.toPx()
                        val badgeX = (endX - badgeW / 2f).coerceIn(leftPadding, w - rightPadding - badgeW)
                        val badgeY = max(2.dp.toPx(), endY - badgeH - 4.dp.toPx())

                        drawRoundRect(
                            color = Color(0xFF15803D),
                            topLeft = Offset(badgeX, badgeY),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "+$deltaGain%",
                            badgeX + badgeW / 2f,
                            badgeY + badgeH - 3.5.dp.toPx(),
                            badgeTextPaint
                        )
                    }

                    // Bottom 24-hour time labels
                    val timeLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    val timeY = h - 4.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("00:00", leftPadding + 6.dp.toPx(), timeY, timeLabelPaint)
                    drawContext.canvas.nativeCanvas.drawText("06:00", leftPadding + (chartWidth * 0.25f), timeY, timeLabelPaint)
                    drawContext.canvas.nativeCanvas.drawText("12:00", leftPadding + (chartWidth * 0.5f), timeY, timeLabelPaint)
                    drawContext.canvas.nativeCanvas.drawText("18:00", leftPadding + (chartWidth * 0.75f), timeY, timeLabelPaint)
                    drawContext.canvas.nativeCanvas.drawText("24:00", w - rightPadding - 6.dp.toPx(), timeY, timeLabelPaint)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed sessions list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
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
