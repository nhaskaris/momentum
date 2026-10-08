package com.eliteonetube.momentum.ui.screens.profile

import com.eliteonetube.momentum.ui.components.bottomNavClearance
import com.eliteonetube.momentum.ui.components.PrimaryButton
import com.eliteonetube.momentum.ui.components.SecondaryButton
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import com.eliteonetube.momentum.logic.services.WorkScheduler
import com.eliteonetube.momentum.ui.components.BigStat
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.data.AppTheme
import com.eliteonetube.momentum.data.CheckIn
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.algorithm.CoachAlgorithm
import com.eliteonetube.momentum.logic.utils.CrashHandler
import com.eliteonetube.momentum.logic.services.HealthConnectManager
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.logic.parsers.WeightHistoryParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

private data class GoalOption(val goal: Goal, val label: String, val description: String)

private val goalOptions = listOf(
    GoalOption(Goal.CUT, "Cut", "Lose fat in a calorie deficit while holding onto muscle."),
    GoalOption(Goal.BULK, "Bulk", "Build muscle in a lean surplus."),
    GoalOption(Goal.MAINTAIN, "Maintain", "Hold your current weight steady."),
    GoalOption(Goal.REVERSE, "Reverse", "Gradually climb calories back toward maintenance after a cut.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    allWeights: List<WeightEntry> = emptyList(),
    allCheckIns: List<CheckIn> = emptyList(),
    onWeightClick: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onGoalChanged: (Goal) -> Unit,
    onViewGallery: () -> Unit,
    onOpenLearn: () -> Unit = {},
    onWeightsImported: (List<WeightEntry>) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val healthConnectManager = remember { HealthConnectManager(context) }

    var isEditing by remember { mutableStateOf(false) }
    var showChangeGoalDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var detectedWeights by remember { mutableStateOf<List<WeightEntry>?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf(CrashHandler.getCrashReport(context)) }
    var editingReminder by remember { mutableStateOf<WorkScheduler.ReminderSlot?>(null) }

    // Reconcile in case the user revoked access from Health Connect settings directly
    LaunchedEffect(Unit) {
        if (profile.useHealthConnect && !healthConnectManager.hasAllPermissions()) {
            onProfileUpdated(profile.copy(useHealthConnect = false))
        }
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.requestPermissionsContract()
    ) { grantedPermissions ->
        coroutineScope.launch {
            val allGranted = grantedPermissions.containsAll(healthConnectManager.permissions)
            onProfileUpdated(profile.copy(useHealthConnect = allGranted))
        }
    }

    fun connectHealthConnect() {
        when (healthConnectManager.getAvailabilityStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnectPermissionLauncher.launch(healthConnectManager.permissions)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                // Pre-Android 14: Health Connect is a standalone app that needs installing/updating
                try {
                    uriHandler.openUri("market://details?id=com.google.android.apps.healthdata")
                } catch (e: Exception) {
                    uriHandler.openUri("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                }
            }
            else -> {
                // SDK_UNAVAILABLE — device genuinely doesn't support Health Connect
            }
        }
    }

    fun disconnectHealthConnect() {
        // Update local state immediately — Health Connect can report stale
        // grant status within the same app session after a revoke, so we
        // don't wait on or re-verify against getGrantedPermissions().
        onProfileUpdated(profile.copy(useHealthConnect = false))

        coroutineScope.launch {
            try {
                healthConnectManager.revokeAllPermissions()
            } catch (e: Exception) {
                // Revoke best-effort; app-level state is already updated above,
                // and the OS-level grant can still be cleared manually via
                // Health Connect settings if this fails.
            }
            showRestartDialog = true
        }
    }

    fun restartApp() {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessingImage = true
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            var processedCount = 0
            val allDetected = mutableListOf<WeightEntry>()

            uris.forEach { uri ->
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val parsed = WeightHistoryParser.parse(visionText, profile.unitSystem)
                        allDetected.addAll(parsed)
                    }
                    .addOnCompleteListener {
                        processedCount++
                        if (processedCount == uris.size) {
                            if (allDetected.isNotEmpty()) {
                                detectedWeights = allDetected.distinctBy { it.date }.sortedByDescending { it.date }
                            }
                            isProcessingImage = false
                        }
                    }
            }
        }
    }

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

    editingReminder?.let { slot ->
        val current = WorkScheduler.reminderTime(profile, slot)
        val state = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        )
        TimePickerDialog(
            title = if (slot == WorkScheduler.ReminderSlot.MORNING) "Morning reminder" else "Evening reminder",
            onDismiss = { editingReminder = null },
            onConfirm = {
                val picked = "%02d:%02d".format(state.hour, state.minute)
                onProfileUpdated(
                    if (slot == WorkScheduler.ReminderSlot.MORNING) profile.copy(morningReminderTime = picked)
                    else profile.copy(eveningReminderTime = picked)
                )
                editingReminder = null
            }
        ) {
            TimePicker(state = state)
        }
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

    if (showDisconnectDialog) {
        DisconnectHealthConnectDialog(
            onDismiss = { showDisconnectDialog = false },
            onConfirmDisconnect = {
                showDisconnectDialog = false
                disconnectHealthConnect()
            }
        )
    }

    if (showRestartDialog) {
        RestartAppDialog(
            onRestartNow = {
                showRestartDialog = false
                restartApp()
            },
            onLater = { showRestartDialog = false }
        )
    }

    detectedWeights?.let { weights ->
        ConfirmImportDialog(
            weights = weights,
            existingByDate = remember(allWeights) { allWeights.associate { it.date to it.weight } },
            unitSystem = profile.unitSystem,
            onDismiss = { detectedWeights = null },
            onConfirm = { selected ->
                onWeightsImported(selected)
                detectedWeights = null
            }
        )
    }

    val currentWeightKg = recentWeights.firstOrNull()?.weight
    val isImperial = profile.unitSystem == UnitSystem.IMPERIAL
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero — same gradient as the other tabs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.15f else 0.12f),
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
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "YOUR STATS & SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            // YOU
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("YOU", modifier = Modifier.weight(1f))
                TextButton(onClick = { isEditing = true }, modifier = Modifier.offset(y = (-6).dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit", fontWeight = FontWeight.Bold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "Weight history", onClick = onWeightClick)
                ) {
                    Text("Weight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (currentWeightKg != null) {
                        BigStat(
                            "%.1f".format(if (isImperial) Units.kgToLb(currentWeightKg) else currentWeightKg),
                            if (isImperial) "lb" else "kg"
                        )
                    } else {
                        BigStat("--", "")
                    }
                    Text("History ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Body fat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BigStat(profile.bodyFatPercentage?.let { "%.1f".format(it) } ?: "--", if (profile.bodyFatPercentage != null) "%" else "")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ProfileStatRow(Icons.Default.Straighten, "Height", Units.displayHeight(profile.height, profile.unitSystem))
            HorizontalDivider(color = divider)
            ProfileStatRow(Icons.Default.Cake, "Age", "${profile.age} years")
            HorizontalDivider(color = divider)
            ProfileStatRow(Icons.Default.Wc, "Sex", if (profile.isMale) "Male" else "Female")

            SectionDivider()

            // GOAL
            SectionLabel("GOAL")
            SettingsRow(
                icon = Icons.Default.Flag,
                title = profile.goal.name.lowercase().replaceFirstChar { it.uppercase() },
                subtitle = "Tap to change your goal",
                onClick = { showChangeGoalDialog = true },
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            HorizontalDivider(color = divider)
            ProfileStatRow(Icons.Default.LocalFireDepartment, "Estimated maintenance", "%,d kcal".format(profile.estimatedMaintenanceCalories))

            SectionDivider()

            // ACTIVITY
            SectionLabel("ACTIVITY")
            ProfileStatRow(Icons.AutoMirrored.Filled.DirectionsWalk, "Daily steps", "%,d".format(profile.averageDailySteps))
            HorizontalDivider(color = divider)
            SettingsRow(
                icon = Icons.Default.HealthAndSafety,
                title = "Health Connect",
                subtitle = if (profile.useHealthConnect) "Connected — steps sync automatically" else "Not connected",
                trailing = {
                    if (profile.useHealthConnect) {
                        TextButton(
                            onClick = { showDisconnectDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Disconnect") }
                    } else {
                        FilledTonalButton(onClick = { connectHealthConnect() }, shape = RoundedCornerShape(12.dp)) { Text("Connect") }
                    }
                }
            )
            HorizontalDivider(color = divider)
            ProfileStatRow(Icons.Default.Tune, "Units", if (isImperial) "Imperial" else "Metric")

            SectionDivider()

            // DATA
            SectionLabel("DATA")
            SettingsRow(
                icon = Icons.Default.AddPhotoAlternate,
                title = "Import weights from screenshots",
                subtitle = "Read past weigh-ins from another app's history screen",
                onClick = if (isProcessingImage) null else ({ pickerLauncher.launch("image/*") }),
                trailing = {
                    if (isProcessingImage) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )
            if (allCheckIns.any { (it.frontPhotoPath != null) || (it.backPhotoPath != null) || (it.sidePhotoPath != null) }) {
                HorizontalDivider(color = divider)
                SettingsRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Progress photos",
                    subtitle = "View and compare your check-in photos",
                    onClick = onViewGallery,
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            SectionDivider()

            // LEARN
            SectionLabel("LEARN")
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "How Momentum works",
                subtitle = "Targets, weekly adjustments and training tips",
                onClick = onOpenLearn,
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )

            SectionDivider()

            // SETTINGS
            SectionLabel("SETTINGS")
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Weigh-in reminders",
                subtitle = "Only sent if you haven't logged your weight that day",
                trailing = {
                    Switch(checked = profile.remindersEnabled, onCheckedChange = { onProfileUpdated(profile.copy(remindersEnabled = it)) })
                }
            )
            if (profile.remindersEnabled) {
                HorizontalDivider(color = divider)
                SettingsRow(
                    icon = Icons.Default.WbSunny,
                    title = "Morning reminder",
                    subtitle = "Also when your weekly check-in arrives on Mondays",
                    onClick = { editingReminder = WorkScheduler.ReminderSlot.MORNING },
                    trailing = { ReminderTimeText(WorkScheduler.reminderTime(profile, WorkScheduler.ReminderSlot.MORNING)) }
                )
                HorizontalDivider(color = divider)
                SettingsRow(
                    icon = Icons.Default.Bedtime,
                    title = "Evening reminder",
                    onClick = { editingReminder = WorkScheduler.ReminderSlot.EVENING },
                    trailing = { ReminderTimeText(WorkScheduler.reminderTime(profile, WorkScheduler.ReminderSlot.EVENING)) }
                )
            }
            HorizontalDivider(color = divider)
            SettingsRow(
                icon = Icons.Default.QrCodeScanner,
                title = "Online food lookup",
                subtitle = "Search Open Food Facts by name or barcode. Only search words and barcodes are sent.",
                trailing = {
                    Switch(checked = profile.useExternalApi, onCheckedChange = { onProfileUpdated(profile.copy(useExternalApi = it)) })
                }
            )
            HorizontalDivider(color = divider)
            Spacer(modifier = Modifier.height(14.dp))
            Text("Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
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

            crashReport?.let { report ->
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = divider)
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "Crash report available",
                    subtitle = "Sending it helps fix the problem",
                    trailing = {
                        Row {
                            TextButton(onClick = {
                                CrashHandler.clearCrashReport(context)
                                crashReport = null
                            }) { Text("Dismiss") }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Momentum Crash Report")
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    // Kept until dismissed: the share sheet can be closed without sending
                                    context.startActivity(Intent.createChooser(intent, "Send Crash Report"))
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Send") }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(bottomNavClearance()))
        }
    }
}

/** A plain settings-style row: icon, title, optional subtitle, and a trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
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
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun DisconnectHealthConnectDialog(
    onDismiss: () -> Unit,
    onConfirmDisconnect: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect Health Connect", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This disconnects Momentum from Health Connect and revokes its access to your step data.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                        } catch (e: Exception) {
                            // No activity found to handle it
                        }
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("View in Health Connect Settings")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Disconnect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun RestartAppDialog(
    onRestartNow: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Restart Required", fontWeight = FontWeight.Black) },
        text = {
            Text(
                "Health Connect has been disconnected. Restart Momentum now to make sure the change is fully applied.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onRestartNow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ConfirmImportDialog(
    weights: List<WeightEntry>,
    existingByDate: Map<String, Double>,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onConfirm: (List<WeightEntry>) -> Unit
) {
    // Dates you already logged start unticked, so a misread screenshot can't silently replace them
    var selectedEntries by remember { mutableStateOf(weights.filter { it.date !in existingByDate }.toSet()) }
    val dateFormat = remember { java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Data Import", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("We found ${weights.size} entries. Select which ones to import:", style = MaterialTheme.typography.bodyMedium)

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(weights) { entry ->
                        val isSelected = selectedEntries.contains(entry)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEntries = if (isSelected) selectedEntries - entry else selectedEntries + entry
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                selectedEntries = if (it) selectedEntries + entry else selectedEntries - entry
                            })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    runCatching { java.time.LocalDate.parse(entry.date).format(dateFormat) }.getOrDefault(entry.date),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(Units.displayWeight(entry.weight, unitSystem), style = MaterialTheme.typography.bodySmall)
                                existingByDate[entry.date]?.let { existing ->
                                    Text(
                                        "Replaces ${Units.displayWeight(existing, unitSystem)} you logged",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedEntries.toList()) },
                enabled = selectedEntries.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Import Selected", fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileForm(
    profile: UserProfile,
    onCancel: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var unitSystem by remember { mutableStateOf(profile.unitSystem) }
    val context = LocalContext.current
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

            OutlinedTextField(
                value = stepsInput,
                onValueChange = { stepsInput = it },
                label = { Text("Average Daily Steps") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                isError = stepsError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Biological Sex", fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = isMale, onClick = { isMale = true }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Male") }
                    SegmentedButton(selected = !isMale, onClick = { isMale = false }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Female") }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                PrimaryButton(
                    onClick = {
                        onSave(profile.copy(height = parsedHeightCm!!, age = parsedAge!!, isMale = isMale, averageDailySteps = parsedSteps!!, unitSystem = unitSystem, bodyFatPercentage = parsedBodyFat, useHealthConnect = useHealthConnect))
                    },
                    enabled = allValid,
                    modifier = Modifier.weight(1.5f)
                ) { Text("Save Changes") }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}


@Composable
private fun TimePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                content()
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

/** A reminder time in the phone's own 12/24-hour style. */
@Composable
private fun ReminderTimeText(time: java.time.LocalTime) {
    Text(
        time.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary
    )
}
