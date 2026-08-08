package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var initialPendingSets by remember { mutableStateOf<List<PendingSet>>(emptyList()) }
    var initialSessionExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

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
                val completedSets = sets.filter { it.isCompleted }
                if (completedSets.isNotEmpty()) {
                    onSessionSaved(LocalDate.now().toString(), completedSets, activeTemplateId, editingSessionId)
                }
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
                            notes = set.notes,
                            isCompleted = true
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
                            val avgReps = if (exerciseSets.isNotEmpty()) {
                                (exerciseSets.sumOf { it.reps }.toDouble() / exerciseSets.size).let { Math.round(it).toInt() }
                            } else 10
                            val avgWeightKg = if (exerciseSets.isNotEmpty()) {
                                exerciseSets.sumOf { it.weightKg } / exerciseSets.size
                            } else 0.0

                            TemplateExerciseInput(
                                exercise = exerciseObj,
                                targetSets = defaultSetsCount.coerceAtLeast(1),
                                targetReps = avgReps.coerceAtLeast(1),
                                targetWeightKg = avgWeightKg
                            )
                        }
                    } else emptyList()

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
            title = { Text("Delete workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove this session from ${formatToPhoneDate(session.date)}? This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { onSessionDeleted(session.id); sessionPendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(top = 48.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Workouts",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Consistency is key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // QUICK START BUTTON
            item {
                Button(
                    onClick = {
                        activeTemplateId = null
                        editingSessionId = null
                        initialSessionExercises = emptyList()
                        initialPendingSets = emptyList()
                        isLoggingSession = true
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Start Empty Workout", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            // SAVED ROUTINES
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmarks, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saved Routines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showCreateTemplateDialog = true }) {
                        Text("New Routine", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (allTemplates.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            "No routines saved yet.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
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
                                initialSessionExercises = templateExercises.mapNotNull { exerciseMap[it.exerciseId] }
                                initialPendingSets = templateExercises.flatMap { te ->
                                    (1..te.targetSets.coerceAtLeast(1)).map { sn ->
                                        PendingSet(exerciseId = te.exerciseId, setNumber = sn, weightKg = te.targetWeightKg, reps = te.targetReps)
                                    }
                                }
                                isLoggingSession = true
                            }
                        },
                        onDelete = { onTemplateDeleted(template.id) }
                    )
                }
            }

            // RECENT SESSIONS
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recent History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (recentSessions.isEmpty()) {
                item {
                    EmptyState("No workouts logged yet.")
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
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                template.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                Button(
                    onClick = onStartRoutine,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
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
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Icon(Icons.Default.Delete, null, tint = Color.White) }
        }
    ) {
        SessionCardItem(session, unitSystem, onClick)
    }
}

@Composable
private fun SessionCardItem(
    session: WorkoutSession,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = formatToPhoneDate(session.date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${session.exerciseCount} exercises • ${session.setCount} sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                val isImperial = unitSystem == UnitSystem.IMPERIAL
                val vol = if (isImperial) (session.totalVolumeKg * 2.20462).toInt() else session.totalVolumeKg.toInt()
                val unit = if (isImperial) "lb" else "kg"
                Text(
                    text = "$vol $unit",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
