package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.Units
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
) {
    val today = remember { LocalDate.now().toString() }
    val todayEntry = recentWeights.firstOrNull { it.date == today }
    val unitLabel = if (profile.unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"

    var weightInput by remember { mutableStateOf("") }
    var isEditingToday by remember { mutableStateOf(false) }

    val parsedInput = weightInput.replace(',', '.').toDoubleOrNull()
    val weightError = weightInput.isNotBlank() && parsedInput == null
    val parsedWeightKg = parsedInput?.let {
        if (profile.unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (profile.pendingAdjustmentReason != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Weekly check-in", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(profile.pendingAdjustmentReason, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (profile.pendingCalorieTarget != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAdjustmentAccepted) {
                                Text("Update to ${profile.pendingCalorieTarget} kcal")
                            }
                            OutlinedButton(onClick = onAdjustmentDismissed) {
                                Text("Keep current")
                            }
                        }
                    } else {
                        TextButton(onClick = onAdjustmentDismissed) {
                            Text("Got it")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            "CURRENT CALORIES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$calorieTarget",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "kcal per day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (todayEntry != null && !isEditingToday) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Logged ${Units.displayWeight(todayEntry.weight, profile.unitSystem)} today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        weightInput = if (profile.unitSystem == UnitSystem.IMPERIAL) {
                            "%.1f".format(Units.kgToLb(todayEntry.weight))
                        } else {
                            todayEntry.weight.toString()
                        }
                        isEditingToday = true
                    }) {
                        Text("Edit today's entry")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (todayEntry != null) "Update today's weight" else "Log today's weight",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text(unitLabel) },
                        isError = weightError,
                        supportingText = { if (weightError) Text("Enter a valid number, e.g. 70.8") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isEditingToday) {
                            OutlinedButton(onClick = {
                                isEditingToday = false
                                weightInput = ""
                            }) {
                                Text("Cancel")
                            }
                        }
                        Button(
                            onClick = {
                                parsedWeightKg?.let {
                                    onWeightSubmitted(it)
                                    weightInput = ""
                                    isEditingToday = false
                                }
                            },
                            enabled = parsedWeightKg != null
                        ) {
                            Text("Submit Weight Entry")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        StreakCard(
            currentStreak = currentStreak,
            totalDaysLogged = totalDaysLogged,
            loggedDates = loggedDates
        )
    }
}