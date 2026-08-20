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
import kotlinx.coroutines.flow.combine
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        WorkScheduler.scheduleWeeklyRecalculation(applicationContext)

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
                MomentumAppContent(
                    weightDao = weightDao,
                    workoutDao = workoutDao,
                    foodDao = foodDao,
                    algorithm = algorithm,
                    savedProfile = savedProfile,
                    openWorkoutRequests = openWorkoutRequests,
                    openWeightEntryRequests = openWeightEntryRequests
                )
            }
        }
    }
}

@Composable
fun MomentumAppContent(
    weightDao: WeightDao,
    workoutDao: WorkoutDao,
    foodDao: FoodDao,
    algorithm: CoachAlgorithm,
    savedProfile: UserProfile?,
    openWorkoutRequests: MutableStateFlow<Int>,
    openWeightEntryRequests: MutableStateFlow<Int>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Core State Collection
    val recentWeights by weightDao.getLastTwoWeeks().collectAsState(initial = emptyList())
    val allWeights by weightDao.getAllWeights().collectAsState(initial = emptyList())
    val recentSessions by workoutDao.getRecentSessions().collectAsState(initial = emptyList())
    val allExercises by workoutDao.getAllExercises().collectAsState(initial = emptyList())
    val allTemplates by workoutDao.getAllTemplates().collectAsState(initial = emptyList())
    val allCheckIns by weightDao.getAllCheckIns().collectAsState(initial = emptyList())
    val allMeals by foodDao.getAllMeals().collectAsState(initial = emptyList())
    val allWeightDates by weightDao.getAllWeightDates().collectAsState(initial = emptyList())

    val activeSets by workoutDao.getActiveSets().collectAsState(initial = emptyList())
    val openWorkoutRequest by openWorkoutRequests.collectAsState()
    val openWeightEntryRequest by openWeightEntryRequests.collectAsState()

    val today = remember { LocalDate.now().toString() }
    val todayFoodLogsFlow = remember(today) {
        combine(
            foodDao.getFoodLogsForDate(today),
            foodDao.getDailyMealLogsForDate(today)
        ) { items, meals ->
            val mappedMeals = meals.map { meal ->
                FoodLogWithItem(
                    id = meal.id,
                    foodItemId = meal.mealId,
                    quantity = 1.0,
                    name = meal.name,
                    calories = meal.calories,
                    protein = meal.protein,
                    fat = meal.fat,
                    carbs = meal.carbs,
                    isMeal = true
                )
            }
            items + mappedMeals
        }
    }
    val todayFoodLogs by todayFoodLogsFlow.collectAsState(initial = emptyList())
    val allFoodItems by foodDao.getAllFoodItems().collectAsState(initial = emptyList())

    // 2. High-Frequency Logic (Remembered/Isolated)
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
        savedProfile?.let { 
            currentCalorieTarget = it.currentCalorieTarget
            WorkScheduler.scheduleDailyReminders(context.applicationContext, it)
        }
    }

    if (!isProfileLoaded) {
        LoadingScreen()
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                allMeals = allMeals,
                activeSets = activeSets,
                hasActiveWorkout = savedProfile?.hasActiveWorkout == true,
                openWorkoutRequest = openWorkoutRequest,
                openWeightEntryRequest = openWeightEntryRequest,
                currentStreak = currentStreak,
                totalDaysLogged = allWeightDates.size,
                loggedDates = loggedDates,
                onWeightSubmitted = { enteredWeight ->
                    coroutineScope.launch {
                        val now = LocalDate.now().toString()
                        weightDao.insertWeight(WeightEntry(date = now, weight = enteredWeight, calorieTargetAtEntry = currentCalorieTarget))
                        NotificationHelper.cancelWeighInReminder(context.applicationContext)
                    }
                },
                onPastWeightSubmitted = { date, enteredWeight ->
                    coroutineScope.launch {
                        weightDao.insertWeight(WeightEntry(date = date, weight = enteredWeight, calorieTargetAtEntry = currentCalorieTarget))
                        if (date == LocalDate.now().toString()) NotificationHelper.cancelWeighInReminder(context.applicationContext)
                    }
                },
                onWeightDeleted = { date -> coroutineScope.launch { weightDao.deleteWeight(date) } },
                onProfileUpdated = { updatedProfile ->
                    coroutineScope.launch {
                        val recalculated = algorithm.calculateInitialMaintenance(
                            recentWeights.firstOrNull()?.weight ?: updatedProfile.height,
                            updatedProfile.height, updatedProfile.age, updatedProfile.isMale,
                            updatedProfile.averageDailySteps, updatedProfile.bodyFatPercentage
                        )
                        var final = updatedProfile.copy(estimatedMaintenanceCalories = recalculated)
                        if (final.goal == Goal.MAINTAIN) final = final.copy(currentCalorieTarget = recalculated)
                        weightDao.saveProfile(final)
                    }
                },
                onGoalChanged = { newGoal ->
                    savedProfile?.let { profile ->
                        coroutineScope.launch {
                            val trans = algorithm.calculateGoalTransition(profile.goal, newGoal, profile.currentCalorieTarget, profile.estimatedMaintenanceCalories)
                            weightDao.saveProfile(profile.copy(goal = newGoal, currentCalorieTarget = trans.newTarget, pendingCalorieTarget = null, pendingAdjustmentReason = null))
                        }
                    }
                },
                onAdjustmentAccepted = {
                    savedProfile?.let { p ->
                        coroutineScope.launch {
                            weightDao.saveProfile(p.copy(currentCalorieTarget = p.pendingCalorieTarget ?: p.currentCalorieTarget, pendingCalorieTarget = null, pendingAdjustmentReason = null))
                        }
                    }
                },
                onAdjustmentDismissed = {
                    savedProfile?.let { p ->
                        coroutineScope.launch { weightDao.saveProfile(p.copy(pendingCalorieTarget = null, pendingAdjustmentReason = null)) }
                    }
                },
                onFoodLogged = { foodId, qty ->
                    coroutineScope.launch { foodDao.insertFoodLog(DailyFoodLog(date = LocalDate.now().toString(), foodItemId = foodId, quantity = qty)) }
                },
                onFoodLogDeleted = { id, isMeal ->
                    coroutineScope.launch { if (isMeal) foodDao.deleteDailyMealLog(id) else foodDao.deleteFoodLog(id) }
                },
                onFoodLogUpdated = { id, fId, q ->
                    coroutineScope.launch { foodDao.updateFoodLog(DailyFoodLog(id = id, date = LocalDate.now().toString(), foodItemId = fId, quantity = q)) }
                },
                onLogMeal = { mealId ->
                    coroutineScope.launch {
                        val meal = foodDao.getAllMeals().first().find { it.id == mealId } ?: return@launch
                        val items = foodDao.getItemsForMeal(mealId).first()
                        foodDao.insertDailyMealLog(DailyMealLog(
                            date = LocalDate.now().toString(), mealId = mealId, name = meal.name,
                            calories = items.sumOf { it.calories * it.quantity },
                            protein = items.sumOf { it.protein * it.quantity },
                            fat = items.sumOf { it.fat * it.quantity },
                            carbs = items.sumOf { it.carbs * it.quantity }
                        ))
                    }
                },
                onMealCreated = { name, items ->
                    coroutineScope.launch {
                        val mid = foodDao.insertMeal(Meal(name = name))
                        items.forEach { (f, q) -> foodDao.insertMealFoodItem(MealFoodItem(mealId = mid, foodItemId = f.id, quantity = q)) }
                    }
                },
                onFoodCreated = { item -> coroutineScope.launch { foodDao.insertFoodItem(item) } },
                onQuickLog = { item ->
                    coroutineScope.launch {
                        val fid = foodDao.insertFoodItem(item)
                        foodDao.insertFoodLog(DailyFoodLog(date = LocalDate.now().toString(), foodItemId = fid, quantity = 1.0))
                    }
                },
                onNewFoodItemCreated = { item -> coroutineScope.launch { foodDao.insertFoodItem(item) } },
                onGetFoodByBarcode = { bc -> foodDao.getFoodItemByBarcode(bc) },
                onUpdateActiveWorkout = { tid, sets ->
                    coroutineScope.launch {
                        weightDao.saveProfile(savedProfile!!.copy(activeWorkoutTemplateId = tid, hasActiveWorkout = true))
                        workoutDao.replaceActiveSets(sets.map { ps ->
                            ActiveWorkoutSet(exerciseId = ps.exerciseId, setNumber = ps.setNumber, weightKg = ps.weightKg, reps = ps.reps, notes = ps.notes, isCompleted = ps.isCompleted, durationSeconds = ps.durationSeconds, distanceKm = ps.distanceKm)
                        })
                    }
                },
                onClearActiveWorkout = {
                    coroutineScope.launch {
                        weightDao.saveProfile(savedProfile!!.copy(activeWorkoutTemplateId = null, hasActiveWorkout = false))
                        workoutDao.clearActiveSets()
                    }
                },
                onCheckInCompleted = { w, ph ->
                    savedProfile?.let { p ->
                        coroutineScope.launch {
                            val now = LocalDate.now().toString()
                            weightDao.insertWeight(WeightEntry(date = now, weight = w, calorieTargetAtEntry = p.pendingCalorieTarget ?: p.currentCalorieTarget))
                            weightDao.insertCheckIn(CheckIn(date = now, weight = w, frontPhotoPath = ph.getOrNull(0)?.toString(), backPhotoPath = ph.getOrNull(1)?.toString(), sidePhotoPath = ph.getOrNull(2)?.toString(), calorieTargetBefore = p.currentCalorieTarget, calorieTargetAfter = p.pendingCalorieTarget ?: p.currentCalorieTarget, adjustmentReason = p.pendingAdjustmentReason ?: "Weekly Review"))
                            weightDao.saveProfile(p.copy(currentCalorieTarget = p.pendingCalorieTarget ?: p.currentCalorieTarget, pendingCalorieTarget = null, pendingAdjustmentReason = null, checkInDue = false, lastCheckInDate = now))
                            NotificationHelper.cancelWeighInReminder(context.applicationContext)
                        }
                    }
                },
                getSetsForSession = { sid -> workoutDao.getSetsForSession(sid).first() },
                getExercisesForTemplate = { tid -> workoutDao.getExercisesForTemplate(tid).first() },
                getSetsForTemplateExercise = { teid -> workoutDao.getSetsForTemplateExercise(teid) },
                onSessionSaved = { date, setsList, tid, exSid ->
                    coroutineScope.launch {
                        val valid = setsList.filter { it.isCompleted || it.weightKg > 0 || it.reps > 0 }
                        if (valid.isEmpty()) return@launch
                        val sid = if (exSid != null) {
                            workoutDao.updateSession(WorkoutSession(id = exSid, date = date, templateId = tid, totalVolumeKg = valid.sumOf { it.weightKg * it.reps }, exerciseCount = valid.map { it.exerciseId }.distinct().size, setCount = valid.size))
                            workoutDao.deleteSetsBySessionId(exSid); exSid
                        } else {
                            workoutDao.insertSession(WorkoutSession(date = date, templateId = tid, totalVolumeKg = valid.sumOf { it.weightKg * it.reps }, exerciseCount = valid.map { it.exerciseId }.distinct().size, setCount = valid.size))
                        }
                        valid.forEach { ps -> workoutDao.insertSet(LoggedSet(sessionId = sid, exerciseId = ps.exerciseId, setNumber = ps.setNumber, weightKg = ps.weightKg, reps = ps.reps, notes = ps.notes, durationSeconds = ps.durationSeconds, distanceKm = ps.distanceKm)) }
                        tid?.let { id ->
                            workoutDao.deleteTemplateSetsByTemplateId(id); workoutDao.deleteTemplateExercises(id)
                            setsList.map { it.exerciseId }.distinct().forEachIndexed { idx, eid ->
                                val exs = setsList.filter { it.exerciseId == eid }
                                val vxs = exs.filter { it.setNumber > 0 && (it.reps > 0 || it.weightKg > 0) }
                                val teid = workoutDao.insertTemplateExercise(TemplateExercise(templateId = id, exerciseId = eid, targetSets = if (vxs.isNotEmpty()) vxs.size else 3, targetReps = if (vxs.isNotEmpty()) vxs.first().reps else 10, targetWeightKg = if (vxs.isNotEmpty()) vxs.first().weightKg else 0.0, orderIndex = idx))
                                if (vxs.isNotEmpty()) vxs.forEach { ps -> workoutDao.insertTemplateSet(TemplateSet(templateExerciseId = teid, setNumber = ps.setNumber, targetReps = ps.reps, targetWeightKg = ps.weightKg, targetDurationSeconds = ps.durationSeconds, targetDistanceKm = ps.distanceKm)) }
                                else repeat(3) { sn -> workoutDao.insertTemplateSet(TemplateSet(templateExerciseId = teid, setNumber = sn + 1, targetReps = 10, targetWeightKg = 0.0)) }
                            }
                        }
                    }
                },
                onSessionDeleted = { sid -> coroutineScope.launch { workoutDao.deleteSession(sid) } },
                onCreateExercise = { n, m, t, oc -> coroutineScope.launch { val ex = Exercise(name = n, muscleGroup = m, exerciseType = t); val nid = workoutDao.insertExercise(ex); oc(ex.copy(id = nid)) } },
                onTemplateCreated = { n, nt, exs ->
                    coroutineScope.launch {
                        val tid = workoutDao.insertTemplate(WorkoutTemplate(name = n, notes = nt))
                        exs.forEachIndexed { i, input ->
                            val teid = workoutDao.insertTemplateExercise(TemplateExercise(templateId = tid, exerciseId = input.exercise.id, targetSets = input.targetSets, targetReps = input.targetReps, targetWeightKg = input.targetWeightKg, orderIndex = i))
                            repeat(input.targetSets) { sn -> workoutDao.insertTemplateSet(TemplateSet(templateExerciseId = teid, setNumber = sn + 1, targetReps = input.targetReps, targetWeightKg = input.targetWeightKg)) }
                        }
                    }
                },
                onTemplateDeleted = { tid -> coroutineScope.launch { workoutDao.deleteTemplate(tid) } },
                onOnboardingCompleted = { w, h, a, m, s, g, cc, us, bf, hc ->
                    val main = algorithm.calculateInitialMaintenance(w, h, a, m, s, bf)
                    val init = cc ?: when (g) { Goal.CUT -> main - 500; Goal.BULK -> main + 300; Goal.MAINTAIN -> main; Goal.REVERSE -> main - 300 }
                    coroutineScope.launch {
                        weightDao.saveProfile(UserProfile(height = h, age = a, isMale = m, averageDailySteps = s, estimatedMaintenanceCalories = main, goal = g, currentCalorieTarget = init, unitSystem = us, bodyFatPercentage = bf, useHealthConnect = hc, useExternalApi = false))
                        weightDao.insertWeight(WeightEntry(date = LocalDate.now().toString(), weight = w, calorieTargetAtEntry = init))
                    }
                }
            )
        }
    }
}
