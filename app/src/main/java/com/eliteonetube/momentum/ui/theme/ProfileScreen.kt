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
import androidx.compose.ui.graphics.Brush
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
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Section
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
                .padding(top = 48.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manage your metrics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stats & Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onViewGallery,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Compare, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View & Compare Photos", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. App Settings Block
            ProfileBlock(title = "App Settings", icon = Icons.Default.SettingsSuggest) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online Barcode Lookup", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Search Open Food Facts if barcode is not found locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = profile.useExternalApi,
                            onCheckedChange = { onProfileUpdated(profile.copy(useExternalApi = it)) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text("App Theme", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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

            Spacer(modifier = Modifier.height(120.dp)) // Space for floating nav bar
        }
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
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
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
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
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
            .height(100.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = containerColor, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, fontSize = 24.sp)
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
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
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
        title = { Text("Update Strategy", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedGoal = option.goal })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(option.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (selectedGoal != currentProfile.goal) {
                    Card(
                        modifier = Modifier.padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            transition.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedGoal) },
                enabled = selectedGoal != currentProfile.goal,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm Change", fontWeight = FontWeight.Bold)
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
            .verticalScroll(rememberScrollState())
    ) {
        // Form Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)))
                .padding(top = 48.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EDIT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Profile", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Unit Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Imperial Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("lb, ft/in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = unitSystem == UnitSystem.IMPERIAL,
                        onCheckedChange = { unitSystem = if (it) UnitSystem.IMPERIAL else UnitSystem.METRIC }
                    )
                }
            }

            // Height
            if (unitSystem == UnitSystem.IMPERIAL) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = heightFeetInput,
                        onValueChange = { heightFeetInput = it },
                        label = { Text("Feet") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = heightInchesInput,
                        onValueChange = { heightInchesInput = it },
                        label = { Text("Inches") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
            } else {
                OutlinedTextField(
                    value = heightCmInput,
                    onValueChange = { heightCmInput = it },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    isError = heightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
            }

            // Age & BF
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    isError = ageError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = bodyFatInput,
                    onValueChange = { bodyFatInput = it },
                    label = { Text("Body Fat %") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    isError = bfError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
            }

            // Steps
            OutlinedTextField(
                value = stepsInput,
                onValueChange = { stepsInput = it },
                label = { Text("Average Daily Steps") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                isError = stepsError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )

            // Sex
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Biological Sex", fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = isMale, onClick = { isMale = true }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Male") }
                    SegmentedButton(selected = !isMale, onClick = { isMale = false }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Female") }
                }
            }

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(profile.copy(height = parsedHeightCm!!, age = parsedAge!!, isMale = isMale, averageDailySteps = parsedSteps!!, unitSystem = unitSystem, bodyFatPercentage = parsedBodyFat, useHealthConnect = useHealthConnect))
                    },
                    enabled = allValid,
                    modifier = Modifier.weight(1.5f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Save Changes", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
