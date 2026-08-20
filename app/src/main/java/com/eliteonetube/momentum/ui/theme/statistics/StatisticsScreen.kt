package com.eliteonetube.momentum.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.logic.Units
import com.eliteonetube.momentum.ui.WeightTrendChart
import com.eliteonetube.momentum.ui.VolumeTrendChart
import com.eliteonetube.momentum.ui.theme.dashboard.MascotMood
import com.eliteonetube.momentum.ui.theme.dashboard.MomentumMascot
import java.time.LocalDate
import kotlin.math.roundToInt
import androidx.compose.animation.*

@Composable
fun StatisticsScreen(
    weights: List<WeightEntry>,
    recentSessions: List<WorkoutSession>,
    unitSystem: UnitSystem
) {
    var mascotMood by remember { mutableStateOf(MascotMood.IDLE) }
    var scrubText by remember { mutableStateOf<String?>(null) }
    var helperMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Hero Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(top = 48.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    ) {
                        AnimatedVisibility(
                            visible = helperMessage != null,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End) + scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End) + scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp),
                                shadowElevation = 8.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = helperMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Box(modifier = Modifier.size(100.dp)) {
                            MomentumMascot(mood = mascotMood, modifier = Modifier.fillMaxSize())
                        }
                    }
                    
                    scrubText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } ?: run {
                        Text(
                            text = "Analytics",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    Text(
                        text = if (scrubText != null) "" else "Track your long-term evolution",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Weight Trends Section
                StatsSection("Weight Trends", Icons.Default.MonitorWeight) {
                    WeightTrendChart(
                        entries = weights,
                        unitSystem = unitSystem,
                        onScrub = { entry ->
                            if (entry != null) {
                                val index = weights.indexOf(entry)
                                val nextEntry = weights.getOrNull(index + 1)
                                
                                val comment = if (nextEntry != null) {
                                    val diff = entry.weight - nextEntry.weight
                                    when {
                                        diff < -0.2 -> "Scale moved down! Persistence pays off."
                                        diff > 0.2 -> "Don't sweat a daily shift—it's usually just water."
                                        else -> "Holding steady. Consistency is our secret weapon."
                                    }
                                } else {
                                    "Every data point helps me optimize your plan."
                                }
                                
                                mascotMood = if (nextEntry != null && entry.weight < nextEntry.weight) MascotMood.HAPPY else MascotMood.IDLE
                                scrubText = Units.displayWeight(entry.weight, unitSystem)
                                helperMessage = comment
                            } else {
                                mascotMood = MascotMood.IDLE
                                scrubText = null
                                helperMessage = null
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WeightStatsContent(weights, unitSystem)
                }

                // Workout Trends Section
                StatsSection("Training Volume", Icons.Default.FitnessCenter) {
                    val avgVolume = remember(recentSessions) { 
                        if (recentSessions.isNotEmpty()) recentSessions.map { it.totalVolumeKg }.average() else 0.0 
                    }
                    
                    VolumeTrendChart(
                        sessions = recentSessions,
                        unitSystem = unitSystem,
                        onScrub = { session ->
                            if (session != null) {
                                val comment = when {
                                    session.totalVolumeKg > avgVolume * 1.2 -> "Incredible session! You're building serious strength."
                                    session.totalVolumeKg < avgVolume * 0.8 -> "Every bit of movement counts toward the goal."
                                    else -> "Solid training day. Great job showing up!"
                                }
                                
                                mascotMood = if (session.totalVolumeKg >= avgVolume) MascotMood.HAPPY else MascotMood.IDLE
                                val vol = if (unitSystem == UnitSystem.IMPERIAL) (session.totalVolumeKg * 2.20462).toInt() else session.totalVolumeKg.toInt()
                                val unit = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
                                scrubText = "$vol $unit"
                                helperMessage = comment
                            } else {
                                mascotMood = MascotMood.IDLE
                                scrubText = null
                                helperMessage = null
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WorkoutStatsContent(recentSessions, unitSystem)
                }
                
                // Progression Block
                StatsSection("Performance", Icons.Default.TrendingUp) {
                    PerformanceContent(recentSessions, unitSystem)
                }
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun WeightStatsContent(weights: List<WeightEntry>, unitSystem: UnitSystem) {
    if (weights.isEmpty()) {
        EmptyStatsCard("No weight data logged yet.")
        return
    }

    val currentWeight = weights.first().weight
    val startWeight = weights.last().weight
    val diff = currentWeight - startWeight
    val diffLabel = Units.displayWeight(kotlin.math.abs(diff), unitSystem)
    val color = if (diff <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = "Starting",
                    value = Units.displayWeight(startWeight, unitSystem),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Current",
                    value = Units.displayWeight(currentWeight, unitSystem),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Total Change",
                    value = "${if (diff > 0) "+" else ""}$diffLabel",
                    valueColor = color,
                    modifier = Modifier.weight(1f)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            val minWeight = weights.minBy { it.weight }.weight
            val maxWeight = weights.maxBy { it.weight }.weight
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = "O All-Time Low",
                    value = Units.displayWeight(minWeight, unitSystem),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "All-Time High",
                    value = Units.displayWeight(maxWeight, unitSystem),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkoutStatsContent(sessions: List<WorkoutSession>, unitSystem: UnitSystem) {
    if (sessions.isEmpty()) {
        EmptyStatsCard("No workouts logged yet.")
        return
    }

    val totalSessions = sessions.size
    val totalVolume = sessions.sumOf { it.totalVolumeKg }
    val totalSets = sessions.sumOf { it.setCount }
    
    val unit = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    val volumeDisplay = if (unitSystem == UnitSystem.IMPERIAL) (totalVolume * 2.20462).toInt() else totalVolume.toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = "Sessions",
                    value = "$totalSessions",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Total Sets",
                    value = "$totalSets",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Cumulative",
                    value = "$volumeDisplay $unit",
                    modifier = Modifier.weight(1f)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            val thirtyDaysAgo = LocalDate.now().minusDays(30)
            val recentSessionsCount = sessions.count { runCatching { LocalDate.parse(it.date) }.getOrNull()?.isAfter(thirtyDaysAgo) ?: false }
            val weeklyAvg = (recentSessionsCount / 4.3).let { (it * 10).roundToInt() / 10.0 }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = "Past 30 Days",
                    value = "$recentSessionsCount",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Weekly Avg",
                    value = "$weeklyAvg",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PerformanceContent(sessions: List<WorkoutSession>, unitSystem: UnitSystem) {
    if (sessions.isEmpty()) {
        EmptyStatsCard("Log more sessions to unlock performance insights.")
        return
    }

    val maxVolumeSession = sessions.maxByOrNull { it.totalVolumeKg }
    val unit = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Strength Milestones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            maxVolumeSession?.let {
                val vol = if (unitSystem == UnitSystem.IMPERIAL) (it.totalVolumeKg * 2.20462).toInt() else it.totalVolumeKg.toInt()
                PerformanceRow(
                    icon = Icons.Default.MilitaryTech,
                    label = "Heaviest Session",
                    value = "$vol $unit",
                    date = it.date
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val mostExercises = sessions.maxByOrNull { it.exerciseCount }
            mostExercises?.let {
                PerformanceRow(
                    icon = Icons.Default.Layers,
                    label = "Most Variety",
                    value = "${it.exerciseCount} exercises",
                    date = it.date
                )
            }
        }
    }
}

@Composable
private fun PerformanceRow(
    icon: ImageVector,
    label: String,
    value: String,
    date: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = valueColor, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyStatsCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
