package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.ExerciseType
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
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

    val coroutineScope = rememberCoroutineScope()

    if (showSessionPicker) {
        AlertDialog(
            onDismissRequest = { showSessionPicker = false },
            title = { Text("Import from Past Session") },
            text = {
                if (recentSessions.isEmpty()) {
                    Text("No past sessions found.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSessions) { session ->
                            // Local state for each item to fetch its own summary lazily
                            var exerciseCount by remember { mutableStateOf<Int?>(null) }
                            LaunchedEffect(session.id) {
                                val sets = getSetsForSession(session.id)
                                exerciseCount = sets.map { it.exerciseId }.distinct().size
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        session.date,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (exerciseCount == null) "Loading..." else "$exerciseCount exercises",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSessionPicker = false }) { Text("Cancel") }
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Workout Template",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Template Name") },
                    placeholder = { Text("e.g. Push Day, Leg Hypertrophy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = templateNotes,
                    onValueChange = { templateNotes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Focus on form and slow negatives") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exercises (${selectedExercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        TextButton(onClick = { showSessionPicker = true }) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("From Past")
                        }
                        TextButton(onClick = { showExercisePicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }

                if (selectedExercises.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No exercises added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        itemsIndexed(selectedExercises, key = { _, item -> item.exercise.id }) { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.exercise.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.exercise.muscleGroup,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedExercises = selectedExercises.filterIndexed { i, _ -> i != index }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = templateName.isNotBlank() && selectedExercises.isNotEmpty(),
                onClick = {
                    onCreateTemplate(
                        templateName.trim(),
                        templateNotes.trim().ifEmpty { null },
                        selectedExercises
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
