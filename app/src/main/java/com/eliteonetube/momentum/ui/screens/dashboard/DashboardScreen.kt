package com.eliteonetube.momentum.ui.screens.dashboard

import com.eliteonetube.momentum.ui.components.PrimaryButton
import com.eliteonetube.momentum.ui.components.SecondaryButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.FoodLogWithItem
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.ui.components.BigStat
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.ui.components.bounceClick
import com.eliteonetube.momentum.ui.components.rememberCurrentDate
import com.eliteonetube.momentum.ui.screens.nutrition.MacroTargets
import com.eliteonetube.momentum.ui.screens.nutrition.MacroTargetsUnavailable
import com.eliteonetube.momentum.ui.theme.CarbGreen
import com.eliteonetube.momentum.ui.theme.FatYellow
import com.eliteonetube.momentum.ui.theme.ProteinBlue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun MainDashboard(
    calorieTarget: Int,
    recentWeights: List<WeightEntry>,
    profile: UserProfile,
    currentStreak: Int,
    totalDaysLogged: Int,
    loggedDates: Set<LocalDate>,
    todayLogs: List<FoodLogWithItem>,
    onOpenNutrition: () -> Unit,
    onWeightSubmitted: (Double) -> Unit,
    onAdjustmentDismissed: () -> Unit,
    onAdjustmentAccepted: () -> Unit,
    onStartCheckIn: () -> Unit
) {
    val currentDate by rememberCurrentDate()
    val today = currentDate.toString()
    val todayEntry = remember(recentWeights, today) { recentWeights.firstOrNull { it.date == today } }
    val unitLabel = if (profile.unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    
    // Due once Monday's review has run; the 8-day fallback covers a week where the background job
    // never got to run (phone off, battery saver)
    val daysSinceCheckIn = profile.lastCheckInDate?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), currentDate) }.getOrNull() }
    val showCheckInAlert = profile.checkInDue || (daysSinceCheckIn != null && daysSinceCheckIn >= 8)

    var weightInput by remember { mutableStateOf("") }
    var isEditingToday by remember { mutableStateOf(false) }

    val parsedInput = weightInput.replace(',', '.').toDoubleOrNull()
    val weightError = weightInput.isNotBlank() && parsedInput == null
    val parsedWeightKg = parsedInput?.let {
        if (profile.unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it
    }
    val isDark = isSystemInDarkTheme()

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
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(top = 48.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                TodayCaloriesSummary(
                    calorieTarget = calorieTarget,
                    todayLogs = todayLogs,
                    weightKg = recentWeights.firstOrNull()?.weight,
                    onClick = onOpenNutrition
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Notifications / Check-in Alerts
                if (showCheckInAlert) {
                    DashboardAlert(
                        title = "Weekly Review Ready",
                        description = "Log today's weight and I'll compare this week with last week to set your plan.",
                        actionText = "Start Review",
                        onAction = onStartCheckIn,
                        secondaryActionText = "Skip this week",
                        onSecondaryAction = onAdjustmentDismissed,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else if (profile.pendingAdjustmentReason != null) {
                    DashboardAlert(
                        title = "Adjustment Recommended",
                        description = profile.pendingAdjustmentReason,
                        actionText = profile.pendingCalorieTarget?.let { "Update to $it kcal" } ?: "Got it",
                        onAction = onAdjustmentAccepted,
                        secondaryActionText = if (profile.pendingCalorieTarget != null) "Dismiss" else null,
                        onSecondaryAction = onAdjustmentDismissed,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }

                SectionDivider()

                // Weight Section
                SectionLabel("WEIGHT")
                if (todayEntry != null && !isEditingToday) {
                    val previousEntry = remember(recentWeights, today) { recentWeights.firstOrNull { it.date != today } }
                    WeightLoggedCard(
                        weightKg = todayEntry.weight,
                        previousEntry = previousEntry,
                        unitSystem = profile.unitSystem,
                        showReview = showCheckInAlert,
                        onReview = onStartCheckIn,
                        onEdit = {
                            weightInput = if (profile.unitSystem == UnitSystem.IMPERIAL) {
                                "%.1f".format(Units.kgToLb(todayEntry.weight))
                            } else {
                                todayEntry.weight.toString()
                            }
                            isEditingToday = true
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (todayEntry != null) "Update today's weight" else "Log today's weight",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            placeholder = { Text("0.0") },
                            suffix = { Text(unitLabel) },
                            isError = weightError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Black),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                // Without a card around it, the field itself needs a fill to stand out
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isEditingToday) {
                                SecondaryButton(
                                    onClick = { isEditingToday = false; weightInput = "" },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel") }
                            }
                            PrimaryButton(
                                onClick = {
                                    parsedWeightKg?.let {
                                        if (profile.checkInDue) {
                                            onWeightSubmitted(it)
                                            onStartCheckIn()
                                        } else {
                                            onWeightSubmitted(it)
                                            weightInput = ""
                                            isEditingToday = false
                                        }
                                    }
                                },
                                enabled = parsedWeightKg != null,
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(if (profile.checkInDue) "Review & Save" else "Save Weight")
                            }
                        }
                    }
                }

                SectionDivider()

                // Streak Section
                StreakCard(
                    currentStreak = currentStreak,
                    totalDaysLogged = totalDaysLogged,
                    loggedDates = loggedDates
                )
            }
        }
    }
}

/** Calorie ring (eaten vs target, kcal left in the middle) with today's macro bars underneath. */
@Composable
private fun TodayCaloriesSummary(
    calorieTarget: Int,
    todayLogs: List<FoodLogWithItem>,
    weightKg: Double?,
    onClick: () -> Unit
) {
    val eaten = remember(todayLogs) { todayLogs.sumOf { it.calories * it.quantity }.roundToInt() }
    val protein = remember(todayLogs) { todayLogs.sumOf { it.protein * it.quantity }.roundToInt() }
    val fat = remember(todayLogs) { todayLogs.sumOf { it.fat * it.quantity }.roundToInt() }
    val carbs = remember(todayLogs) { todayLogs.sumOf { it.carbs * it.quantity }.roundToInt() }
    val targets = remember(calorieTarget, weightKg) { MacroTargets.from(calorieTarget, weightKg) }

    val remaining = calorieTarget - eaten
    val isOver = remaining < 0
    val progress by animateFloatAsState(
        targetValue = if (calorieTarget > 0) (eaten.toFloat() / calorieTarget).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "calorieRing"
    )
    val ringColor = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClickLabel = "Open nutrition", onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            "TODAY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isOver) "${-remaining} kilocalories over today's target of $calorieTarget"
                    else "$remaining of $calorieTarget kilocalories left today"
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(trackColor, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (progress > 0f) {
                    drawArc(ringColor, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%,d".format(if (isOver) -remaining else remaining),
                    style = MaterialTheme.typography.displayMedium.copy(letterSpacing = (-2).sp),
                    fontWeight = FontWeight.Black,
                    color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (isOver) "kcal over" else "kcal left",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "%,d eaten  ·  %,d target".format(eaten, calorieTarget),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        // Compact glance only — the full macro bars live on the Nutrition tab
        if (targets != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MacroGlance("Protein", protein, targets.proteinGrams, ProteinBlue, Modifier.weight(1f))
                MacroGlance("Fats", fat, targets.fatGrams, FatYellow, Modifier.weight(1f))
                MacroGlance("Carbs", carbs, targets.carbGrams, CarbGreen, Modifier.weight(1f))
            }
        } else {
            MacroTargetsUnavailable()
        }
    }
}





@Composable
private fun WeightLoggedCard(
    weightKg: Double,
    previousEntry: WeightEntry?,
    unitSystem: UnitSystem,
    showReview: Boolean,
    onReview: () -> Unit,
    onEdit: () -> Unit
) {
    val isImperial = unitSystem == UnitSystem.IMPERIAL
    fun display(kg: Double) = if (isImperial) Units.kgToLb(kg) else kg
    val unit = if (isImperial) "lb" else "kg"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigStat("%.1f".format(display(weightKg)), unit)
                if (previousEntry != null) {
                    val change = display(weightKg) - display(previousEntry.weight)
                    val since = runCatching {
                        LocalDate.parse(previousEntry.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                    }.getOrDefault(previousEntry.date)
                    Text(
                        when {
                            change > 0.05 -> "▲ %.1f %s since %s".format(change, unit, since)
                            change < -0.05 -> "▼ %.1f %s since %s".format(-change, unit, since)
                            else -> "No change since $since"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showReview) {
                Button(onClick = onReview, shape = RoundedCornerShape(16.dp)) {
                    Text("Review", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            TextButton(onClick = onEdit) { Text("Edit", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MacroGlance(label: String, current: Int, target: Int, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "${current}g",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "of ${target}g",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun DashboardAlert(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    containerColor: Color
) {
    val isDark = isSystemInDarkTheme()
    val bgAlpha = if (isDark) 0.25f else 0.18f
    val borderAlpha = if (isDark) 0.4f else 0.25f
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = bgAlpha)),
        border = BorderStroke(1.dp, containerColor.copy(alpha = borderAlpha))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = containerColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = containerColor)
                ) {
                    Text(actionText, fontWeight = FontWeight.Bold)
                }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    TextButton(onClick = onSecondaryAction) {
                        Text(secondaryActionText)
                    }
                }
            }
        }
    }
}
