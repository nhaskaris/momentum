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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(text = exercise.targetMuscleGroup.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove Exercise", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onExerciseRemoved() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (historySets.isNotEmpty()) {
                val lastSets = remember(historySets) { historySets.take(3) }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PREVIOUS: " + lastSets.joinToString(", ") { set ->
                                val w = if (unitSystem == UnitSystem.IMPERIAL) "${(set.weightKg * 2.20462).toInt()} lb" else "${set.weightKg.toInt()} kg"
                                "$w × ${set.reps}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Headers
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SET", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("WEIGHT ($unitLabel)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("REPS", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(88.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(sets, key = { _, s -> s.setNumber }) { _, set ->
                    SetInputRow(set, unitSystem, { upd -> onSetUpdated(set.setNumber, upd) }, { onSetRemoved(set.setNumber) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val last = sets.lastOrNull()
                    onSetAdded(PendingSet(exerciseId = exercise.id, setNumber = sets.size + 1, weightKg = last?.weightKg ?: 0.0, reps = last?.reps ?: 10))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Set", fontWeight = FontWeight.Bold)
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

    val bgColor = if (set.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(16.dp)).padding(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onUpdate(set.copy(isCompleted = !set.isCompleted)) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                Text(text = "${set.setNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if(set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }

            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it; it.toDoubleOrNull()?.let { v -> onUpdate(set.copy(weightKg = if (unitSystem == UnitSystem.IMPERIAL) v / 2.20462 else v)) } },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            )

            OutlinedTextField(
                value = repsInput,
                onValueChange = { repsInput = it; it.toIntOrNull()?.let { r -> onUpdate(set.copy(reps = r)) } },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            )

            IconButton(onClick = { showNotes = !showNotes }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.Comment, null, modifier = Modifier.size(18.dp), tint = if (showNotes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }

        if (showNotes) {
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it; onUpdate(set.copy(notes = it.ifBlank { null })) },
                placeholder = { Text("Add set notes...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 44.dp, end = 8.dp, bottom = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}
