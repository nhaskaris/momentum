package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.UnitSystem

@Composable
fun ExerciseSetLogger(
    exercise: Exercise,
    sets: List<PendingSet>,
    historySets: List<LoggedSet>,
    unitSystem: UnitSystem,
    onSetAdded: (PendingSet) -> Unit,
    onSetUpdated: (Int, PendingSet) -> Unit,
    onSetRemoved: (Int) -> Unit,
    onExerciseRemoved: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val unitLabel = if (unitSystem == UnitSystem.IMPERIAL) "lbs" else "kg"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = exercise.targetMuscleGroup,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove Exercise", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onExerciseRemoved()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (historySets.isNotEmpty()) {
                val lastSessionSets = remember(historySets) { historySets.take(3) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Last time: " + lastSessionSets.joinToString(", ") { set ->
                                val weight = if (unitSystem == UnitSystem.IMPERIAL) {
                                    "${(set.weightKg * 2.20462).toInt()} lbs"
                                } else {
                                    "${set.weightKg.toInt()} kg"
                                }
                                "${weight} × ${set.reps}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.width(36.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "SET",
                    modifier = Modifier.width(36.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "WEIGHT ($unitLabel)",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "REPS",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(80.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sets, key = { _, set -> set.setNumber }) { index, set ->
                    SetInputRow(
                        set = set,
                        unitSystem = unitSystem,
                        onUpdate = { updatedSet -> onSetUpdated(set.setNumber, updatedSet) },
                        onRemove = { onSetRemoved(set.setNumber) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val lastSet = sets.lastOrNull()
                    val nextWeight = lastSet?.weightKg ?: 0.0
                    val nextReps = lastSet?.reps ?: 10
                    onSetAdded(
                        PendingSet(
                            exerciseId = exercise.id,
                            setNumber = sets.size + 1,
                            weightKg = nextWeight,
                            reps = nextReps
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Set")
            }
        }
    }
}

@Composable
private fun SetInputRow(
    set: PendingSet,
    unitSystem: UnitSystem,
    onUpdate: (PendingSet) -> Unit,
    onRemove: () -> Unit
) {
    val displayWeight = remember(set.weightKg, unitSystem) {
        if (unitSystem == UnitSystem.IMPERIAL) {
            (set.weightKg * 2.20462).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it) }
        } else {
            if (set.weightKg % 1.0 == 0.0) set.weightKg.toInt().toString() else set.weightKg.toString()
        }
    }

    var weightInput by remember(set.setNumber) { mutableStateOf(displayWeight) }
    var repsInput by remember(set.setNumber) { mutableStateOf(set.reps.toString()) }
    var showNotes by remember { mutableStateOf(set.notes?.isNotBlank() == true) }
    var notesInput by remember(set.setNumber) { mutableStateOf(set.notes ?: "") }

    val completedColor = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                completedColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onUpdate(set.copy(isCompleted = !set.isCompleted)) },
                modifier = Modifier.size(36.dp)
            ) {
                if (set.isCompleted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Not Completed", tint = MaterialTheme.colorScheme.outline)
                }
            }

            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${set.setNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = weightInput,
                onValueChange = { newValue ->
                    weightInput = newValue
                    val parsed = newValue.toDoubleOrNull() ?: 0.0
                    val weightInKg = if (unitSystem == UnitSystem.IMPERIAL) parsed / 2.20462 else parsed
                    onUpdate(set.copy(weightKg = weightInKg))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = repsInput,
                onValueChange = { newValue ->
                    repsInput = newValue
                    val parsedReps = newValue.toIntOrNull() ?: 0
                    onUpdate(set.copy(reps = parsedReps))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(8.dp)
            )

            IconButton(
                onClick = { showNotes = !showNotes },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Add Notes",
                    modifier = Modifier.size(18.dp),
                    tint = if (showNotes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove Set",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (showNotes) {
            OutlinedTextField(
                value = notesInput,
                onValueChange = {
                    notesInput = it
                    onUpdate(set.copy(notes = it.ifBlank { null }))
                },
                placeholder = { Text("Set notes...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 36.dp, end = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}
