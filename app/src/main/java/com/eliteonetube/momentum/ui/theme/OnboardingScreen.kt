package com.eliteonetube.momentum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.logic.Units
import kotlinx.coroutines.launch

private data class GoalInfo(val goal: Goal, val label: String, val description: String)

private val goalInfoList = listOf(
    GoalInfo(Goal.CUT, "Cut", "Eat in a calorie deficit to lose fat while holding onto muscle. Calories adjust weekly only when your loss actually stalls."),
    GoalInfo(Goal.BULK, "Bulk", "Eat in a surplus to build muscle over time, with the surplus kept lean so gains stay steady rather than rushed."),
    GoalInfo(Goal.MAINTAIN, "Maintain", "Hold your current weight steady — calories nudge in either direction the moment your trend starts drifting."),
    GoalInfo(Goal.REVERSE, "Reverse", "Coming off a cut? Calories gradually climb back toward maintenance so your metabolism rebuilds without rapid fat regain.")
)

@Composable
fun OnboardingScreen(onComplete: (Double, Double, Int, Boolean, Int, Goal, Int?, UnitSystem, Double?) -> Unit) {
    var unitSystem by remember { mutableStateOf(UnitSystem.METRIC) }

    var weightInput by remember { mutableStateOf("") }
    var heightCmInput by remember { mutableStateOf("") }
    var heightFeetInput by remember { mutableStateOf("") }
    var heightInchesInput by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var stepsInput by remember { mutableStateOf("") }
    var selectedGoal by remember { mutableStateOf<Goal?>(null) }
    var knowsCalories by remember { mutableStateOf(false) }
    var customCaloriesInput by remember { mutableStateOf("") }

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
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenSafePadding()
    ) {
        LinearProgressIndicator(
            progress = { (pagerState.currentPage + 1) / 4f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            when (page) {
                0 -> Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Welcome to Momentum", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Let's start with the basics.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use imperial units (lb, ft/in)", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = unitSystem == UnitSystem.IMPERIAL,
                            onCheckedChange = {
                                unitSystem = if (it) UnitSystem.IMPERIAL else UnitSystem.METRIC
                            }
                        )
                    }

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text(if (unitSystem == UnitSystem.IMPERIAL) "Current Weight (lb)" else "Current Weight (kg)") },
                        isError = weightError,
                        supportingText = { if (weightError) Text("Enter a valid number") },
                        keyboardOptions = doneKeyboardOptions,
                        keyboardActions = doneKeyboardActions,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

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
                                keyboardOptions = doneKeyboardOptions,
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = heightInchesInput,
                                onValueChange = { heightInchesInput = it },
                                label = { Text("Height (in)") },
                                isError = heightError,
                                keyboardOptions = doneKeyboardOptions,
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (heightError) {
                            Text(
                                "Enter valid feet and inches",
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
                            keyboardOptions = doneKeyboardOptions,
                            keyboardActions = doneKeyboardActions,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            label = { Text("Age") },
                            isError = ageError,
                            supportingText = { if (ageError) Text("Enter a whole number") },
                            keyboardOptions = doneKeyboardOptions,
                            keyboardActions = doneKeyboardActions,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bodyFatInput,
                            onValueChange = { bodyFatInput = it },
                            label = { Text("Body Fat % (Optional)") },
                            isError = bfError,
                            supportingText = { if (bfError) Text("Valid number") },
                            keyboardOptions = doneKeyboardOptions,
                            keyboardActions = doneKeyboardActions,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Biological Sex:")
                        Button(onClick = { isMale = !isMale }) {
                            Text(if (isMale) "Male" else "Female")
                        }
                    }
                }

                1 -> Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Activity", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Roughly how many steps do you walk on an average day?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
                    )

                    OutlinedTextField(
                        value = stepsInput,
                        onValueChange = { stepsInput = it },
                        label = { Text("Average Daily Steps (e.g. 8000)") },
                        isError = stepsError,
                        supportingText = { if (stepsError) Text("Enter a whole number") },
                        keyboardOptions = doneKeyboardOptions,
                        keyboardActions = doneKeyboardActions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Your Goal", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "What are you working toward?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
                    )

                    goalInfoList.forEach { info ->
                        val isSelected = selectedGoal == info.goal
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clickable { selectedGoal = info.goal },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(info.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    RadioButton(selected = isSelected, onClick = { selectedGoal = info.goal })
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(info.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                3 -> Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Calories", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Last thing — are you already tracking calories?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Already tracking calories?", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Skip the estimate and start from what you already eat",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(checked = knowsCalories, onCheckedChange = { knowsCalories = it })
                            }

                            if (knowsCalories) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customCaloriesInput,
                                    onValueChange = { customCaloriesInput = it },
                                    label = { Text("Your current daily calories") },
                                    isError = customCaloriesError,
                                    supportingText = { if (customCaloriesError) Text("Enter a whole number") },
                                    keyboardOptions = doneKeyboardOptions,
                                    keyboardActions = doneKeyboardActions,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
                OutlinedButton(
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

            Button(
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
                            parsedBodyFat
                        )
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                enabled = currentStepValid,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isLastPage) "Finish" else "Next")
            }
        }
    }
}