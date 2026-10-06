package com.eliteonetube.momentum.ui.screens.statistics

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.DailyNutrition
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.data.WorkoutStats
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.ui.components.BigStat
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.ui.components.VolumeTrendChart
import com.eliteonetube.momentum.ui.components.WeightTrendChart
import com.eliteonetube.momentum.ui.components.rememberCurrentDate
import com.eliteonetube.momentum.ui.screens.dashboard.MascotMood
import com.eliteonetube.momentum.ui.screens.dashboard.MomentumMascot
import com.eliteonetube.momentum.ui.theme.CarbGreen
import com.eliteonetube.momentum.ui.theme.FatYellow
import com.eliteonetube.momentum.ui.theme.ProteinBlue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StatisticsScreen(
    weights: List<WeightEntry>,
    recentSessions: List<WorkoutSession>,
    recentNutrition: List<DailyNutrition> = emptyList(),
    workoutStats: WorkoutStats? = null,
    goal: Goal,
    unitSystem: UnitSystem
) {
    var mascotMood by remember { mutableStateOf(MascotMood.IDLE) }
    var scrubText by remember { mutableStateOf<String?>(null) }
    var helperMessage by remember { mutableStateOf<String?>(null) }
    val isDark = isSystemInDarkTheme()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Hero Section — same gradient as the Dashboard hero
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
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

                    Text(
                        text = scrubText ?: "Analytics",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = if (scrubText != null) 32.sp else 44.sp),
                        fontWeight = FontWeight.Black,
                        color = if (scrubText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = if (scrubText != null) "" else "YOUR LONG-TERM PROGRESS",
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
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Weight
                SectionLabel("WEIGHT")
                WeightTrendChart(
                    entries = weights,
                    unitSystem = unitSystem,
                    onScrub = { entry ->
                        if (entry != null) {
                            val olderEntry = weights.getOrNull(weights.indexOf(entry) + 1)
                            val dailyChange = olderEntry?.let { entry.weight - it.weight }
                            val progress = dailyChange?.let { weightProgress(it, goal) }
                            helperMessage = when {
                                dailyChange == null -> "Every data point helps me optimize your plan."
                                abs(dailyChange) <= 0.2 -> "Holding steady. Consistency is our secret weapon."
                                progress == true -> "Moving the right way! Persistence pays off."
                                progress == false -> "Don't sweat a daily shift—it's usually just water."
                                else -> "Daily swings are normal — the weekly trend is what counts."
                            }
                            mascotMood = if (progress == true) MascotMood.HAPPY else MascotMood.IDLE
                            scrubText = Units.displayWeight(entry.weight, unitSystem)
                        } else {
                            mascotMood = MascotMood.IDLE
                            scrubText = null
                            helperMessage = null
                        }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                WeightStatsContent(weights, goal, unitSystem)

                SectionDivider()

                // Training
                SectionLabel("TRAINING")
                val avgVolume = remember(recentSessions) {
                    if (recentSessions.isNotEmpty()) recentSessions.map { it.totalVolumeKg }.average() else 0.0
                }
                VolumeTrendChart(
                    sessions = recentSessions,
                    unitSystem = unitSystem,
                    onScrub = { session ->
                        if (session != null) {
                            helperMessage = when {
                                session.totalVolumeKg > avgVolume * 1.2 -> "Incredible session! You're building serious strength."
                                session.totalVolumeKg < avgVolume * 0.8 -> "Every bit of movement counts toward the goal."
                                else -> "Solid training day. Great job showing up!"
                            }
                            mascotMood = if (session.totalVolumeKg >= avgVolume) MascotMood.HAPPY else MascotMood.IDLE
                            scrubText = formatVolume(session.totalVolumeKg, unitSystem)
                        } else {
                            mascotMood = MascotMood.IDLE
                            scrubText = null
                            helperMessage = null
                        }
                    }
                )
                if (recentSessions.isNotEmpty()) {
                    Text(
                        "Chart shows your last ${recentSessions.size} workouts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                WorkoutStatsContent(workoutStats, unitSystem)

                SectionDivider()

                // Records
                SectionLabel("RECORDS")
                RecordsContent(workoutStats, unitSystem)

                SectionDivider()

                // Nutrition
                SectionLabel("NUTRITION")
                NutritionStatsContent(recentNutrition)
            }
        }
    }
}

/** true = moving toward the goal, false = away from it, null = no direction to judge (maintain/reverse or flat). */
private fun weightProgress(changeKg: Double, goal: Goal): Boolean? = when {
    abs(changeKg) < 0.05 -> null
    goal == Goal.CUT -> changeKg < 0
    goal == Goal.BULK -> changeKg > 0
    else -> null
}

private fun formatVolume(kg: Double, unitSystem: UnitSystem): String {
    val value = if (unitSystem == UnitSystem.IMPERIAL) Units.kgToLb(kg) else kg
    val unit = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    return if (value >= 100_000) "%.0fk %s".format(value / 1000, unit) else "%,d %s".format(value.roundToInt(), unit)
}

private val readableDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private val shortDay = DateTimeFormatter.ofPattern("EEE d")

private fun formatDate(iso: String, formatter: DateTimeFormatter): String =
    runCatching { LocalDate.parse(iso).format(formatter) }.getOrDefault(iso)

@Composable
private fun WeightStatsContent(weights: List<WeightEntry>, goal: Goal, unitSystem: UnitSystem) {
    if (weights.isEmpty()) {
        EmptyStatsText("No weight data logged yet.")
        return
    }

    val isImperial = unitSystem == UnitSystem.IMPERIAL
    val unit = if (isImperial) "lb" else "kg"
    fun display(kg: Double) = if (isImperial) Units.kgToLb(kg) else kg

    val currentWeight = weights.first().weight
    val startWeight = weights.last().weight
    val diff = currentWeight - startWeight
    val changeColor = when (weightProgress(diff, goal)) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text("Current", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BigStat("%.1f".format(display(currentWeight)), unit)
    val changeDisplay = abs(display(currentWeight) - display(startWeight))
    Text(
        when {
            abs(diff) < 0.05 -> "Same as when you started"
            diff < 0 -> "▼ %.1f %s since you started".format(changeDisplay, unit)
            else -> "▲ %.1f %s since you started".format(changeDisplay, unit)
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = changeColor
    )

    Spacer(modifier = Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatItem("Start", Units.displayWeight(startWeight, unitSystem), Modifier.weight(1f))
        StatItem("Lowest", Units.displayWeight(weights.minOf { it.weight }, unitSystem), Modifier.weight(1f))
        StatItem("Highest", Units.displayWeight(weights.maxOf { it.weight }, unitSystem), Modifier.weight(1f))
    }
}

@Composable
private fun WorkoutStatsContent(stats: WorkoutStats?, unitSystem: UnitSystem) {
    val totals = stats?.totals ?: return
    if (totals.sessions == 0) {
        EmptyStatsText("No workouts logged yet.")
        return
    }

    val weeklyAvg = (totals.sessionsLast30Days / (30 / 7.0)).let { (it * 10).roundToInt() / 10.0 }

    Text("All time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BigStat("%,d".format(totals.sessions), if (totals.sessions == 1) "workout" else "workouts")
    Spacer(modifier = Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatItem("Sets", "%,d".format(totals.sets), Modifier.weight(1f))
        StatItem("Volume", formatVolume(totals.volumeKg, unitSystem), Modifier.weight(1.3f))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatItem("Last 30 days", "${totals.sessionsLast30Days}", Modifier.weight(1f))
        StatItem("Per week", "$weeklyAvg", Modifier.weight(1.3f))
    }
}

@Composable
private fun RecordsContent(stats: WorkoutStats?, unitSystem: UnitSystem) {
    if (stats == null) return
    val heaviest = stats.heaviest
    val mostVaried = stats.mostVaried
    if (heaviest == null && mostVaried == null) {
        EmptyStatsText("Log more sessions to unlock your records.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        heaviest?.let {
            RecordRow(Icons.Default.MilitaryTech, "Heaviest session", formatVolume(it.totalVolumeKg, unitSystem), formatDate(it.date, readableDate))
        }
        mostVaried?.let {
            RecordRow(Icons.Default.Layers, "Most variety", "${it.exerciseCount} exercises", formatDate(it.date, readableDate))
        }
    }
}

@Composable
private fun RecordRow(icon: ImageVector, label: String, value: String, date: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NutritionStatsContent(recentNutrition: List<DailyNutrition>) {
    if (recentNutrition.isEmpty()) {
        EmptyStatsText("No nutrition data logged yet.")
        return
    }

    val today by rememberCurrentDate()
    // Average over the last 7 finished days that have food logged — today is usually still incomplete
    val completedDays = remember(recentNutrition, today) {
        recentNutrition.filter { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@filter false
            date.isBefore(today) && !date.isBefore(today.minusDays(7))
        }
    }

    if (completedDays.isEmpty()) {
        EmptyStatsText("Log a full day of food to see your weekly average.")
    } else {
        Text(
            "Daily average · last 7 days (${completedDays.size} logged)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BigStat("%,d".format(completedDays.map { it.totalCalories }.average().roundToInt()), "kcal")
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MacroAverage("Protein", completedDays.map { it.totalProtein }.average(), ProteinBlue, Modifier.weight(1f))
            MacroAverage("Carbs", completedDays.map { it.totalCarbs }.average(), CarbGreen, Modifier.weight(1f))
            MacroAverage("Fats", completedDays.map { it.totalFat }.average(), FatYellow, Modifier.weight(1f))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Recent days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(4.dp))
    recentNutrition.take(7).forEach { daily ->
        val isToday = daily.date == today.toString()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isToday) "Today" else formatDate(daily.date, shortDay),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${daily.totalProtein.roundToInt()}P  ${daily.totalCarbs.roundToInt()}C  ${daily.totalFat.roundToInt()}F",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "%,d kcal".format(daily.totalCalories.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@Composable
private fun MacroAverage(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${grams.roundToInt()}g", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun EmptyStatsText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
