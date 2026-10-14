package com.eliteonetube.momentum.ui.screens.onboarding

import com.eliteonetube.momentum.ui.components.filledFieldColors
import com.eliteonetube.momentum.ui.components.FieldShape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.ui.components.SecondaryButton
import com.eliteonetube.momentum.ui.components.PrimaryButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.logic.services.HealthConnectManager
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.ui.components.screenSafePadding
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch

private data class GoalInfo(val goal: Goal, val label: String, val description: String)

private val goalInfoList = listOf(
    GoalInfo(Goal.CUT, "Cut", "Eat in a calorie deficit to lose fat while holding onto muscle. Calories adjust weekly only when your loss actually stalls."),
    GoalInfo(Goal.BULK, "Bulk", "Eat in a surplus to build muscle over time, with the surplus kept lean so gains stay steady rather than rushed."),
    GoalInfo(Goal.MAINTAIN, "Maintain", "Hold your current weight steady — calories nudge in either direction the moment your trend starts drifting."),
    GoalInfo(Goal.REVERSE, "Reverse", "Coming off a cut? Calories gradually climb back toward maintenance so your metabolism rebuilds without rapid fat regain.")
)

@Composable
fun OnboardingScreen(onComplete: (Double, Double, Int, Boolean, Int, Goal, Int?, UnitSystem, Double?, Boolean, Boolean) -> Unit) {
    var unitSystem by remember { mutableStateOf(UnitSystem.METRIC) }

    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var weightInput by remember { mutableStateOf("") }
    var heightCmInput by remember { mutableStateOf("") }
    var heightFeetInput by remember { mutableStateOf("") }
    var heightInchesInput by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var stepsInput by remember { mutableStateOf("") }
    var useHealthConnect by remember { mutableStateOf(false) }
    // Off by default: the app is offline-first unless the user chooses online lookup here (or later in Settings)
    var useOnlineLookup by remember { mutableStateOf(false) }
    var selectedGoal by remember { mutableStateOf<Goal?>(null) }
    var knowsCalories by remember { mutableStateOf(false) }
    var customCaloriesInput by remember { mutableStateOf("") }

    // Tutorial State
    var tutorialActive by remember { mutableStateOf(true) }
    val targetPositions = remember { mutableStateMapOf<String, Rect>() }
    val tutorialSteps = remember {
        listOf(
            TutorialStep(
                title = "Current Weight",
                description = "Your mass is the most important factor for baseline metabolic math. The algorithm uses this to determine your initial calorie needs.",
                targetTag = "weight_input"
            ),
            TutorialStep(
                title = "Height",
                description = "Taller individuals generally have more surface area and tissue to maintain, leading to a higher resting energy burn.",
                targetTag = "height_input"
            ),
            TutorialStep(
                title = "Age & Metabolism",
                description = "As we age, our metabolic baselines shift. Providing your age helps Momentum set a more realistic starting point.",
                targetTag = "age_input"
            ),
            TutorialStep(
                title = "Body Fat %",
                description = "If you know your body fat percentage, the algorithm can use the Katch-McArdle formula for even higher precision in your BMR estimate.",
                targetTag = "bf_input"
            ),
            TutorialStep(
                title = "Biological Sex",
                description = "Different hormonal profiles result in slightly different metabolic floors. This helps us dial in the math from day one.",
                targetTag = "sex_input"
            ),
            TutorialStep(
                title = "Activity Level",
                description = "Movement is the variable that changes most. Your average steps tell us how much 'active' fuel your body requires daily.",
                targetTag = "steps_input"
            ),
            TutorialStep(
                title = "Your Strategy",
                description = "Finally, your goal acts as the 'Compass' for Momentum. It chooses whether to nudge your calories for fat loss or muscle gain.",
                targetTag = "goal_selector"
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val allGranted = healthConnectManager.permissions.all { it in granted }
        if (allGranted) {
            useHealthConnect = true
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val doneKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
    val doneKeyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })

    val parsedWeightInput = weightInput.replace(',', '.').toDoubleOrNull()
    val parsedWeightKg = parsedWeightInput?.let {
        if (unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it
    }

    val parsedHeightCm: Double? = if (unitSystem == UnitSystem.IMPERIAL) {
        val ft = heightFeetInput.toIntOrNull()
        val inch = heightInchesInput.toIntOrNull()
        if (ft != null && inch != null) Units.feetInchesToCm(ft, inch) else null
    } else {
        heightCmInput.replace(',', '.').toDoubleOrNull()
    }

    val parsedAge = age.toIntOrNull()
    val parsedBodyFat = bodyFatInput.replace(',', '.').toDoubleOrNull()
    val parsedSteps = stepsInput.toIntOrNull()
    val parsedCustomCalories = customCaloriesInput.toIntOrNull()

    val weightError = weightInput.isNotBlank() && parsedWeightKg == null
    val heightError = if (unitSystem == UnitSystem.IMPERIAL) {
        (heightFeetInput.isNotBlank() || heightInchesInput.isNotBlank()) && parsedHeightCm == null
    } else {
        heightCmInput.isNotBlank() && parsedHeightCm == null
    }
    val ageError = age.isNotBlank() && parsedAge == null
    val bfError = bodyFatInput.isNotBlank() && parsedBodyFat == null
    val stepsError = stepsInput.isNotBlank() && parsedSteps == null
    val customCaloriesError = knowsCalories && customCaloriesInput.isNotBlank() && parsedCustomCalories == null

    val step0Valid = parsedWeightKg != null && parsedHeightCm != null && parsedAge != null
    val step1Valid = parsedSteps != null
    val step2Valid = selectedGoal != null
    val step3Valid = !knowsCalories || parsedCustomCalories != null

    val pagerState = rememberPagerState(pageCount = { 4 })

    // Auto-check permissions when we reach the Activity step
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            if (healthConnectManager.hasAllPermissions()) {
                useHealthConnect = true
            }
        }
    }

    // Trigger step fetch when useHealthConnect becomes true
    LaunchedEffect(useHealthConnect) {
        if (useHealthConnect) {
            val avgSteps = healthConnectManager.fetchAverageStepsLast7Days()
            if (avgSteps != null) {
                stepsInput = avgSteps.toString()
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                        MaterialTheme.colorScheme.background
                    ),
                    endY = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenSafePadding()
        ) {
            StepProgress(current = pagerState.currentPage, total = 4)
            Spacer(modifier = Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                when (page) {
                    0 -> StepPage("STEP 1 OF 4", "About you", "The basics Momentum needs to estimate your starting calories.") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            listOf(UnitSystem.METRIC to "Metric (kg, cm)", UnitSystem.IMPERIAL to "Imperial (lb, ft)").forEachIndexed { i, (system, label) ->
                                SegmentedButton(
                                    selected = unitSystem == system,
                                    onClick = { unitSystem = system },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(label) }
                            }
                        }

                        OnboardingField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = "Current weight",
                            suffix = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg",
                            error = if (weightError) "Enter a valid number" else null,
                            keyboardOptions = doneKeyboardOptions,
                            keyboardActions = doneKeyboardActions,
                            modifier = Modifier.fillMaxWidth()
                                .tutorialTarget("weight_input") { tag, rect -> targetPositions[tag] = rect }
                        )

                        Box(modifier = Modifier.fillMaxWidth().tutorialTarget("height_input") { tag, rect -> targetPositions[tag] = rect }) {
                            if (unitSystem == UnitSystem.IMPERIAL) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OnboardingField(
                                        value = heightFeetInput,
                                        onValueChange = { heightFeetInput = it },
                                        label = "Height",
                                        suffix = "ft",
                                        error = if (heightError) "Enter feet and inches" else null,
                                        keyboardOptions = doneKeyboardOptions,
                                        keyboardActions = doneKeyboardActions,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OnboardingField(
                                        value = heightInchesInput,
                                        onValueChange = { heightInchesInput = it },
                                        label = "",
                                        suffix = "in",
                                        error = if (heightError) " " else null,
                                        keyboardOptions = doneKeyboardOptions,
                                        keyboardActions = doneKeyboardActions,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                OnboardingField(
                                    value = heightCmInput,
                                    onValueChange = { heightCmInput = it },
                                    label = "Height",
                                    suffix = "cm",
                                    error = if (heightError) "Enter a valid number, e.g. 175" else null,
                                    keyboardOptions = doneKeyboardOptions,
                                    keyboardActions = doneKeyboardActions,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OnboardingField(
                                value = age,
                                onValueChange = { age = it },
                                label = "Age",
                                suffix = "years",
                                error = if (ageError) "Whole number" else null,
                                keyboardOptions = doneKeyboardOptions,
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.weight(1f).tutorialTarget("age_input") { tag, rect -> targetPositions[tag] = rect }
                            )
                            OnboardingField(
                                value = bodyFatInput,
                                onValueChange = { bodyFatInput = it },
                                label = "Body fat (optional)",
                                suffix = "%",
                                error = if (bfError) "Valid number" else null,
                                keyboardOptions = doneKeyboardOptions,
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.weight(1f).tutorialTarget("bf_input") { tag, rect -> targetPositions[tag] = rect }
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth().tutorialTarget("sex_input") { tag, rect -> targetPositions[tag] = rect }) {
                            Text(
                                "Biological sex",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(true to "Male", false to "Female").forEachIndexed { i, (male, label) ->
                                    SegmentedButton(
                                        selected = isMale == male,
                                        onClick = { isMale = male },
                                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }

                    1 -> StepPage("STEP 2 OF 4", "Activity", "Roughly how many steps do you walk on an average day?") {
                        OnboardingField(
                            value = stepsInput,
                            onValueChange = { stepsInput = it },
                            label = "Average daily steps",
                            suffix = "steps",
                            error = if (stepsError) "Enter a whole number" else null,
                            keyboardOptions = doneKeyboardOptions,
                            keyboardActions = doneKeyboardActions,
                            modifier = Modifier.fillMaxWidth().tutorialTarget("steps_input") { tag, rect -> targetPositions[tag] = rect }
                        )

                        if (healthConnectManager.isAvailable()) {
                            OptionRow(
                                icon = Icons.Default.HealthAndSafety,
                                title = "Health Connect",
                                description = "Import your average steps automatically for more accurate calculations."
                            ) {
                                if (useHealthConnect) {
                                    Text("Connected", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    FilledTonalButton(onClick = {
                                        try {
                                            permissionLauncher.launch(healthConnectManager.permissions)
                                        } catch (e: Exception) {
                                            Log.e("HealthConnect", "Failed to launch permission request", e)
                                        }
                                    }) { Text("Connect") }
                                }
                            }
                        }

                        OptionRow(
                            icon = Icons.Default.Public,
                            title = "Online food lookup",
                            description = "Off: Momentum works fully offline with your own foods. " +
                                "On: also find foods by name or barcode with Open Food Facts, a free, open food database — " +
                                "only your search words and scanned barcodes are sent, nothing about you. " +
                                "You can change this anytime in Settings."
                        ) {
                            Switch(checked = useOnlineLookup, onCheckedChange = { useOnlineLookup = it })
                        }
                    }

                    2 -> StepPage(
                        "STEP 3 OF 4", "Your goal", "What are you working toward?",
                        modifier = Modifier.tutorialTarget("goal_selector") { tag, rect -> targetPositions[tag] = rect }
                    ) {
                        Column {
                            goalInfoList.forEachIndexed { index, info ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                val isSelected = selectedGoal == info.goal
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.RadioButton) { selectedGoal = info.goal }
                                        .padding(vertical = 12.dp)
                                ) {
                                    RadioButton(selected = isSelected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
                                    Column {
                                        Text(
                                            info.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    3 -> StepPage("STEP 4 OF 4", "Calories", "Last thing — are you already tracking calories?") {
                        OptionRow(
                            icon = Icons.Default.LocalFireDepartment,
                            title = "I already track calories",
                            description = "Skip the estimate and start from what you already eat."
                        ) {
                            Switch(checked = knowsCalories, onCheckedChange = { knowsCalories = it })
                        }
                        if (knowsCalories) {
                            OnboardingField(
                                value = customCaloriesInput,
                                onValueChange = { customCaloriesInput = it },
                                label = "Your current daily calories",
                                suffix = "kcal",
                                error = if (customCaloriesError) "Enter a whole number" else null,
                                keyboardOptions = doneKeyboardOptions,
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    SecondaryButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }

                val currentStepValid = when (pagerState.currentPage) {
                    0 -> step0Valid
                    1 -> step1Valid
                    2 -> step2Valid
                    else -> step3Valid
                }
                val isLastPage = pagerState.currentPage == 3

                PrimaryButton(
                    onClick = {
                        if (isLastPage) {
                            onComplete(
                                parsedWeightKg!!,
                                parsedHeightCm!!,
                                parsedAge!!,
                                isMale,
                                parsedSteps!!,
                                selectedGoal!!,
                                if (knowsCalories) parsedCustomCalories else null,
                                unitSystem,
                                parsedBodyFat,
                                useHealthConnect,
                                useOnlineLookup
                            )
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = currentStepValid,
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text(if (isLastPage) "Finish" else "Next")
                }
            }
        }

        if (tutorialActive) {
            TutorialOverlay(
                steps = tutorialSteps,
                targetPositions = targetPositions,
                onStepCompleted = { stepIdx ->
                    coroutineScope.launch {
                        val targetPage = when (stepIdx) {
                            0, 1, 2, 3, 4 -> 0
                            5 -> 1
                            6 -> 2
                            else -> 3
                        }
                        if (pagerState.currentPage != targetPage) {
                            pagerState.animateScrollToPage(
                                page = targetPage,
                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                },
                onFinished = {
                    tutorialActive = false
                    // Don't animate back, just snap to page 0 to avoid heavy layout work
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                }
            )
        }
    }
}

/** Four thin segments: done and current steps filled, the rest faint. */
@Composable
private fun StepProgress(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
            )
        }
    }
}

/** A step's heading (small capital label, big title, muted line) followed by its content. */
@Composable
private fun StepPage(
    label: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            SectionLabel(label)
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
        content()
    }
}

/** Filled, borderless input matching the rest of the app; the unit sits inside the field. */
@Composable
private fun OnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    error: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isNotEmpty()) ({ Text(label) }) else null,
        suffix = { Text(suffix) },
        isError = error != null,
        supportingText = error?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
        shape = FieldShape,
        colors = filledFieldColors()
    )
}

/** A choice row: icon, title, explanation, and a control (switch or button) on the right. */
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            control()
        }
    }
}
