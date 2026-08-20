package com.eliteonetube.momentum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.Units
import com.eliteonetube.momentum.ui.theme.bounceClick
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MainDashboard(
    calorieTarget: Int,
    recentWeights: List<WeightEntry>,
    profile: UserProfile,
    currentStreak: Int,
    totalDaysLogged: Int,
    loggedDates: Set<LocalDate>,
    onWeightSubmitted: (Double) -> Unit,
    onAdjustmentDismissed: () -> Unit,
    onAdjustmentAccepted: () -> Unit,
    onStartCheckIn: () -> Unit
) {
    val today = remember { LocalDate.now().toString() }
    val todayEntry = remember(recentWeights, today) { recentWeights.firstOrNull { it.date == today } }
    val unitLabel = if (profile.unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "DAILY TARGET",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$calorieTarget",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp,
                            letterSpacing = (-4).sp
                        ),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "kcal per day",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Notifications / Check-in Alerts
                if (profile.checkInDue) {
                    DashboardAlert(
                        title = "Weekly Check-in Due",
                        description = "Time to log your weight and progress photos.",
                        actionText = "Start Now",
                        onAction = onStartCheckIn,
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

                Spacer(modifier = Modifier.height(24.dp))

                // Weight Entry Card
                Text(
                    "Weight Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                if (todayEntry != null && !isEditingToday) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Today's Weight",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    Units.displayWeight(todayEntry.weight, profile.unitSystem),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            IconButton(
                                onClick = {
                                    weightInput = if (profile.unitSystem == UnitSystem.IMPERIAL) {
                                        "%.1f".format(Units.kgToLb(todayEntry.weight))
                                    } else {
                                        todayEntry.weight.toString()
                                    }
                                    isEditingToday = true
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                if (todayEntry != null) "Update Entry" else "Log Entry",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { weightInput = it },
                                placeholder = { Text("0.0") },
                                suffix = { Text(unitLabel) },
                                isError = weightError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (isEditingToday) {
                                    OutlinedButton(
                                        onClick = { isEditingToday = false; weightInput = "" },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Cancel") }
                                }
                                Button(
                                onClick = {
                                    parsedWeightKg?.let {
                                        onWeightSubmitted(it)
                                        weightInput = ""
                                        isEditingToday = false
                                    }
                                },
                                enabled = parsedWeightKg != null,
                                modifier = Modifier.weight(1.5f).height(50.dp).bounceClick(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Weight", fontWeight = FontWeight.Bold)
                            }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor.copy(alpha = borderAlpha))
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
