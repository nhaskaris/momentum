package com.eliteonetube.momentum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.CoachAlgorithm
import com.eliteonetube.momentum.logic.HealthConnectManager
import com.eliteonetube.momentum.logic.Units

private data class GoalOption(val goal: Goal, val label: String, val description: String)

private val goalOptions = listOf(
    GoalOption(Goal.CUT, "Cut", "Lose fat in a calorie deficit while holding onto muscle."),
    GoalOption(Goal.BULK, "Bulk", "Build muscle in a lean surplus."),
    GoalOption(Goal.MAINTAIN, "Maintain", "Hold your current weight steady."),
    GoalOption(Goal.REVERSE, "Reverse", "Gradually climb calories back toward maintenance after a cut.")
)

@Composable
fun ProfileScreen(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    onWeightClick: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onGoalChanged: (Goal) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showChangeGoalDialog by remember { mutableStateOf(false) }

    if (isEditing) {
        EditProfileForm(
            profile = profile,
            onCancel = { isEditing = false },
            onSave = {
                onProfileUpdated(it)
                isEditing = false
            }
        )
        return
    }

    if (showChangeGoalDialog) {
        ChangeGoalDialog(
            currentProfile = profile,
            onDismiss = { showChangeGoalDialog = false },
            onConfirm = { newGoal ->
                onGoalChanged(newGoal)
                showChangeGoalDialog = false
            }
        )
    }

    val currentWeightKg = recentWeights.firstOrNull()?.weight ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Profile", style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = { isEditing = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable { onWeightClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Weight (Click for History)", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (currentWeightKg > 0) Units.displayWeight(currentWeightKg, profile.unitSystem) else "No entries yet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileStatRow(label = "Height", value = Units.displayHeight(profile.height, profile.unitSystem))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow(label = "Age", value = "${profile.age} years old")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow(label = "Biological Sex", value = if (profile.isMale) "Male" else "Female")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow(label = "Average Daily Steps", value = "${profile.averageDailySteps} steps")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProfileStatRow(
                    label = "Health Connect",
                    value = if (profile.useHealthConnect) "Connected" else "Disconnected"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow(
                    label = "Units",
                    value = if (profile.unitSystem == UnitSystem.IMPERIAL) "Imperial (lb / ft-in)" else "Metric (kg / cm)"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProfileStatRow(
                    label = "Body Fat",
                    value = if (profile.bodyFatPercentage != null) "${profile.bodyFatPercentage}%" else "Not provided"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProfileStatRow(label = "Goal", value = profile.goal.name.lowercase().replaceFirstChar { it.uppercase() })
                TextButton(
                    onClick = { showChangeGoalDialog = true },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("Change goal", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow(label = "Estimated Maintenance", value = "${profile.estimatedMaintenanceCalories} kcal")
            }
        }
    }
}

@Composable
fun ProfileStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChangeGoalDialog(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onConfirm: (Goal) -> Unit
) {
    var selectedGoal by remember { mutableStateOf(currentProfile.goal) }
    val algorithm = remember { CoachAlgorithm() }

    val transition = remember(selectedGoal) {
        algorithm.calculateGoalTransition(
            previousGoal = currentProfile.goal,
            newGoal = selectedGoal,
            currentCalorieTarget = currentProfile.currentCalorieTarget,
            estimatedMaintenance = currentProfile.estimatedMaintenanceCalories
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Goal") },
        text = {
            Column {
                goalOptions.forEach { option ->
                    val isSelected = selectedGoal == option.goal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGoal = option.goal }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedGoal = option.goal })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(option.label, fontWeight = FontWeight.Bold)
                            Text(option.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (selectedGoal != currentProfile.goal) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        transition.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedGoal) },
                enabled = selectedGoal != currentProfile.goal
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditProfileForm(
    profile: UserProfile,
    onCancel: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var unitSystem by remember { mutableStateOf(profile.unitSystem) }

    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    var useHealthConnect by remember { mutableStateOf(profile.useHealthConnect) }

    // Height input state depends on unit system — cm as a single field, or feet+inches as two
    val initialFeetInches = remember { Units.cmToFeetInches(profile.height) }
    var heightCmInput by remember { mutableStateOf(profile.height.toString()) }
    var heightFeetInput by remember { mutableStateOf(initialFeetInches.first.toString()) }
    var heightInchesInput by remember { mutableStateOf(initialFeetInches.second.toString()) }

    var age by remember { mutableStateOf(profile.age.toString()) }
    var bodyFatInput by remember { mutableStateOf(profile.bodyFatPercentage?.toString() ?: "") }
    var isMale by remember { mutableStateOf(profile.isMale) }
    var stepsInput by remember { mutableStateOf(profile.averageDailySteps.toString()) }

    val parsedAge = age.toIntOrNull()
    val parsedBodyFat = bodyFatInput.replace(',', '.').toDoubleOrNull()
    val parsedSteps = stepsInput.toIntOrNull()
    val ageError = age.isNotBlank() && parsedAge == null
    val bfError = bodyFatInput.isNotBlank() && parsedBodyFat == null
    val stepsError = stepsInput.isNotBlank() && parsedSteps == null

    val parsedHeightCm: Double? = if (unitSystem == UnitSystem.IMPERIAL) {
        val ft = heightFeetInput.toIntOrNull()
        val inch = heightInchesInput.toIntOrNull()
        if (ft != null && inch != null) Units.feetInchesToCm(ft, inch) else null
    } else {
        heightCmInput.replace(',', '.').toDoubleOrNull()
    }
    val heightError = if (unitSystem == UnitSystem.IMPERIAL) {
        (heightFeetInput.isNotBlank() || heightInchesInput.isNotBlank()) && parsedHeightCm == null
    } else {
        heightCmInput.isNotBlank() && parsedHeightCm == null
    }

    val allValid = parsedHeightCm != null && parsedAge != null && parsedSteps != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Edit Profile", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 24.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use imperial units (lb, ft/in)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = unitSystem == UnitSystem.IMPERIAL,
                    onCheckedChange = {
                        unitSystem = if (it) UnitSystem.IMPERIAL else UnitSystem.METRIC
                    }
                )
            }
        }

        if (unitSystem == UnitSystem.IMPERIAL) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = heightFeetInput,
                    onValueChange = { heightFeetInput = it },
                    label = { Text("Height (ft)") },
                    isError = heightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = heightInchesInput,
                    onValueChange = { heightInchesInput = it },
                    label = { Text("Height (in)") },
                    isError = heightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f)
                )
            }
            if (heightError) {
                Text(
                    "Enter valid feet and inches, e.g. 5 ft 10 in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = heightCmInput,
                onValueChange = { heightCmInput = it },
                label = { Text("Height (cm)") },
                isError = heightError,
                supportingText = { if (heightError) Text("Enter a valid number, e.g. 175") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }

        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text("Age") },
            isError = ageError,
            supportingText = { if (ageError) Text("Enter a whole number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = bodyFatInput,
            onValueChange = { bodyFatInput = it },
            label = { Text("Body Fat % (Optional)") },
            isError = bfError,
            supportingText = { if (bfError) Text("Enter a valid number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biological Sex:")
            Button(onClick = { isMale = !isMale }) {
                Text(if (isMale) "Male" else "Female")
            }
        }

        OutlinedTextField(
            value = stepsInput,
            onValueChange = { stepsInput = it },
            label = { Text("Average Daily Steps") },
            isError = stepsError,
            supportingText = { if (stepsError) Text("Enter a whole number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        if (healthConnectManager.isAvailable()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Use Health Connect", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = useHealthConnect,
                        onCheckedChange = { useHealthConnect = it }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            height = parsedHeightCm!!,
                            age = parsedAge!!,
                            isMale = isMale,
                            averageDailySteps = parsedSteps!!,
                            unitSystem = unitSystem,
                            bodyFatPercentage = parsedBodyFat,
                            useHealthConnect = useHealthConnect
                        )
                    )
                },
                enabled = allValid,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}