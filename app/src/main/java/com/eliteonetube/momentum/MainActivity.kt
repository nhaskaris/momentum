package com.eliteonetube.momentum

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.room3.Room
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.logic.*
import com.eliteonetube.momentum.ui.HomeScreen
import com.eliteonetube.momentum.ui.LoadingScreen
import com.eliteonetube.momentum.ui.theme.WeeklyCoachTheme
import com.eliteonetube.momentum.ui.workout.PendingSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        WorkScheduler.scheduleWeeklyRecalculation(applicationContext)
        WorkScheduler.scheduleDailyWeighInReminder(applicationContext)

        val database = Room.databaseBuilder<WeightDatabase>(
            context = applicationContext,
            name = "weight_tracker_db",
        ).addMigrations(
            WeightDatabase.MIGRATION_12_13,
            WeightDatabase.MIGRATION_13_14,
            WeightDatabase.MIGRATION_14_15,
            WeightDatabase.MIGRATION_15_16,
            WeightDatabase.MIGRATION_16_17,
            WeightDatabase.MIGRATION_17_18,
            WeightDatabase.MIGRATION_18_19,
            WeightDatabase.MIGRATION_19_20,
            WeightDatabase.MIGRATION_20_21,
        ).fallbackToDestructiveMigration().build()

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
                    val recentSessions by workoutDao.getRecentSessions().collectAsState(initial = emptyList())
                    val allExercises by workoutDao.getAllExercises().collectAsState(initial = emptyList())
                    val allTemplates by workoutDao.getAllTemplates().collectAsState(initial = emptyList())
                    val allCheckIns by weightDao.getAllCheckIns().collectAsState(initial = emptyList())
                    val allWeightDates by weightDao.getAllWeightDates().collectAsState(initial = emptyList())

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
                            recentSessions = recentSessions,
                            allExercises = allExercises,
                            allTemplates = allTemplates,
                            allCheckIns = allCheckIns,
                            todayFoodLogs = todayFoodLogs,
                            allFoodItems = allFoodItems,
                            currentStreak = currentStreak,
                            totalDaysLogged = allWeightDates.size,
                            loggedDates = loggedDates,
                            onWeightSubmitted = { enteredWeight ->
                                coroutineScope.launch {
                                    weightDao.insertWeight(
                                        WeightEntry(
                                            date = LocalDate.now().toString(),
                                            weight = enteredWeight,
                                            calorieTargetAtEntry = currentCalorieTarget
                                        )
                                    )
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
                                    
                                    // If user is on a maintenance goal, we should update their current target 
                                    // to match the new maintenance estimate immediately.
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
                            onCreateExercise = { name, muscleGroup, onCreated ->
                                coroutineScope.launch {
                                    val newExercise = Exercise(name = name, muscleGroup = muscleGroup)
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
                            onNewFoodItemCreated = { item ->
                                coroutineScope.launch {
                                    foodDao.insertFoodItem(item)
                                }
                            },
                            onGetFoodByBarcode = { barcode ->
                                foodDao.getFoodItemByBarcode(barcode)
                            },
                            onCheckInCompleted = { weight, photos ->
                                savedProfile?.let { profile ->
                                    coroutineScope.launch {
                                        val today = LocalDate.now().toString()
                                        
                                        // 1. Insert Weight
                                        weightDao.insertWeight(
                                            WeightEntry(
                                                date = today,
                                                weight = weight,
                                                calorieTargetAtEntry = profile.pendingCalorieTarget ?: profile.currentCalorieTarget
                                            )
                                        )
                                        
                                        // 2. Save CheckIn Record
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

                                        // 3. Update Profile
                                        weightDao.saveProfile(
                                            profile.copy(
                                                currentCalorieTarget = profile.pendingCalorieTarget ?: profile.currentCalorieTarget,
                                                pendingCalorieTarget = null,
                                                pendingAdjustmentReason = null,
                                                checkInDue = false,
                                                lastCheckInDate = today
                                            )
                                        )
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
                                                notes = pendingSet.notes
                                            )
                                        )
                                    }

                                    // If this session was started from a routine, update the routine's 
                                    // default weights/reps to reflect what was just lifted.
                                    templateId?.let { tid: Long ->
                                        val uniqueExerciseIds = sets.map { it.exerciseId }.distinct()

                                        uniqueExerciseIds.forEach { exId ->
                                            val exerciseSets = sets.filter { it.exerciseId == exId }
                                            if (exerciseSets.isNotEmpty()) {
                                                val avgReps = (exerciseSets.sumOf { it.reps }.toDouble() / exerciseSets.size)
                                                    .let { Math.round(it).toInt() }
                                                val avgWeight = exerciseSets.sumOf { it.weightKg } / exerciseSets.size

                                                // Update existing template exercise record
                                                workoutDao.updateTemplateExerciseTargets(
                                                    templateId = tid,
                                                    exerciseId = exId,
                                                    newSets = exerciseSets.size,
                                                    newReps = avgReps,
                                                    newWeight = avgWeight
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
                                            useHealthConnect = useHC
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
