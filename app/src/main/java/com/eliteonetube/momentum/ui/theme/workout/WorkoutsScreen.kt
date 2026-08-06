package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun formatToPhoneDate(isoDateString: String): String {
    return try {
        val parsedDate = LocalDate.parse(isoDateString)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        parsedDate.format(formatter)
    } catch (e: Exception) {
        isoDateString
    }
}

@Composable
fun WorkoutsScreen(
    recentSessions: List<WorkoutSession>,
    allExercises: List<Exercise>,
    allTemplates: List<WorkoutTemplate> = emptyList(),
    unitSystem: UnitSystem,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    getExercisesForTemplate: suspend (Long) -> List<TemplateExercise> = { emptyList() },
    onSessionSaved: (date: String, sets: List<PendingSet>, templateId: Long?, sessionId: Long?) -> Unit,
    onSessionDeleted: (Long) -> Unit,
    onTemplateCreated: (String, String?, exercises: List<TemplateExerciseInput>) -> Unit = { _, _, _ -> },
    onTemplateDeleted: (Long) -> Unit = {},
    onSessionStateChanged: (Boolean) -> Unit = {},
    onCreateExercise: (String, String, (Exercise) -> Unit) -> Unit = { _, _, _ -> }
) {
    var isLoggingSession by remember { mutableStateOf(false) }
    var activeTemplateId by remember { mutableStateOf<Long?>(null) }
    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var selectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var sessionPendingDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }

    // Pre-filled state passed when user starts a session from a past session or routine
    var initialPendingSets by remember { mutableStateOf<List<PendingSet>>(emptyList()) }
    var initialSessionExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    // Notify parent component whenever session active state toggles
    LaunchedEffect(isLoggingSession) {
        onSessionStateChanged(isLoggingSession)
    }

    if (isLoggingSession) {
        ActiveSessionScreen(
            allExercises = allExercises,
            unitSystem = unitSystem,
            initialExercises = initialSessionExercises,
            initialSets = initialPendingSets,
            onCreateExercise = onCreateExercise,
            onCancel = {
                isLoggingSession = false
                activeTemplateId = null
                editingSessionId = null
                initialSessionExercises = emptyList()
                initialPendingSets = emptyList()
            },
            onFinish = { sets ->
                onSessionSaved(LocalDate.now().toString(), sets, activeTemplateId, editingSessionId)
                isLoggingSession = false
                activeTemplateId = null
                editingSessionId = null
                initialSessionExercises = emptyList()
                initialPendingSets = emptyList()
            }
        )
        return
    }

    selectedSession?.let { session ->
        SessionDetailSheet(
            session = session,
            allExercises = allExercises,
            unitSystem = unitSystem,
            getSetsForSession = getSetsForSession,
            onDismiss = { selectedSession = null },
            onEdit = {
                coroutineScope.launch {
                    val sets = getSetsForSession(session.id)
                    val exerciseMap = allExercises.associateBy { it.id }

                    editingSessionId = session.id
                    activeTemplateId = session.templateId
                    initialSessionExercises = sets.mapNotNull { exerciseMap[it.exerciseId] }.distinctBy { it.id }
                    initialPendingSets = sets.map { set ->
                        PendingSet(
                            exerciseId = set.exerciseId,
                            setNumber = set.setNumber,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            notes = set.notes
                        )
                    }
                    selectedSession = null
                    isLoggingSession = true
                }
            },
            onSaveAsRoutine = { routineName ->
                coroutineScope.launch {
                    val sets = getSetsForSession(session.id)
                    val exerciseMap = allExercises.associateBy { it.id }

                    val uniqueExerciseIds = sets.map { it.exerciseId }.distinct()

                    val templateInputs = if (uniqueExerciseIds.isNotEmpty()) {
                        uniqueExerciseIds.mapNotNull { exId ->
                            val exerciseObj = exerciseMap[exId] ?: return@mapNotNull null
                            val exerciseSets = sets.filter { it.exerciseId == exId }
                            val defaultSetsCount = exerciseSets.size

                            // Carry over the actual weight/reps used, so the routine
                            // pre-fills with what you last lifted instead of generic defaults.
                            val avgReps = if (exerciseSets.isNotEmpty()) {
                                (exerciseSets.sumOf { it.reps }.toDouble() / exerciseSets.size)
                                    .let { Math.round(it).toInt() }
                            } else {
                                10
                            }
                            val avgWeightKg = if (exerciseSets.isNotEmpty()) {
                                exerciseSets.sumOf { it.weightKg } / exerciseSets.size
                            } else {
                                0.0
                            }

                            TemplateExerciseInput(
                                exercise = exerciseObj,
                                targetSets = defaultSetsCount.coerceAtLeast(1),
                                targetReps = avgReps.coerceAtLeast(1),
                                targetWeightKg = avgWeightKg
                            )
                        }
                    } else {
                        emptyList()
                    }

                    // Call callback to save in DB/ViewModel
                    onTemplateCreated(
                        routineName.ifBlank { "Routine from ${formatToPhoneDate(session.date)}" },
                        "Created from past workout on ${formatToPhoneDate(session.date)}",
                        templateInputs
                    )

                    selectedSession = null
                }
            },
            onDelete = {
                onSessionDeleted(session.id)
                selectedSession = null
            }
        )
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Delete workout?") },
            text = { Text("Remove this session from ${formatToPhoneDate(session.date)}? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onSessionDeleted(session.id)
                    sessionPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Workouts", style = MaterialTheme.typography.headlineLarge)
            FilledIconButton(
                onClick = {
                    activeTemplateId = null
                    editingSessionId = null
                    initialSessionExercises = emptyList()
                    initialPendingSets = emptyList()
                    isLoggingSession = true
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Start quick workout")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // WORKOUT TEMPLATES & PAST SESSION ROUTINES
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Saved Routines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showCreateTemplateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Routine")
                    }
                }
            }

            if (allTemplates.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "No routines created yet. Tap 'New Routine' to create a template from scratch or a past session.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(allTemplates, key = { "template_${it.id}" }) { template ->
                    TemplateItemCard(
                        template = template,
                        onStartRoutine = {
                            coroutineScope.launch {
                                activeTemplateId = template.id
                                val templateExercises = getExercisesForTemplate(template.id)
                                val exerciseMap = allExercises.associateBy { it.id }
                                val loadedExercises = templateExercises.mapNotNull { exerciseMap[it.exerciseId] }

                                // Pre-fill sets from the template's saved targets so starting a
                                // routine restores the weight/reps you used last time, instead of
                                // handing you a blank slate for every exercise.
                                initialSessionExercises = loadedExercises
                                initialPendingSets = templateExercises.flatMap { templateExercise ->
                                    (1..templateExercise.targetSets.coerceAtLeast(1)).map { setNumber ->
                                        PendingSet(
                                            exerciseId = templateExercise.exerciseId,
                                            setNumber = setNumber,
                                            weightKg = templateExercise.targetWeightKg,
                                            reps = templateExercise.targetReps
                                        )
                                    }
                                }
                                isLoggingSession = true
                            }
                        },
                        onDelete = { onTemplateDeleted(template.id) }
                    )
                }
            }

            // RECENT COMPLETED SESSIONS
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Recent Sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recentSessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No workouts logged yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap + or start a routine to log your workout",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentSessions, key = { "session_${it.id}" }) { session ->
                    SwipeableSessionCard(
                        session = session,
                        allExercises = allExercises,
                        unitSystem = unitSystem,
                        onClick = { selectedSession = session },
                        onSwipeToDelete = { sessionPendingDelete = session }
                    )
                }
            }
        }
    }

    if (showCreateTemplateDialog) {
        CreateTemplateDialog(
            allExercises = allExercises,
            recentSessions = recentSessions,
            getSetsForSession = getSetsForSession,
            onDismiss = { showCreateTemplateDialog = false },
            onCreateTemplate = { name, notes, exercises ->
                onTemplateCreated(name, notes, exercises)
                showCreateTemplateDialog = false
            },
            onCreateExercise = onCreateExercise
        )
    }
}

@Composable
private fun TemplateItemCard(
    template: WorkoutTemplate,
    onStartRoutine: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                template.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Routine", tint = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onStartRoutine,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSessionCard(
    session: WorkoutSession,
    allExercises: List<Exercise>,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    onSwipeToDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToDelete()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    ) {
        SessionCardItem(
            session = session,
            allExercises = allExercises,
            unitSystem = unitSystem,
            onClick = onClick
        )
    }
}

@Composable
private fun SessionCardItem(
    session: WorkoutSession,
    allExercises: List<Exercise>,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    val formattedDate = remember(session.date) {
        formatToPhoneDate(session.date)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${session.exerciseCount} exercises • ${session.setCount} sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (session.setCount == 0) {
                Text(
                    text = "No sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val isImperial = unitSystem == UnitSystem.IMPERIAL
                val totalVolumeDisplay = if (isImperial) {
                    "${(session.totalVolumeKg * 2.20462).toInt()} lbs total volume"
                } else {
                    "${session.totalVolumeKg.toInt()} kg total volume"
                }

                Text(
                    text = totalVolumeDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}