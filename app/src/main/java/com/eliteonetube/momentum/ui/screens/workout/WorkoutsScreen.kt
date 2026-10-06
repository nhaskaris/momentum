package com.eliteonetube.momentum.ui.screens.workout

import com.eliteonetube.momentum.ui.components.bottomNavClearance
import com.eliteonetube.momentum.ui.components.PrimaryButton
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Keeps the "Starting…" message on screen long enough to read instead of flashing
private const val MIN_START_OVERLAY_MS = 500L

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
    activeSets: List<ActiveWorkoutSet> = emptyList(),
    activeTemplateId: Long? = null,
    hasActiveWorkout: Boolean = false,
    activeWorkoutStartTime: Long? = null,
    unitSystem: UnitSystem,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    getExercisesForTemplate: suspend (Long) -> List<TemplateExercise> = { emptyList() },
    getSetsForTemplate: suspend (Long) -> List<TemplateSet> = { emptyList() },
    onSessionSaved: (date: String, sets: List<PendingSet>, templateId: Long?, sessionId: Long?, updateRoutine: Boolean) -> Unit,
    onSessionDeleted: (Long) -> Unit,
    onTemplateCreated: (String, String?, exercises: List<TemplateExerciseInput>) -> Unit = { _, _, _ -> },
    onTemplateUpdated: (Long, String, String?, List<TemplateExerciseInput>) -> Unit = { _, _, _, _ -> },
    allSessionsFlow: Flow<List<WorkoutSession>> = emptyFlow(),
    templateSummaries: List<TemplateSummary> = emptyList(),
    onTemplateDeleted: (Long) -> Unit = {},
    onSessionStateChanged: (Boolean) -> Unit = {},
    onUpdateActiveWorkout: (Long?, List<PendingSet>) -> Unit = { _, _ -> },
    onClearActiveWorkout: () -> Unit = {},
    onCreateExercise: (String, String, ExerciseType, (Exercise) -> Unit) -> Unit = { _, _, _, _ -> },
    getExerciseHistory: suspend (Long) -> List<LoggedSet> = { emptyList() }
) {
    var isLoggingSession by remember(activeSets, hasActiveWorkout) {
        mutableStateOf(activeSets.isNotEmpty() || hasActiveWorkout)
    }
    var currentActiveTemplateId by remember(activeTemplateId) { mutableStateOf(activeTemplateId) }
    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var editingSessionDate by remember { mutableStateOf<String?>(null) }
    var selectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var sessionPendingDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var startingRoutine by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var showFullHistory by remember { mutableStateOf(false) }
    // Routine being edited, with its current exercises loaded into the dialog's input format
    var routineToEdit by remember { mutableStateOf<Pair<WorkoutTemplate, List<TemplateExerciseInput>>?>(null) }
    val summariesById = remember(templateSummaries) { templateSummaries.associateBy { it.templateId } }

    var initialPendingSets by remember(activeSets) {
        mutableStateOf(
            activeSets.map {
                PendingSet(
                    exerciseId = it.exerciseId,
                    setNumber = it.setNumber,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    notes = it.notes,
                    isCompleted = it.isCompleted,
                    durationSeconds = it.durationSeconds,
                    distanceKm = it.distanceKm,
                    targetWeightKg = it.targetWeightKg,
                    targetReps = it.targetReps,
                    targetDurationSeconds = it.targetDurationSeconds,
                    targetDistanceKm = it.targetDistanceKm,
                    orderIndex = it.orderIndex
                )
            }
        )
    }
    
    var initialSessionExercises by remember(activeSets, allExercises) {
        mutableStateOf(
            activeSets.map { it.exerciseId }.distinct().mapNotNull { id -> allExercises.find { it.id == id } }
        )
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isLoggingSession) {
        onSessionStateChanged(isLoggingSession)
    }

    // Saved routine layout (exercise id -> set count), used to detect changes made during the session
    val routineStructure by produceState<List<Pair<Long, Int>>?>(null, currentActiveTemplateId) {
        value = currentActiveTemplateId?.let { tid ->
            val setCounts = getSetsForTemplate(tid).groupingBy { it.templateExerciseId }.eachCount()
            getExercisesForTemplate(tid).map { te ->
                te.exerciseId to (setCounts[te.id] ?: te.targetSets.coerceAtLeast(1))
            }
        }
    }

    if (isLoggingSession) {
        val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = enterState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 12 }
        ) {
            ActiveSessionScreen(
                allExercises = allExercises,
                unitSystem = unitSystem,
                startTimeMillis = activeWorkoutStartTime,
                // Editing a past session never rewrites the routine
                isRoutine = currentActiveTemplateId != null && editingSessionId == null,
                routineStructure = routineStructure,
                initialExercises = initialSessionExercises,
                initialSets = initialPendingSets,
                getExerciseHistory = getExerciseHistory,
                onCreateExercise = onCreateExercise,
                onCancel = {
                    onClearActiveWorkout()
                    isLoggingSession = false
                    currentActiveTemplateId = null
                    editingSessionId = null
                    editingSessionDate = null
                    initialSessionExercises = emptyList()
                    initialPendingSets = emptyList()
                },
                onFinish = { sets, shouldUpdateRoutine ->
                    onSessionSaved(
                        editingSessionDate ?: LocalDate.now().toString(),
                        sets,
                        currentActiveTemplateId,
                        editingSessionId,
                        shouldUpdateRoutine
                    )
                    onClearActiveWorkout()
                    isLoggingSession = false
                    currentActiveTemplateId = null
                    editingSessionId = null
                    editingSessionDate = null
                    initialSessionExercises = emptyList()
                    initialPendingSets = emptyList()
                },
                onUpdateActiveSets = { sets ->
                    onUpdateActiveWorkout(currentActiveTemplateId, sets)
                }
            )
        }
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
                    editingSessionDate = session.date
                    currentActiveTemplateId = session.templateId
                    initialSessionExercises =
                        sets.mapNotNull { exerciseMap[it.exerciseId] }.distinctBy { it.id }
                    initialPendingSets = sets.mapIndexed { index, set ->
                        PendingSet(
                            exerciseId = set.exerciseId,
                            setNumber = set.setNumber,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            notes = set.notes,
                            isCompleted = true,
                            durationSeconds = set.durationSeconds,
                            distanceKm = set.distanceKm,
                            orderIndex = index
                        )
                    }
                    onUpdateActiveWorkout(currentActiveTemplateId, initialPendingSets)
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
                                (exerciseSets.sumOf { it.reps }
                                    .toDouble() / exerciseSets.size).let { Math.round(it).toInt() }
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

    if (showFullHistory) {
        WorkoutHistoryScreen(
            sessionsFlow = allSessionsFlow,
            unitSystem = unitSystem,
            onBack = { showFullHistory = false },
            onSessionClick = { selectedSession = it },
            onSessionDelete = { sessionPendingDelete = it }
        )
    } else Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Hero — scrolls away like the other tabs, same gradient
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.15f else 0.12f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(top = 48.dp, bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Train",
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "PICK A ROUTINE OR START FRESH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    PrimaryButton(
                        onClick = {
                            editingSessionId = null
                            currentActiveTemplateId = null
                            initialSessionExercises = emptyList()
                            initialPendingSets = emptyList()
                            onUpdateActiveWorkout(null, emptyList())
                            isLoggingSession = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Start workout")
                    }

                    SectionDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("ROUTINES", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { showCreateTemplateDialog = true },
                            modifier = Modifier.offset(y = (-6).dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (allTemplates.isEmpty()) {
                item {
                    EmptyState("Save a routine to make your next workout one tap away.")
                }
            } else {
                itemsIndexed(allTemplates, key = { _, t -> "template_${t.id}" }) { index, template ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        RoutineRow(
                            template = template,
                            summary = summariesById[template.id],
                            onStartRoutine = {
                                if (startingRoutine == null) {
                                    startingRoutine = template
                                    coroutineScope.launch {
                                        try {
                                            val shownAt = System.currentTimeMillis()
                                            currentActiveTemplateId = template.id
                                            // Two queries total, however many exercises the routine has
                                            val templateExercises = getExercisesForTemplate(template.id)
                                            val setsByTemplateExercise = getSetsForTemplate(template.id).groupBy { it.templateExerciseId }
                                            val exerciseMap = allExercises.associateBy { it.id }
                                            initialSessionExercises = templateExercises.mapNotNull { exerciseMap[it.exerciseId] }
                                            initialPendingSets = templateExercises.flatMap { te ->
                                                val templateSets = setsByTemplateExercise[te.id].orEmpty()
                                                if (templateSets.isNotEmpty()) {
                                                    templateSets.map { ts ->
                                                        PendingSet(
                                                            exerciseId = te.exerciseId,
                                                            setNumber = ts.setNumber,
                                                            weightKg = 0.0,
                                                            reps = 0,
                                                            durationSeconds = null,
                                                            distanceKm = null,
                                                            targetWeightKg = ts.targetWeightKg,
                                                            targetReps = ts.targetReps,
                                                            targetDurationSeconds = ts.targetDurationSeconds,
                                                            targetDistanceKm = ts.targetDistanceKm
                                                        )
                                                    }
                                                } else {
                                                    (1..te.targetSets.coerceAtLeast(1)).map { sn ->
                                                        PendingSet(
                                                            exerciseId = te.exerciseId,
                                                            setNumber = sn,
                                                            weightKg = 0.0,
                                                            reps = 0,
                                                            durationSeconds = null,
                                                            distanceKm = null,
                                                            targetWeightKg = te.targetWeightKg,
                                                            targetReps = te.targetReps,
                                                            targetDurationSeconds = te.targetDurationSeconds,
                                                            targetDistanceKm = te.targetDistanceKm
                                                        )
                                                    }
                                                }
                                            }
                                            delay(MIN_START_OVERLAY_MS - (System.currentTimeMillis() - shownAt))
                                            // Persist only now: the saved active workout opens the session by itself
                                            onUpdateActiveWorkout(currentActiveTemplateId, initialPendingSets)
                                            isLoggingSession = true
                                        } finally {
                                            startingRoutine = null
                                        }
                                    }
                                }
                            },
                            onEdit = {
                                coroutineScope.launch {
                                    val exerciseMap = allExercises.associateBy { it.id }
                                    val setsByTemplateExercise = getSetsForTemplate(template.id).groupBy { it.templateExerciseId }
                                    val inputs = getExercisesForTemplate(template.id).mapNotNull { te ->
                                        val exercise = exerciseMap[te.exerciseId] ?: return@mapNotNull null
                                        val sets = setsByTemplateExercise[te.id].orEmpty()
                                        TemplateExerciseInput(
                                            exercise = exercise,
                                            targetSets = if (sets.isNotEmpty()) sets.size else te.targetSets,
                                            targetReps = sets.firstOrNull()?.targetReps ?: te.targetReps,
                                            targetWeightKg = sets.firstOrNull()?.targetWeightKg ?: te.targetWeightKg
                                        )
                                    }
                                    routineToEdit = template to inputs
                                }
                            },
                            onDelete = { onTemplateDeleted(template.id) }
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SectionDivider()
                    SectionLabel("RECENT WORKOUTS")
                }
            }

            if (recentSessions.isEmpty()) {
                item { EmptyState("No workouts logged yet.") }
            } else {
                val shown = recentSessions.take(RECENT_SESSIONS_SHOWN)
                itemsIndexed(shown, key = { _, session -> "session_${session.id}" }) { index, session ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SwipeableSessionRow(
                            session = session,
                            unitSystem = unitSystem,
                            onClick = { selectedSession = session },
                            onSwipeToDelete = { sessionPendingDelete = session }
                        )
                    }
                }
                item {
                    TextButton(
                        onClick = { showFullHistory = true },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("See all workouts", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(bottomNavClearance())) }
        }

        startingRoutine?.let { StartingWorkoutOverlay(routineName = it.name) }
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

    routineToEdit?.let { (template, inputs) ->
        CreateTemplateDialog(
            allExercises = allExercises,
            recentSessions = recentSessions,
            getSetsForSession = getSetsForSession,
            onDismiss = { routineToEdit = null },
            onCreateTemplate = { name, notes, exercises ->
                onTemplateUpdated(template.id, name, notes, exercises)
                routineToEdit = null
            },
            onCreateExercise = onCreateExercise,
            title = "Edit Routine",
            initialName = template.name,
            initialNotes = template.notes,
            initialExercises = inputs
        )
    }
}

// The Train tab shows a short list; everything else is under "See all workouts"
private const val RECENT_SESSIONS_SHOWN = 5

/** "today", "yesterday", "3 days ago", or a date once it's over a month back. */
private fun relativeDay(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 31L -> "$days days ago"
        else -> formatToPhoneDate(isoDate)
    }
}

@Composable
private fun RoutineRow(
    template: WorkoutTemplate,
    summary: TemplateSummary?,
    onStartRoutine: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (summary != null) {
                Text(
                    buildString {
                        append(if (summary.exerciseCount == 1) "1 exercise" else "${summary.exerciseCount} exercises")
                        append(" · ")
                        append(summary.lastDone?.let { "Last done ${relativeDay(it)}" } ?: "Not done yet")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            template.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, "Routine options", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit routine") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Delete routine", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; showDeleteConfirmation = true }
                )
            }
        }
        FilledTonalButton(
            onClick = onStartRoutine,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start", fontWeight = FontWeight.Bold)
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete routine?") },
            text = { Text("${template.name} will be removed. Your completed workout history will stay intact.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirmation = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Keep routine") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSessionRow(
    session: WorkoutSession,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    onSwipeToDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = {
            // The confirmation dialog decides; the row always springs back
            onSwipeToDelete()
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onError) }
        }
    ) {
        SessionRow(session, unitSystem, onClick)
    }
}

@Composable
private fun SessionRow(
    session: WorkoutSession,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    val formattedDate = remember(session.date) { formatToPhoneDate(session.date) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formattedDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${session.exerciseCount} exercises · ${session.setCount} sets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Cardio-only sessions have no weight × reps volume, so "0 kg" would be misleading
        Text(
            if (session.totalVolumeKg > 0.0) {
                val isImperial = unitSystem == UnitSystem.IMPERIAL
                val vol = (if (isImperial) session.totalVolumeKg * 2.20462 else session.totalVolumeKg).roundToInt()
                "%,d %s".format(vol, if (isImperial) "lb" else "kg")
            } else "Cardio",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Every logged workout, grouped by month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutHistoryScreen(
    sessionsFlow: Flow<List<WorkoutSession>>,
    unitSystem: UnitSystem,
    onBack: () -> Unit,
    onSessionClick: (WorkoutSession) -> Unit,
    onSessionDelete: (WorkoutSession) -> Unit
) {
    BackHandler(onBack = onBack)
    val sessions by sessionsFlow.collectAsState(initial = null)
    val byMonth = remember(sessions) {
        sessions.orEmpty().groupBy { s ->
            runCatching { LocalDate.parse(s.date).let { "${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${it.year}" } }
                .getOrDefault(s.date)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All workouts", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Clears the floating bottom navigation bar
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomNavClearance())
        ) {
            if (sessions?.isEmpty() == true) {
                item { EmptyState("No workouts logged yet.") }
            }
            byMonth.forEach { (month, monthSessions) ->
                item(key = "month_$month") {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionLabel(month.uppercase())
                }
                itemsIndexed(monthSessions, key = { _, s -> "history_${s.id}" }) { index, session ->
                    Column {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SwipeableSessionRow(
                            session = session,
                            unitSystem = unitSystem,
                            onClick = { onSessionClick(session) },
                            onSwipeToDelete = { onSessionDelete(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun StartingWorkoutOverlay(routineName: String) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val pulse = rememberInfiniteTransition(label = "startPulse")
    val iconScale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconScale"
    )

    AnimatedVisibility(visibleState = appear, enter = fadeIn(tween(150))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                // Swallow taps so nothing underneath can be started twice
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.animateEnterExit(
                    enter = scaleIn(tween(250, easing = FastOutSlowInEasing), initialScale = 0.85f)
                )
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp).scale(iconScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Starting $routineName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Loading your sets and last performance…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
