package com.eliteonetube.momentum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.AppTheme
import com.eliteonetube.momentum.data.CheckIn
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
    allCheckIns: List<CheckIn> = emptyList(),
    onWeightClick: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onGoalChanged: (Goal) -> Unit,
    onViewGallery: () -> Unit
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
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Your Profile",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Personal stats & settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { isEditing = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Edit Profile")
            }
        }

        // 1. Body Composition Block
        ProfileBlock(title = "Body Composition", icon = Icons.Default.MonitorWeight) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Weight",
                    value = if (currentWeightKg > 0) Units.displayWeight(currentWeightKg, profile.unitSystem) else "--",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onWeightClick
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Body Fat",
                    value = if (profile.bodyFatPercentage != null) "${profile.bodyFatPercentage}%" else "--",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }

        // 2. Physical Details Block
        ProfileBlock(title = "Physical Details", icon = Icons.Default.Person) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatRow(
                    icon = Icons.Default.Straighten,
                    label = "Height",
                    value = Units.displayHeight(profile.height, profile.unitSystem)
                )
                ProfileStatRow(
                    icon = Icons.Default.Cake,
                    label = "Age",
                    value = "${profile.age} years"
                )
                ProfileStatRow(
                    icon = Icons.Default.Wc,
                    label = "Sex",
                    value = if (profile.isMale) "Male" else "Female"
                )
            }
        }

        // 3. Activity & Connectivity Block
        ProfileBlock(title = "Activity & Integration", icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatRow(
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    label = "Daily Steps",
                    value = "${profile.averageDailySteps}"
                )
                ProfileStatRow(
                    icon = Icons.Default.HealthAndSafety,
                    label = "Health Connect",
                    value = if (profile.useHealthConnect) "Connected" else "Disconnected",
                    valueColor = if (profile.useHealthConnect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                ProfileStatRow(
                    icon = Icons.Default.Tune,
                    label = "Units",
                    value = if (profile.unitSystem == UnitSystem.IMPERIAL) "Imperial" else "Metric"
                )
            }
        }

        // 4. Goal & Metabolism Block
        ProfileBlock(title = "Goal & Metabolism", icon = Icons.Default.LocalFireDepartment) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangeGoalDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Current Goal", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    profile.goal.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimated Maintenance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${profile.estimatedMaintenanceCalories} kcal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 5. Progress Gallery Block
        if (allCheckIns.any { (it.frontPhotoPath != null) || (it.backPhotoPath != null) || (it.sidePhotoPath != null) }) {
            ProfileBlock(title = "Progress Gallery", icon = Icons.Default.PhotoLibrary) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Track your visual transformation over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onViewGallery,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Compare, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View & Compare Photos")
                    }
                }
            }
        }

        // 6. App Settings Block
        ProfileBlock(title = "App Settings", icon = Icons.Default.SettingsSuggest) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppTheme.entries.forEachIndexed { index, theme ->
                        SegmentedButton(
                            selected = profile.theme == theme,
                            onClick = { onProfileUpdated(profile.copy(theme = theme)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size)
                        ) {
                            Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileBlock(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .height(90.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ProfileStatRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
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
        title = { Text("Change Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goalOptions.forEach { option ->
                    val isSelected = selectedGoal == option.goal
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGoal = option.goal },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = if (!isSelected) CardDefaults.outlinedCardBorder() else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedGoal = option.goal })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(option.label, fontWeight = FontWeight.Bold)
                                Text(option.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (selectedGoal != currentProfile.goal) {
                    Card(
                        modifier = Modifier.padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            transition.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedGoal) },
                enabled = selectedGoal != currentProfile.goal,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Change")
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
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Edit Profile",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            "Update your parameters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Imperial Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("lb, ft/in", style = MaterialTheme.typography.bodySmall)
                }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = heightInchesInput,
                    onValueChange = { heightInchesInput = it },
                    label = { Text("Height (in)") },
                    isError = heightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = heightCmInput,
                onValueChange = { heightCmInput = it },
                label = { Text("Height (cm)") },
                isError = heightError,
                supportingText = { if (heightError) Text("Enter a valid number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age") },
                isError = ageError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = bodyFatInput,
                onValueChange = { bodyFatInput = it },
                label = { Text("Body Fat %") },
                isError = bfError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = stepsInput,
            onValueChange = { stepsInput = it },
            label = { Text("Average Daily Steps") },
            isError = stepsError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biological Sex", fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = isMale,
                    onClick = { isMale = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Male") }
                SegmentedButton(
                    selected = !isMale,
                    onClick = { isMale = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Female") }
            }
        }

        if (healthConnectManager.isAvailable()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Health Connect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Sync steps automatically", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = useHealthConnect,
                        onCheckedChange = { useHealthConnect = it }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
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
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        }
    }
}
