package com.eliteonetube.momentum.ui.screens.workout

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.ui.components.filledFieldColors
import com.eliteonetube.momentum.ui.components.FieldShape
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.logic.parsers.ScannedExerciseReview
import com.eliteonetube.momentum.logic.parsers.WorkoutHistoryParser
import com.eliteonetube.momentum.ui.components.bounceClick
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTemplateDialog(
    allExercises: List<Exercise>,
    recentSessions: List<WorkoutSession>,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    onDismiss: () -> Unit,
    onCreateTemplate: (name: String, notes: String?, inputs: List<TemplateExerciseInput>) -> Unit,
    onCreateExercise: (String, String, ExerciseType, (Exercise) -> Unit) -> Unit = { _, _, _, _ -> },
    // Edit mode: pre-fill with an existing routine
    title: String = "New Routine",
    initialName: String = "",
    initialNotes: String? = null,
    initialExercises: List<TemplateExerciseInput> = emptyList(),
    unitSystem: UnitSystem = UnitSystem.METRIC
) {
    var templateName by remember { mutableStateOf(initialName) }
    var templateNotes by remember { mutableStateOf(initialNotes.orEmpty()) }
    var selectedExercises by remember { mutableStateOf(initialExercises) }
    
    var showExercisePicker by remember { mutableStateOf(false) }
    var showSessionPicker by remember { mutableStateOf(false) }
    var isProcessingScreenshot by remember { mutableStateOf(false) }
    var pendingReviewList by remember { mutableStateOf<List<ScannedExerciseReview>>(emptyList()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessingScreenshot = true
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            var processedCount = 0
            val allDetected = mutableListOf<ScannedExerciseReview>()

            uris.forEach { uri ->
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val parsed = WorkoutHistoryParser.parse(visionText, allExercises)
                        allDetected.addAll(parsed)
                    }
                    .addOnCompleteListener {
                        processedCount++
                        if (processedCount == uris.size) {
                            if (allDetected.isNotEmpty()) {
                                pendingReviewList = allDetected
                            }
                            isProcessingScreenshot = false
                        }
                    }
            }
        }
    }

    if (showSessionPicker) {
        AlertDialog(
            onDismissRequest = { showSessionPicker = false },
            title = { Text("Import from History", fontWeight = FontWeight.Black) },
            text = {
                if (recentSessions.isEmpty()) {
                    Text("No past sessions found.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        itemsIndexed(recentSessions) { index, session ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            var exerciseCount by remember { mutableStateOf<Int?>(null) }
                            LaunchedEffect(session.id) {
                                val sets = getSetsForSession(session.id)
                                exerciseCount = sets.map { it.exerciseId }.distinct().size
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            val sets = getSetsForSession(session.id)
                                            val exerciseMap = allExercises.associateBy { it.id }
                                            val uniqueExerciseIds = sets.map { it.exerciseId }.distinct()

                                            val importedExercises = uniqueExerciseIds.mapNotNull { exId ->
                                                val exerciseObj = exerciseMap[exId] ?: return@mapNotNull null
                                                val exerciseSets = sets.filter { it.exerciseId == exId }
                                                
                                                val avgReps = if (exerciseSets.isNotEmpty()) {
                                                    (exerciseSets.sumOf { it.reps }.toDouble() / exerciseSets.size)
                                                        .let { Math.round(it).toInt() }
                                                } else 10
                                                
                                                val avgWeight = if (exerciseSets.isNotEmpty()) {
                                                    exerciseSets.sumOf { it.weightKg } / exerciseSets.size
                                                } else 0.0

                                                TemplateExerciseInput(
                                                    exercise = exerciseObj,
                                                    targetSets = exerciseSets.size.coerceAtLeast(1),
                                                    targetReps = avgReps.coerceAtLeast(1),
                                                    targetWeightKg = avgWeight
                                                )
                                            }

                                            selectedExercises = (selectedExercises + importedExercises)
                                                .distinctBy { it.exercise.id }
                                            
                                            if (templateName.isBlank()) {
                                                templateName = "Routine from ${session.date}"
                                            }
                                            showSessionPicker = false
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            runCatching { java.time.LocalDate.parse(session.date).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy")) }.getOrDefault(session.date),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (exerciseCount == null) "Loading..." else "$exerciseCount exercises",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSessionPicker = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showExercisePicker) {
        ExercisePickerScreen(
            allExercises = allExercises,
            onDismiss = { showExercisePicker = false },
            onExerciseSelected = { exercise ->
                if (selectedExercises.none { it.exercise.id == exercise.id }) {
                    selectedExercises = selectedExercises + TemplateExerciseInput(exercise = exercise)
                }
                showExercisePicker = false
            },
            onCreateExercise = onCreateExercise
        )
        return
    }

    if (pendingReviewList.isNotEmpty()) {
        ScanReviewDialog(
            scannedList = pendingReviewList,
            onDismiss = { pendingReviewList = emptyList() },
            onConfirm = { reviewed ->
                selectedExercises = (selectedExercises + reviewed).distinctBy { it.exercise.id }
                pendingReviewList = emptyList()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    TextButton(
                        onClick = {
                            onCreateTemplate(templateName.trim(), templateNotes.trim().ifEmpty { null }, selectedExercises)
                        },
                        enabled = templateName.isNotBlank() && selectedExercises.isNotEmpty()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Routine Name") },
                        placeholder = { Text("e.g. Upper Body Focus") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        shape = FieldShape,
                        colors = filledFieldColors()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = templateNotes,
                        onValueChange = { templateNotes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Tips, focus points, or equipment...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = FieldShape,
                        colors = filledFieldColors()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CreationActionCard(
                            icon = Icons.Default.Screenshot,
                            label = "Scan",
                            isLoading = isProcessingScreenshot,
                            onClick = { screenshotPicker.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                        CreationActionCard(
                            icon = Icons.Default.History,
                            label = "History",
                            onClick = { showSessionPicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        CreationActionCard(
                            icon = Icons.Default.Add,
                            label = "Add",
                            onClick = { showExercisePicker = true },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SectionLabel("EXERCISES · ${selectedExercises.size}")
                }

                if (selectedExercises.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Add exercises to build your routine.\nYou can also scan a screenshot!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp)
                    ) {
                        itemsIndexed(selectedExercises, key = { _, item -> item.exercise.id }) { index, item ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            RoutineExerciseItem(
                                item = item,
                                unitSystem = unitSystem,
                                onUpdate = { updated ->
                                    val newList = selectedExercises.toMutableList()
                                    newList[index] = updated
                                    selectedExercises = newList
                                },
                                onRemove = {
                                    selectedExercises = selectedExercises.filterIndexed { i, _ -> i != index }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanReviewDialog(
    scannedList: List<ScannedExerciseReview>,
    onDismiss: () -> Unit,
    onConfirm: (List<TemplateExerciseInput>) -> Unit
) {
    val matchedItems = remember(scannedList) { scannedList.filter { it.exercise != null } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanned Exercises", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (matchedItems.isEmpty()) {
                    Text("I couldn't find any exercises in those photos. Try a clearer screenshot or add them manually!", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("I found ${matchedItems.size} exercises. Ready to add them to your routine?", style = MaterialTheme.typography.bodySmall)
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        itemsIndexed(matchedItems) { index, item ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.exercise?.name ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text("${item.targetSets} sets • ${item.targetReps} reps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (matchedItems.isNotEmpty()) {
                Button(
                    onClick = {
                        val final = matchedItems.mapNotNull { item ->
                            item.exercise?.let { ex ->
                                TemplateExerciseInput(
                                    exercise = ex,
                                    targetSets = item.targetSets,
                                    targetReps = item.targetReps,
                                    targetWeightKg = item.targetWeightKg
                                )
                            }
                        }
                        onConfirm(final)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add to Routine", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text(if (matchedItems.isEmpty()) "Close" else "Cancel") 
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun CreationActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .bounceClick(onClick),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            } else {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = contentColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = contentColor)
            }
        }
    }
}

@Composable
private fun RoutineExerciseItem(
    item: TemplateExerciseInput,
    unitSystem: UnitSystem,
    onUpdate: (TemplateExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    val isImperial = unitSystem == UnitSystem.IMPERIAL
    val shownWeight = if (isImperial) Units.kgToLb(item.targetWeightKg) else item.targetWeightKg
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.exercise.muscleGroup, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove ${item.exercise.name}", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactInput(
                label = "SETS",
                initial = item.targetSets.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> onUpdate(item.copy(targetSets = v)) } },
                modifier = Modifier.weight(1f)
            )
            CompactInput(
                label = "REPS",
                initial = item.targetReps.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> onUpdate(item.copy(targetReps = v)) } },
                modifier = Modifier.weight(1f)
            )
            // Typed in the user's unit; routines always store kg
            CompactInput(
                label = if (isImperial) "LB" else "KG",
                initial = if (shownWeight % 1.0 == 0.0) shownWeight.toInt().toString() else "%.1f".format(shownWeight),
                decimal = true,
                onValueChange = {
                    it.replace(',', '.').toDoubleOrNull()?.let { v ->
                        onUpdate(item.copy(targetWeightKg = if (isImperial) Units.lbToKg(v) else v))
                    }
                },
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

/**
 * Small labelled number field. Keeps its own text while typing, so "82." or an empty field don't get
 * overwritten mid-edit by the last parsed value.
 */
@Composable
private fun CompactInput(
    label: String,
    initial: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false
) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            shape = FieldShape,
            colors = filledFieldColors()
        )
    }
}
