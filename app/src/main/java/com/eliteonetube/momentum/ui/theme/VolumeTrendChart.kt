package com.eliteonetube.momentum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.data.UnitSystem
import kotlin.math.roundToInt

@Composable
fun VolumeTrendChart(
    sessions: List<WorkoutSession>,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
    sessionsCount: Int = 10,
    onScrub: (WorkoutSession?) -> Unit = {}
) {
    if (sessions.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Log more sessions to unlock trends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val chartEntries = remember(sessions, sessionsCount) { sessions.take(sessionsCount).reversed() }
    val lineColor = Color(0xFF10B981) // CarbGreen / Volume Emerald
    val haptics = LocalHapticFeedback.current

    var scrubIndex by remember { mutableIntStateOf(-1) }
    val points = remember(chartEntries) { mutableListOf<Offset>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(chartEntries) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scrubIndex = -1; onScrub(null) },
                        onDragCancel = { scrubIndex = -1; onScrub(null) },
                        onHorizontalDrag = { change, _ ->
                            val x = change.position.x
                            val width = size.width.toFloat()
                            val stepX = if (chartEntries.size > 1) width / (chartEntries.size - 1) else 0f
                            if (stepX > 0f) {
                                val index = (x / stepX).roundToInt().coerceIn(0, chartEntries.size - 1)
                                if (index != scrubIndex) {
                                    scrubIndex = index
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScrub(chartEntries[index])
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val volumes = chartEntries.map { it.totalVolumeKg }
                val minVol = 0.0
                val maxVol = volumes.max()
                val range = maxVol.takeIf { it > 1.0 } ?: 1.0

                val paddingY = 20.dp.toPx()
                val chartHeight = size.height - (paddingY * 2)
                val stepX = if (chartEntries.size > 1) size.width / (chartEntries.size - 1) else 0f

                points.clear()
                chartEntries.forEachIndexed { index, entry ->
                    val x = stepX * index
                    val normalized = (entry.totalVolumeKg - minVol) / range
                    val y = paddingY + (chartHeight * (1 - normalized).toFloat())
                    points.add(Offset(x, y))
                }

                if (points.size > 1) {
                    val fillPath = Path().apply {
                        points.forEachIndexed { index, point ->
                            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                        }
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
                }

                val path = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                if (scrubIndex != -1 && scrubIndex < points.size) {
                    val scrubPoint = points[scrubIndex]
                    drawLine(
                        color = lineColor.copy(alpha = 0.5f),
                        start = Offset(scrubPoint.x, 0f),
                        end = Offset(scrubPoint.x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    drawCircle(color = lineColor, radius = 6.dp.toPx(), center = scrubPoint)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = scrubPoint)
                } else {
                    points.forEach { point ->
                        drawCircle(color = lineColor.copy(alpha = 0.4f), radius = 4.dp.toPx(), center = point)
                    }
                }
            }

            if (scrubIndex != -1 && scrubIndex < chartEntries.size) {
                val entry = chartEntries[scrubIndex]
                val vol = if (unitSystem == UnitSystem.IMPERIAL) (entry.totalVolumeKg * 2.20462).toInt() else entry.totalVolumeKg.toInt()
                val unit = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
                
                Surface(
                    color = lineColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$vol $unit",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.date.takeLast(5),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
