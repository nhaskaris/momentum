package com.eliteonetube.momentum

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.logic.*
import com.eliteonetube.momentum.ui.HomeScreen
import com.eliteonetube.momentum.ui.LoadingScreen
import com.eliteonetube.momentum.ui.theme.WeeklyCoachTheme
import com.eliteonetube.momentum.ui.workout.PendingSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN_ACTIVE_WORKOUT = "com.eliteonetube.momentum.action.OPEN_ACTIVE_WORKOUT"
        const val ACTION_OPEN_WEIGHT_ENTRY = "com.eliteonetube.momentum.action.OPEN_WEIGHT_ENTRY"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val openWorkoutRequests = MutableStateFlow(0)
    private val openWeightEntryRequests = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_ACTIVE_WORKOUT) {
            openWorkoutRequests.value++
        }
        if (intent.action == ACTION_OPEN_WEIGHT_ENTRY) {
            openWeightEntryRequests.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.action == ACTION_OPEN_ACTIVE_WORKOUT) {
            openWorkoutRequests.value++
        }
        if (intent.action == ACTION_OPEN_WEIGHT_ENTRY) {
            openWeightEntryRequests.value++
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        WorkScheduler.scheduleWeeklyRecalculation(applicationContext)
        WorkScheduler.scheduleDailyWeighInReminder(applicationContext)

        val database = WeightDatabase.getInstance(applicationContext)

        val weightDao = database.weightDao()
        val workoutDao = database.workoutDao()
        val foodDao = database.foodDao()
        val algorithm = CoachAlgorithm()

        lifecycleScope.launch {
            ExerciseSeeder.seedIfNeeded(applicationContext, workoutDao)
            FoodSeeder.seedIfNeeded(applicationContext, foodDao)
        }

        setContent {
            val savedProfile by weightDao.getUserProfile().collectAsState(initial = null)
            
            WeeklyCoachTheme(appTheme = savedProfile?.theme ?: AppTheme.SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val coroutineScope = rememberCoroutineScope()

                    val recentWeights by weightDao.getLastTwoWeeks().collectAsState(initial = emptyList())
                    val allWeights by weightDao.getAllWeights().collectAsState(initial = emptyList())
                    val recentSessions by workoutDao.getRecentSessions().collectAsState(initial = emptyList())
                    val allExercises by workoutDao.getAllExercises().collectAsState(initial = emptyList())
                    val allTemplates by workoutDao.getAllTemplates().collectAsState(initial = emptyList())
                    val allCheckIns by weightDao.getAllCheckIns().collectAsState(initial = emptyList())
                    val allWeightDates by weightDao.getAllWeightDates().collectAsState(initial = emptyList())

                    val activeSets by workoutDao.getActiveSets().collectAsState(initial = emptyList())
                    val openWorkoutRequest by openWorkoutRequests.collectAsState()
                    val openWeightEntryRequest by openWeightEntryRequests.collectAsState()

                    val today = remember { LocalDate.now().toString() }
                    val todayFoodLogs by foodDao.getFoodLogsForDate(today).collectAsState(initial = emptyList())
                    val allFoodItems by foodDao.getAllFoodItems().collectAsState(initial = emptyList())

                    var isProfileLoaded by remember { mutableStateOf(false) }
                    var currentCalorieTarget by remember { mutableIntStateOf(2000) }

                    val currentStreak = remember(allWeightDates) { StreakCalculator.currentStreak(allWeightDates) }
                    val loggedDates = remember(allWeightDates) {
                        allWeightDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                    }

                    LaunchedEffect(Unit) {
                        weightDao.getUserProfile().collect { isProfileLoaded = true }
                    }

                    LaunchedEffect(savedProfile) {
                        savedProfile?.let { currentCalorieTarget = it.currentCalorieTarget }
                    }

                    if (!isProfileLoaded) {
                        LoadingScreen()
                    } else {
                        HomeScreen(
                            currentCalorieTarget = currentCalorieTarget,
                            savedProfile = savedProfile,
                            recentWeights = recentWeights,
                            allWeights = allWeights,
                            recentSessions = recentSessions,
                            allExercises = allExercises,
                            allTemplates = allTemplates,
                            allCheckIns = allCheckIns,
                            todayFoodLogs = todayFoodLogs,
                            allFoodItems = allFoodItems,
                            activeSets = activeSets,
                            hasActiveWorkout = savedProfile?.hasActiveWorkout == true,
                            openWorkoutRequest = openWorkoutRequest,
                            openWeightEntryRequest = openWeightEntryRequest,
                            currentStreak = currentStreak,
                            totalDaysLogged = allWeightDates.size,
                            loggedDates = loggedDates,
                            onWeightSubmitted = { enteredWeight ->
                                coroutineScope.launch {
                                    val today = LocalDate.now().toString()
                                    weightDao.insertWeight(
                                        WeightEntry(
                                            date = today,
                                            weight = enteredWeight,
                                            calorieTargetAtEntry = currentCalorieTarget
                                        )
                                    )
                                    NotificationHelper.cancelWeighInReminder(applicationContext)
                                }
                            },
                            onPastWeightSubmitted = { date, enteredWeight ->
                                coroutineScope.launch {
                                    weightDao.insertWeight(
                                        WeightEntry(
                                            date = date,
                                            weight = enteredWeight,
                                            calorieTargetAtEntry = currentCalorieTarget
                                        )
                                    )
                                    if (date == LocalDate.now().toString()) {
                                        NotificationHelper.cancelWeighInReminder(applicationContext)
                                    }
                                }
                            },
                            onProfileUpdated = { updatedProfile ->
                                coroutineScope.launch {
                                    val recalculated = algorithm.calculateInitialMaintenance(
                                        weightKg = recentWeights.firstOrNull()?.weight ?: updatedProfile.height,
                                        heightCm = updatedProfile.height,
                                        age = updatedProfile.age,
                                        isMale = updatedProfile.isMale,
                                        averageDailySteps = updatedProfile.averageDailySteps,
                                        bodyFatPercentage = updatedProfile.bodyFatPercentage
                                    )
                                    
                                    var finalProfile = updatedProfile.copy(estimatedMaintenanceCalories = recalculated)
                                    if (finalProfile.goal == Goal.MAINTAIN) {
                                        finalProfile = finalProfile.copy(currentCalorieTarget = recalculated)
                                    }
                                    
                                    weightDao.saveProfile(finalProfile)
                                }
                            },
                            onGoalChanged = { newGoal ->
                                savedProfile?.let { profile ->
                                    coroutineScope.launch {
                                        val transition = algorithm.calculateGoalTransition(
                                            previousGoal = profile.goal,
                                            newGoal = newGoal,
                                            currentCalorieTarget = profile.currentCalorieTarget,
                                            estimatedMaintenance = profile.estimatedMaintenanceCalories
                                        )
                                        weightDao.saveProfile(
                                            profile.copy(
                                                goal = newGoal,
                                                currentCalorieTarget = transition.newTarget,
                                                pendingCalorieTarget = null,
                                                pendingAdjustmentReason = null
                                            )
                                        )
                                    }
                                }
                            },
                            getSetsForSession = { sessionId ->
                                workoutDao.getSetsForSession(sessionId).first()
                            },
                            onSessionDeleted = { sessionId ->
                                coroutineScope.launch {
                                    workoutDao.deleteSession(sessionId)
                                }
                            },
                            onCreateExercise = { name, muscleGroup, type, onCreated ->
                                coroutineScope.launch {
                                    val newExercise = Exercise(name = name, muscleGroup = muscleGroup, exerciseType = type)
                                    val newId = workoutDao.insertExercise(newExercise)
                                    onCreated(newExercise.copy(id = newId))
                                }
                            },
                            onTemplateCreated = { name, notes, exercises ->
                                coroutineScope.launch {
                                    val templateId = workoutDao.insertTemplate(
                                        WorkoutTemplate(name = name, notes = notes)
                                    )
                                    exercises.forEachIndexed { index, input ->
                                        workoutDao.insertTemplateExercise(
                                            TemplateExercise(
                                                templateId = templateId,
                                                exerciseId = input.exercise.id,
                                                targetSets = input.targetSets,
                                                targetReps = input.targetReps,
                                                targetWeightKg = input.targetWeightKg,
                                                orderIndex = index
                                            )
                                        )
                                    }
                                }
                            },
                            onTemplateDeleted = { templateId ->
                                coroutineScope.launch {
                                    workoutDao.deleteTemplate(templateId)
                                }
                            },
                            getExercisesForTemplate = { templateId ->
                                workoutDao.getExercisesForTemplate(templateId).first()
                            },
                            onAdjustmentAccepted = {
                                savedProfile?.let { profile ->
                                    coroutineScope.launch {
                                        weightDao.saveProfile(
                                            profile.copy(
                                                currentCalorieTarget = profile.pendingCalorieTarget ?: profile.currentCalorieTarget,
                                                pendingCalorieTarget = null,
                                                pendingAdjustmentReason = null
                                            )
                                        )
                                    }
                                }
                            },
                            onAdjustmentDismissed = {
                                savedProfile?.let { profile ->
                                    coroutineScope.launch {
                                        weightDao.saveProfile(
                                            profile.copy(
                                                pendingCalorieTarget = null,
                                                pendingAdjustmentReason = null
                                            )
                                        )
                                    }
                                }
                            },
                            onFoodLogged = { foodId, qty ->
                                coroutineScope.launch {
                                    foodDao.insertFoodLog(
                                        DailyFoodLog(
                                            date = LocalDate.now().toString(),
                                            foodItemId = foodId,
                                            quantity = qty
                                        )
                                    )
                                }
                            },
                            onFoodLogDeleted = { logId ->
                                coroutineScope.launch {
                                    foodDao.deleteFoodLog(logId)
                                }
                            },
                            onFoodLogUpdated = { logId, foodId, qty ->
                                coroutineScope.launch {
                                    foodDao.updateFoodLog(
                                        DailyFoodLog(
                                            id = logId,
                                            date = LocalDate.now().toString(),
                                            foodItemId = foodId,
                                            quantity = qty
                                        )
                                    )
                                }
                            },
                            onQuickLog = { item ->
                                coroutineScope.launch {
                                    val id = foodDao.insertFoodItem(item)
                                    foodDao.insertFoodLog(
                                        DailyFoodLog(
                                            date = LocalDate.now().toString(),
                                            foodItemId = id,
                                            quantity = 1.0
                                        )
                                    )
                                }
                            },
                            onNewFoodItemCreated = { item ->
                                coroutineScope.launch {
                                    foodDao.insertFoodItem(item)
                                }
                            },
                            onGetFoodByBarcode = { barcode ->
                                foodDao.getFoodItemByBarcode(barcode)
                            },
                            onUpdateActiveWorkout = { templateId, sets ->
                                coroutineScope.launch {
                                    weightDao.saveProfile(
                                        savedProfile!!.copy(
                                            activeWorkoutTemplateId = templateId,
                                            hasActiveWorkout = true
                                        )
                                    )
                                    val activeSets = sets.map { pendingSet ->
                                        ActiveWorkoutSet(
                                            exerciseId = pendingSet.exerciseId,
                                            setNumber = pendingSet.setNumber,
                                            weightKg = pendingSet.weightKg,
                                            reps = pendingSet.reps,
                                            notes = pendingSet.notes,
                                            isCompleted = pendingSet.isCompleted,
                                            durationSeconds = pendingSet.durationSeconds,
                                            distanceKm = pendingSet.distanceKm
                                        )
                                    }
                                    workoutDao.replaceActiveSets(activeSets)
                                }
                            },
                            onClearActiveWorkout = {
                                coroutineScope.launch {
                                    weightDao.saveProfile(
                                        savedProfile!!.copy(
                                            activeWorkoutTemplateId = null,
                                            hasActiveWorkout = false
                                        )
                                    )
                                    workoutDao.clearActiveSets()
                                }
                            },
                            onCheckInCompleted = { weight, photos ->
                                savedProfile?.let { profile ->
                                    coroutineScope.launch {
                                        val today = LocalDate.now().toString()
                                        weightDao.insertWeight(
                                            WeightEntry(
                                                date = today,
                                                weight = weight,
                                                calorieTargetAtEntry = profile.pendingCalorieTarget ?: profile.currentCalorieTarget
                                            )
                                        )
                                        weightDao.insertCheckIn(
                                            CheckIn(
                                                date = today,
                                                weight = weight,
                                                frontPhotoPath = photos.getOrNull(0)?.toString(),
                                                backPhotoPath = photos.getOrNull(1)?.toString(),
                                                sidePhotoPath = photos.getOrNull(2)?.toString(),
                                                calorieTargetBefore = profile.currentCalorieTarget,
                                                calorieTargetAfter = profile.pendingCalorieTarget ?: profile.currentCalorieTarget,
                                                adjustmentReason = profile.pendingAdjustmentReason ?: "Weekly Check-in"
                                            )
                                        )
                                        weightDao.saveProfile(
                                            profile.copy(
                                                currentCalorieTarget = profile.pendingCalorieTarget ?: profile.currentCalorieTarget,
                                                pendingCalorieTarget = null,
                                                pendingAdjustmentReason = null,
                                                checkInDue = false,
                                                lastCheckInDate = today
                                            )
                                        )
                                        NotificationHelper.cancelWeighInReminder(applicationContext)
                                    }
                                }
                            },
                            onWeightDeleted = { date ->
                                coroutineScope.launch {
                                    weightDao.deleteWeight(date)
                                }
                            },
                            onSessionSaved = { date: String, sets: List<PendingSet>, templateId: Long?, existingSessionId: Long? ->
                                coroutineScope.launch {
                                    val totalVolume = sets.sumOf { it.weightKg * it.reps }
                                    val exerciseCount = sets.map { it.exerciseId }.distinct().size
                                    val setCount = sets.size

                                    val sessionId = if (existingSessionId != null) {
                                        workoutDao.updateSession(
                                            WorkoutSession(
                                                id = existingSessionId,
                                                date = date,
                                                templateId = templateId,
                                                totalVolumeKg = totalVolume,
                                                exerciseCount = exerciseCount,
                                                setCount = setCount
                                            )
                                        )
                                        workoutDao.deleteSetsBySessionId(existingSessionId)
                                        existingSessionId
                                    } else {
                                        workoutDao.insertSession(
                                            WorkoutSession(
                                                date = date,
                                                templateId = templateId,
                                                totalVolumeKg = totalVolume,
                                                exerciseCount = exerciseCount,
                                                setCount = setCount
                                            )
                                        )
                                    }
                                    sets.forEach { pendingSet ->
                                        workoutDao.insertSet(
                                            LoggedSet(
                                                sessionId = sessionId,
                                                exerciseId = pendingSet.exerciseId,
                                                setNumber = pendingSet.setNumber,
                                                weightKg = pendingSet.weightKg,
                                                reps = pendingSet.reps,
                                                notes = pendingSet.notes,
                                                durationSeconds = pendingSet.durationSeconds,
                                                distanceKm = pendingSet.distanceKm
                                            )
                                        )
                                    }

                                    templateId?.let { tid: Long ->
                                        // "Save on top": Update template exercises to match the session
                                        workoutDao.deleteTemplateExercises(tid)
                                        
                                        val uniqueExerciseIds = sets.map { it.exerciseId }.distinct()
                                        uniqueExerciseIds.forEachIndexed { index, exId ->
                                            val exerciseSets = sets.filter { it.exerciseId == exId }
                                            if (exerciseSets.isNotEmpty()) {
                                                val avgReps = (exerciseSets.sumOf { it.reps }.toDouble() / exerciseSets.size).let { Math.round(it).toInt() }
                                                val avgWeight = exerciseSets.sumOf { it.weightKg } / exerciseSets.size
                                                val avgDuration = if (exerciseSets.any { it.durationSeconds != null }) {
                                                    exerciseSets.mapNotNull { it.durationSeconds }.average().toInt()
                                                } else null
                                                val avgDistance = if (exerciseSets.any { it.distanceKm != null }) {
                                                    exerciseSets.mapNotNull { it.distanceKm }.average()
                                                } else null

                                                workoutDao.insertTemplateExercise(
                                                    TemplateExercise(
                                                        templateId = tid,
                                                        exerciseId = exId,
                                                        targetSets = exerciseSets.size,
                                                        targetReps = avgReps,
                                                        targetWeightKg = avgWeight,
                                                        orderIndex = index,
                                                        targetDurationSeconds = avgDuration,
                                                        targetDistanceKm = avgDistance
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onOnboardingCompleted = { w: Double, h: Double, a: Int, male: Boolean, stepCount: Int, goal: Goal, customCalories: Int?, unitSystem: UnitSystem, bf: Double?, useHC: Boolean ->
                                val maintenance = algorithm.calculateInitialMaintenance(w, h, a, male, stepCount, bf)
                                val initialTarget = customCalories ?: when (goal) {
                                    Goal.CUT -> maintenance - 500
                                    Goal.BULK -> maintenance + 300
                                    Goal.MAINTAIN -> maintenance
                                    Goal.REVERSE -> maintenance - 300
                                }

                                coroutineScope.launch {
                                    weightDao.saveProfile(
                                        UserProfile(
                                            height = h,
                                            age = a,
                                            isMale = male,
                                            averageDailySteps = stepCount,
                                            estimatedMaintenanceCalories = maintenance,
                                            goal = goal,
                                            currentCalorieTarget = initialTarget,
                                            unitSystem = unitSystem,
                                            bodyFatPercentage = bf,
                                            useHealthConnect = useHC,
                                            useExternalApi = false
                                        )
                                    )
                                    weightDao.insertWeight(
                                        WeightEntry(
                                            date = LocalDate.now().toString(),
                                            weight = w,
                                            calorieTargetAtEntry = initialTarget
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
