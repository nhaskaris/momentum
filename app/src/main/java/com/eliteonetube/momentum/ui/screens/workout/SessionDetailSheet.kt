package com.eliteonetube.momentum.ui.screens.workout

import com.eliteonetube.momentum.ui.components.filledFieldColors
import com.eliteonetube.momentum.ui.components.FieldShape
import com.eliteonetube.momentum.ui.components.SecondaryButton
import com.eliteonetube.momentum.ui.components.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.logic.utils.Units

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailSheet(
    session: WorkoutSession,
    allExercises: List<Exercise>,
    unitSystem: UnitSystem,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSaveAsRoutine: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf("") }
    
    var sets by remember { mutableStateOf<List<LoggedSet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(session.id) {
        isLoading = true
        sets = getSetsForSession(session.id)
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Workout",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = readableSessionDate(session.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val setsByExercise = sets.groupBy { it.exerciseId }
                val exerciseMap = allExercises.associateBy { it.id }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(setsByExercise.toList()) { (exerciseId, exerciseSets) ->
                        val exercise = exerciseMap[exerciseId]
                        Column {
                            SectionLabel((exercise?.name ?: "Unknown exercise").uppercase())
                            Spacer(modifier = Modifier.height(4.dp))
                            exerciseSets.forEachIndexed { index, set ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            "Set ${set.setNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(40.dp)
                                        )
                                        Text(
                                            text = "${Units.displayWeight(set.weightKg, unitSystem)} × ${set.reps}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (!set.notes.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp, start = 52.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Comment,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = set.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }

                SecondaryButton(
                    onClick = { showNameDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Routine")
                }

                SecondaryButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save as routine") },
            text = {
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text("Routine name") },
                    placeholder = { Text("Leave blank to name it by date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                    colors = filledFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAsRoutine(routineName.trim())
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val sessionDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMM yyyy")

private fun readableSessionDate(iso: String): String =
    runCatching { LocalDate.parse(iso.take(10)).format(sessionDateFormatter) }.getOrDefault(iso)
