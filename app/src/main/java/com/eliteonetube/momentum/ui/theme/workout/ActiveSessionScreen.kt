package com.eliteonetube.momentum.ui.workout

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.logic.RestTimerService
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

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
    onCreateExercise: (String, String, (Exercise) -> Unit) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var showExercisePicker by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    
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

    val allSets = remember(setsByExercise, sessionExercises) { sessionExercises.flatMap { setsByExercise[it.id].orEmpty() } }
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
                    coroutineScope.launch { pagerState.animateScrollToPage(sessionExercises.size - 1) }
                }
                showExercisePicker = false
            },
            onCreateExercise = { name, muscleGroup, onCreated ->
                onCreateExercise(name, muscleGroup) { newlyCreatedExercise ->
                    if (sessionExercises.none { it.id == newlyCreatedExercise.id }) {
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
                title = { Text("Active Session", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { if (allSets.isNotEmpty()) showCancelConfirm = true else onCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { showExercisePicker = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold)
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
                                Text(if (currentStepIndex == sessionExercises.size - 1) "Summary" else "Next Exercise", fontWeight = FontWeight.Bold)
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
            // Hero Progress Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), Color.Transparent)))
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isFinishStep) "READY TO FINISH" else "SESSION IN PROGRESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = volumeDisplay,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black
                    )
                    Text("Current Volume", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
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
                            }
                        )
                    }
                }

                if (timerActive) {
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

@Composable
private fun RestTimerOverlay(
    timeLeft: Long,
    onAdjust: (Long) -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Rest Timer", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (timeLeft > 0) "%d:%02d".format(timeLeft / 60, timeLeft % 60) else "GO!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { onAdjust(15) }, modifier = Modifier.size(44.dp)) { Text("+15", fontSize = 10.sp) }
                IconButton(onClick = onStop, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null)
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
                label = { Text(exercise.name) },
                leadingIcon = {
                    if (setsLogged > 0) Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
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
