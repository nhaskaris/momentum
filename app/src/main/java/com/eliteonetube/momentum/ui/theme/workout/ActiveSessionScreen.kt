package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.ExerciseType
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.logic.RestTimerService
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eliteonetube.momentum.ui.theme.MomentumGlass
import com.eliteonetube.momentum.ui.theme.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    allExercises: List<Exercise>,
    unitSystem: UnitSystem,
    initialExercises: List<Exercise> = emptyList(),
    initialSets: List<PendingSet> = emptyList(),
    getExerciseHistory: suspend (Long) -> List<LoggedSet> = { emptyList() },
    onCancel: () -> Unit,
    onFinish: (List<PendingSet>) -> Unit,
    onUpdateActiveSets: (List<PendingSet>) -> Unit = {},
    onCreateExercise: (String, String, ExerciseType, (Exercise) -> Unit) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showExercisePicker by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var exerciseToSwapId by remember { mutableStateOf<Long?>(null) }
    
    val timerActive by RestTimerService.isActive.collectAsState()
    val timeLeft by RestTimerService.timeLeft.collectAsState()
    var restTimerSeconds by remember { mutableLongStateOf(120L) }

    val resolvedInitialExercises = remember(initialExercises, initialSets, allExercises) {
        if (initialExercises.isNotEmpty()) {
            initialExercises
        } else {
            initialSets.map { it.exerciseId }.distinct().mapNotNull { id -> allExercises.find { it.id == id } }
        }
    }

    val initialMap = remember(initialSets) { initialSets.groupBy { it.exerciseId } }
    var sessionExercises by remember { mutableStateOf(resolvedInitialExercises) }
    var setsByExercise by remember { mutableStateOf(initialMap) }

    // Use a flag to avoid initialization loops
    var isInitialized by remember { mutableStateOf(false) }

    // Sync to local state ONLY ONCE when the screen is first loaded with data
    LaunchedEffect(resolvedInitialExercises, initialMap) {
        if (!isInitialized && resolvedInitialExercises.isNotEmpty()) {
            sessionExercises = resolvedInitialExercises
            setsByExercise = initialMap
            isInitialized = true
        }
    }

    // Persist to DB whenever local state changes, but use a debounce or check
    // to avoid triggering loops if the parent passes the same data back
    LaunchedEffect(setsByExercise, sessionExercises) {
        if (isInitialized) {
            val allSets = sessionExercises.flatMap { setsByExercise[it.id].orEmpty() }
            // Only update if there's actual data to save
            if (allSets.isNotEmpty()) {
                onUpdateActiveSets(allSets)
            }
        }
    }

    val exerciseHistoryMap = remember { mutableStateMapOf<Long, List<LoggedSet>>() }
    val coroutineScope = rememberCoroutineScope()

    val currentExerciseIds = remember(sessionExercises) { sessionExercises.map { it.id } }
    LaunchedEffect(currentExerciseIds) {
        currentExerciseIds.forEach { exId ->
            if (!exerciseHistoryMap.containsKey(exId)) {
                exerciseHistoryMap[exId] = getExerciseHistory(exId)
            }
        }
    }

    val pageCount = if (sessionExercises.isEmpty()) 0 else sessionExercises.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // Safety: ensure we are on a valid page
    val currentStepIndex = if (pageCount > 0) {
        pagerState.currentPage.coerceAtMost(pageCount - 1)
    } else 0
    
    val isFinishStep = pageCount > 0 && currentStepIndex == sessionExercises.size && sessionExercises.isNotEmpty()

    val allSets = remember(setsByExercise, sessionExercises) { 
        sessionExercises.flatMap { setsByExercise[it.id].orEmpty() } 
    }
    val totalVolumeKg by remember(allSets) { 
        derivedStateOf { allSets.sumOf { it.weightKg * it.reps } } 
    }
    val completedSets by remember(allSets) { 
        derivedStateOf { allSets.count { it.isCompleted } } 
    }

    val volumeDisplay = if (unitSystem == UnitSystem.IMPERIAL) {
        "${(totalVolumeKg * 2.20462).toInt()} lbs"
    } else {
        "${totalVolumeKg.toInt()} kg"
    }

    if (showExercisePicker) {
        ExercisePickerScreen(
            allExercises = allExercises,
            onDismiss = { 
                showExercisePicker = false
                exerciseToSwapId = null
            },
            onExerciseSelected = { exercise ->
                if (exerciseToSwapId != null) {
                    // Swap logic
                    val oldId = exerciseToSwapId!!
                    sessionExercises = sessionExercises.map { if (it.id == oldId) exercise else it }
                    val oldSets = setsByExercise[oldId].orEmpty()
                    setsByExercise = setsByExercise - oldId + (exercise.id to oldSets.map { it.copy(exerciseId = exercise.id) })
                    exerciseToSwapId = null
                } else if (sessionExercises.none { it.id == exercise.id }) {
                    sessionExercises = sessionExercises + exercise
                    setsByExercise = setsByExercise + (exercise.id to emptyList())
                    coroutineScope.launch { pagerState.animateScrollToPage(sessionExercises.size - 1) }
                }
                showExercisePicker = false
            },
            onCreateExercise = { name, muscleGroup, type, onCreated ->
                onCreateExercise(name, muscleGroup, type) { newlyCreatedExercise ->
                    if (exerciseToSwapId != null) {
                        // Swap with new
                        val oldId = exerciseToSwapId!!
                        sessionExercises = sessionExercises.map { if (it.id == oldId) newlyCreatedExercise else it }
                        val oldSets = setsByExercise[oldId].orEmpty()
                        setsByExercise = setsByExercise - oldId + (newlyCreatedExercise.id to oldSets.map { it.copy(exerciseId = newlyCreatedExercise.id) })
                        exerciseToSwapId = null
                    } else if (sessionExercises.none { it.id == newlyCreatedExercise.id }) {
                        sessionExercises = sessionExercises + newlyCreatedExercise
                        setsByExercise = setsByExercise + (newlyCreatedExercise.id to emptyList())
                        coroutineScope.launch { pagerState.animateScrollToPage(sessionExercises.size - 1) }
                    }
                    onCreated(newlyCreatedExercise)
                    showExercisePicker = false
                }
            }
        )
        return
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Discard Workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to end this session and lose all progress?") },
            confirmButton = {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workout", fontWeight = FontWeight.Black)
                        if (sessionExercises.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$completedSets sets",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(" • ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    volumeDisplay,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (allSets.isNotEmpty()) showCancelConfirm = true else onCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "End workout")
                    }
                },
                actions = {
                    TextButton(onClick = { showExercisePicker = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Exercise", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sessionExercises.isNotEmpty()) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(currentStepIndex - 1) } },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Back") }
                        }

                        if (isFinishStep) {
                            Button(
                                onClick = { onFinish(allSets) },
                                enabled = allSets.isNotEmpty(),
                                modifier = Modifier.weight(2f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Complete Workout", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(currentStepIndex + 1) } },
                                modifier = Modifier.weight(2f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(if (currentStepIndex == sessionExercises.size - 1) "Review workout" else "Next exercise", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (sessionExercises.isNotEmpty()) {
                WorkoutStepBar(
                    exercises = sessionExercises,
                    setsByExercise = setsByExercise,
                    currentStepIndex = currentStepIndex,
                    onStepSelected = { idx -> coroutineScope.launch { pagerState.animateScrollToPage(idx) } }
                )
            }

            if (sessionExercises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyWorkoutStateCard(onAddExerciseClick = { showExercisePicker = true })
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        pageSpacing = 12.dp
                    ) { page ->
                        if (page >= sessionExercises.size) {
                            WorkoutSummaryStep(
                                totalSets = allSets.size,
                                totalReps = allSets.sumOf { it.reps },
                                totalVolumeDisplay = volumeDisplay,
                                exercises = sessionExercises,
                                setsByExercise = setsByExercise,
                                onEditExerciseStep = { idx -> coroutineScope.launch { pagerState.animateScrollToPage(idx) } }
                            )
                        } else {
                            val currentExercise = sessionExercises[page]
                            ExerciseSetLogger(
                                exercise = currentExercise,
                                sets = setsByExercise[currentExercise.id].orEmpty(),
                                historySets = exerciseHistoryMap[currentExercise.id] ?: emptyList(),
                                unitSystem = unitSystem,
                                onSetAdded = { s ->
                                    val list = setsByExercise[currentExercise.id].orEmpty()
                                    setsByExercise = setsByExercise + (currentExercise.id to (list + s))
                                },
                                onSetUpdated = { sn, upd ->
                                    val oldList = setsByExercise[currentExercise.id].orEmpty()
                                    val oldSet = oldList.find { it.setNumber == sn }
                                    val list = oldList.map { if (it.setNumber == sn) upd else it }
                                    setsByExercise = setsByExercise + (currentExercise.id to list)
                                    if (oldSet?.isCompleted == false && upd.isCompleted) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        RestTimerService.startTimer(context, restTimerSeconds)
                                    }
                                },
                                onSetRemoved = { sn ->
                                    val list = setsByExercise[currentExercise.id].orEmpty().filterNot { it.setNumber == sn }.mapIndexed { i, ps -> ps.copy(setNumber = i + 1) }
                                    setsByExercise = setsByExercise + (currentExercise.id to list)
                                },
                                onExerciseRemoved = {
                                    sessionExercises = sessionExercises.filterNot { it.id == currentExercise.id }
                                    setsByExercise = setsByExercise - currentExercise.id
                                },
                                onExerciseSwapped = {
                                    exerciseToSwapId = currentExercise.id
                                    showExercisePicker = true
                                }
                            )
                        }
                    }

                    if (timerActive) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            RestTimerOverlay(
                                timeLeft = timeLeft,
                                onAdjust = { d -> RestTimerService.adjustTimer(context, d) },
                                onStop = { RestTimerService.stopTimer(context) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutProgressCard(
    currentStepIndex: Int,
    exerciseCount: Int,
    completedSets: Int,
    volumeDisplay: String,
    isFinishStep: Boolean
) {
    val progress = if (exerciseCount == 0) 0f else (currentStepIndex.coerceAtMost(exerciseCount).toFloat() / exerciseCount)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isFinishStep) "READY TO REVIEW" else "TRAINING SESSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        if (isFinishStep) "Review Workout" else "Exercise ${currentStepIndex + 1} of $exerciseCount",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "$completedSets sets",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Cumulative Volume: $volumeDisplay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RestTimerOverlay(
    timeLeft: Long,
    onAdjust: (Long) -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth()
                .animateContentSize(),
            color = MomentumGlass.copy(alpha = 0.92f),
            shape = RoundedCornerShape(36.dp),
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop/Close Button
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .bounceClick(onStop)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Stop rest timer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Time Display
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "RESTING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (timeLeft > 0) "%d:%02d".format(timeLeft / 60, timeLeft % 60) else "GO!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (timeLeft > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    )
                }

                // Adjustment Buttons
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onAdjust(-15) },
                        modifier = Modifier.size(44.dp).bounceClick { onAdjust(-15) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("-15", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalIconButton(
                        onClick = { onAdjust(15) },
                        modifier = Modifier.size(44.dp).bounceClick { onAdjust(15) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("+15", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutStepBar(
    exercises: List<Exercise>,
    setsByExercise: Map<Long, List<PendingSet>>,
    currentStepIndex: Int,
    onStepSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(items = exercises, key = { _, e -> e.id }) { index, exercise ->
            val isSelected = currentStepIndex == index
            val setsLogged = setsByExercise[exercise.id].orEmpty().filter { it.isCompleted }.size
            
                FilterChip(
                    selected = isSelected,
                    onClick = { onStepSelected(index) },
                    label = { Text("${index + 1}", fontWeight = FontWeight.Black) },
                    leadingIcon = {
                        if (setsLogged > 0) Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.semantics { contentDescription = "Exercise ${index + 1}: ${exercise.name}" },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
        }
        item {
            FilterChip(
                selected = currentStepIndex == exercises.size,
                onClick = { onStepSelected(exercises.size) },
                label = { Text("Finish") },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary, selectedLabelColor = MaterialTheme.colorScheme.onSecondary)
            )
        }
    }
}
