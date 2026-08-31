package com.eliteonetube.momentum.ui.workout

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.eliteonetube.momentum.logic.ScannedExerciseReview
import com.eliteonetube.momentum.logic.WorkoutHistoryParser
import com.eliteonetube.momentum.ui.theme.bounceClick
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
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
    onCreateExercise: (String, String, ExerciseType, (Exercise) -> Unit) -> Unit = { _, _, _, _ -> }
) {
    var templateName by remember { mutableStateOf("") }
    var templateNotes by remember { mutableStateOf("") }
    var selectedExercises by remember { mutableStateOf<List<TemplateExerciseInput>>(emptyList()) }
    
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
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentSessions) { session ->
                            var exerciseCount by remember { mutableStateOf<Int?>(null) }
                            LaunchedEffect(session.id) {
                                val sets = getSetsForSession(session.id)
                                exerciseCount = sets.map { it.exerciseId }.distinct().size
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick {
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
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Text("New Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    TextButton(
                        onClick = {
                            onCreateTemplate(templateName.trim(), templateNotes.trim().ifEmpty { null }, selectedExercises)
                        },
                        enabled = templateName.isNotBlank() && selectedExercises.isNotEmpty()
                    ) {
                        Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
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
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = templateNotes,
                        onValueChange = { templateNotes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Tips, focus points, or equipment...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
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

                    Text(
                        text = "Exercises (${selectedExercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
                        contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(selectedExercises, key = { _, item -> item.exercise.id }) { index, item ->
                            RoutineExerciseItem(
                                item = item,
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
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matchedItems) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .bounceClick(onClick),
        color = containerColor,
        shape = RoundedCornerShape(20.dp)
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
    onUpdate: (TemplateExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.exercise.muscleGroup, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactInput(
                    label = "SETS",
                    value = item.targetSets.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> onUpdate(item.copy(targetSets = v)) } },
                    modifier = Modifier.weight(1f)
                )
                CompactInput(
                    label = "REPS",
                    value = item.targetReps.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> onUpdate(item.copy(targetReps = v)) } },
                    modifier = Modifier.weight(1f)
                )
                CompactInput(
                    label = "KG",
                    value = if (item.targetWeightKg % 1.0 == 0.0) item.targetWeightKg.toInt().toString() else item.targetWeightKg.toString(),
                    onValueChange = { it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdate(item.copy(targetWeightKg = v)) } },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
private fun CompactInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
            )
        )
    }
}
