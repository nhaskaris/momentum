package com.eliteonetube.momentum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.WeightEntry

@Composable
fun WeightTrendChart(
    entries: List<WeightEntry>,
    modifier: Modifier = Modifier,
    days: Int = 7
) {
    if (entries.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Not enough data for chart yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val chartEntries = remember(entries, days) { entries.take(days).reversed() }
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val weights = chartEntries.map { it.weight }
            val minWeight = weights.min()
            val maxWeight = weights.max()
            val range = (maxWeight - minWeight).takeIf { it > 0.01 } ?: 1.0

            val paddingY = with(density) { 16.dp.toPx() }
            val chartHeight = size.height - (paddingY * 2)
            val stepX = if (chartEntries.size > 1) size.width / (chartEntries.size - 1) else 0f

            val points = chartEntries.mapIndexed { index, entry ->
                val x = stepX * index
                val normalized = (entry.weight - minWeight) / range
                val y = paddingY + (chartHeight * (1 - normalized).toFloat())
                Offset(x, y)
            }

            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = with(density) { 4.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            points.forEach { point ->
                drawCircle(color = lineColor, radius = with(density) { 5.dp.toPx() }, center = point)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            chartEntries.forEach { entry ->
                Text(
                    text = entry.date.takeLast(5),
                    fontSize = 8.sp,
                    color = labelColor
                )
            }
        }
    }
}
