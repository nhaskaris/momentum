package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.UnitSystem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    allExercises: List<Exercise>,
    unitSystem: UnitSystem,
    initialExercises: List<Exercise> = emptyList(), // ADDED PARAMETER
    initialSets: List<PendingSet> = emptyList(),
    getExerciseHistory: suspend (Long) -> List<LoggedSet> = { emptyList() },
    onCancel: () -> Unit,
    onFinish: (List<PendingSet>) -> Unit,
    onCreateExercise: (String, String, (Exercise) -> Unit) -> Unit = { _, _, _ -> }
) {
    var showExercisePicker by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Derive initial exercises either from parameter or from initialSets
    val resolvedInitialExercises = remember(initialExercises, initialSets, allExercises) {
        if (initialExercises.isNotEmpty()) {
            initialExercises
        } else {
            initialSets.map { it.exerciseId }.distinct().mapNotNull { id -> allExercises.find { it.id == id } }
        }
    }

    val initialMap = remember(initialSets) {
        initialSets.groupBy { it.exerciseId }
    }

    var sessionExercises by remember { mutableStateOf(resolvedInitialExercises) }
    var setsByExercise by remember { mutableStateOf(initialMap) }

    // Re-synchronize state whenever initial values change
    LaunchedEffect(resolvedInitialExercises, initialMap) {
        sessionExercises = resolvedInitialExercises
        setsByExercise = initialMap
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

    LaunchedEffect(sessionExercises.size) {
        if (sessionExercises.isNotEmpty() && pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
        }
    }

    val currentStepIndex = pagerState.currentPage.coerceAtMost((pageCount - 1).coerceAtLeast(0))
    val isFinishStep = currentStepIndex == sessionExercises.size && sessionExercises.isNotEmpty()

    val allSets = remember(setsByExercise, sessionExercises) {
        sessionExercises.flatMap { setsByExercise[it.id].orEmpty() }
    }
    val totalSetsCount = allSets.size
    val totalRepsCount = allSets.sumOf { it.reps }
    val totalVolumeKg = allSets.sumOf { it.weightKg * it.reps }

    val volumeDisplay = if (unitSystem == UnitSystem.IMPERIAL) {
        "${(totalVolumeKg * 2.20462).toInt()} lbs"
    } else {
        "${totalVolumeKg.toInt()} kg"
    }

    if (showExercisePicker) {
        ExercisePickerScreen(
            allExercises = allExercises,
            onDismiss = { showExercisePicker = false },
            onExerciseSelected = { exercise ->
                if (sessionExercises.none { it.id == exercise.id }) {
                    sessionExercises = sessionExercises + exercise
                    setsByExercise = setsByExercise + (exercise.id to emptyList())
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(sessionExercises.size - 1)
                    }
                }
                showExercisePicker = false
            },
            onCreateExercise = { name, muscleGroup, onCreated ->
                onCreateExercise(name, muscleGroup) { newlyCreatedExercise ->
                    if (sessionExercises.none { it.id == newlyCreatedExercise.id }) {
                        sessionExercises = sessionExercises + newlyCreatedExercise
                        setsByExercise = setsByExercise + (newlyCreatedExercise.id to emptyList())
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(sessionExercises.size - 1)
                        }
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
            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
            title = { Text("Discard workout session?") },
            text = { Text("All logged sets for this workout will be permanently discarded.") },
            confirmButton = {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard Workout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Keep Logging")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Active Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (sessionExercises.isEmpty()) "Step 0 of 0"
                            else if (isFinishStep) "Final Step: Review"
                            else "Step ${currentStepIndex + 1} of ${sessionExercises.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = {
                        if (allSets.isNotEmpty()) showCancelConfirm = true else onCancel()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel Session"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showExercisePicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Exercise")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sessionExercises.isNotEmpty()) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentStepIndex - 1)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        }

                        if (isFinishStep) {
                            Button(
                                onClick = { onFinish(allSets) },
                                enabled = allSets.isNotEmpty(),
                                modifier = Modifier
                                    .weight(2f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Complete Workout", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentStepIndex + 1)
                                    }
                                },
                                modifier = Modifier
                                    .weight(2f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (currentStepIndex == sessionExercises.size - 1) "Review Workout" else "Next Exercise",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sessionExercises.isNotEmpty()) {
                WorkoutStepBar(
                    exercises = sessionExercises,
                    setsByExercise = setsByExercise,
                    currentStepIndex = currentStepIndex,
                    onStepSelected = { targetIndex ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (sessionExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    EmptyWorkoutStateCard(onAddExerciseClick = { showExercisePicker = true })
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    if (page >= sessionExercises.size) {
                        WorkoutSummaryStep(
                            totalSets = totalSetsCount,
                            totalReps = totalRepsCount,
                            totalVolumeDisplay = volumeDisplay,
                            exercises = sessionExercises,
                            setsByExercise = setsByExercise,
                            onEditExerciseStep = { index ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    } else {
                        val currentExercise = sessionExercises[page]
                        val historySets = exerciseHistoryMap[currentExercise.id] ?: emptyList()

                        ExerciseSetLogger(
                            exercise = currentExercise,
                            sets = setsByExercise[currentExercise.id].orEmpty(),
                            historySets = historySets,
                            unitSystem = unitSystem,
                            onSetAdded = { set ->
                                val list = setsByExercise[currentExercise.id].orEmpty()
                                setsByExercise = setsByExercise + (currentExercise.id to (list + set))
                            },
                            onSetUpdated = { setNumber, updated ->
                                val list = setsByExercise[currentExercise.id].orEmpty().map {
                                    if (it.setNumber == setNumber) updated else it
                                }
                                setsByExercise = setsByExercise + (currentExercise.id to list)
                            },
                            onSetRemoved = { setNumber ->
                                val list = setsByExercise[currentExercise.id].orEmpty()
                                    .filterNot { it.setNumber == setNumber }
                                    .mapIndexed { index, pendingSet -> pendingSet.copy(setNumber = index + 1) }
                                setsByExercise = setsByExercise + (currentExercise.id to list)
                            },
                            onExerciseRemoved = {
                                sessionExercises = sessionExercises.filterNot { it.id == currentExercise.id }
                                setsByExercise = setsByExercise - currentExercise.id
                            }
                        )
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(
            items = exercises,
            key = { _, exercise -> exercise.id }
        ) { index, exercise ->
            val isSelected = currentStepIndex == index
            val setsLogged = setsByExercise[exercise.id].orEmpty().size
            val isComplete = setsLogged > 0

            FilterChip(
                selected = isSelected,
                onClick = { onStepSelected(index) },
                label = { Text("${index + 1}. ${exercise.name}") },
                leadingIcon = {
                    if (isComplete) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingIcon = {
                    if (setsLogged > 0) {
                        Badge(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("$setsLogged")
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        item(key = "finish_step") {
            FilterChip(
                selected = currentStepIndex == exercises.size,
                onClick = { onStepSelected(exercises.size) },
                label = { Text("Finish") },
                leadingIcon = {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    }
}