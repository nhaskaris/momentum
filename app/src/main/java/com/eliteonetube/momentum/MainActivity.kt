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
import com.eliteonetube.momentum.logic.utils.MediaUtils
import com.eliteonetube.momentum.ui.components.rememberCurrentDate
import com.eliteonetube.momentum.ui.diaryFor
import com.eliteonetube.momentum.ui.rememberAppData
import com.eliteonetube.momentum.logic.algorithm.*
import com.eliteonetube.momentum.logic.api.*
import com.eliteonetube.momentum.logic.parsers.*
import com.eliteonetube.momentum.logic.seeders.*
import com.eliteonetube.momentum.logic.services.*
import com.eliteonetube.momentum.logic.utils.*
import com.eliteonetube.momentum.ui.components.LoadingScreen
import com.eliteonetube.momentum.ui.screens.home.HomeScreen
import com.eliteonetube.momentum.ui.theme.WeeklyCoachTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN_ACTIVE_WORKOUT = "com.eliteonetube.momentum.action.OPEN_ACTIVE_WORKOUT"
        const val ACTION_OPEN_WEIGHT_ENTRY = "com.eliteonetube.momentum.action.OPEN_WEIGHT_ENTRY"
        const val ACTION_OPEN_CHECK_IN = "com.eliteonetube.momentum.action.OPEN_CHECK_IN"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val openWorkoutRequests = MutableStateFlow(0)
    private val openWeightEntryRequests = MutableStateFlow(0)
    private val openCheckInRequests = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_ACTIVE_WORKOUT -> openWorkoutRequests.value++
            ACTION_OPEN_WEIGHT_ENTRY -> openWeightEntryRequests.value++
            ACTION_OPEN_CHECK_IN -> openCheckInRequests.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after rotation or process restore the same intent comes back
        // and would reopen the check-in (or workout) the user already left
        if (savedInstanceState == null) handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
                    openWeightEntryRequests = openWeightEntryRequests,
                    openCheckInRequests = openCheckInRequests
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
    openWeightEntryRequests: MutableStateFlow<Int>,
    openCheckInRequests: MutableStateFlow<Int>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Keeps active-workout saves and clears in call order so a late save cannot revive a finished workout
    val activeWorkoutMutex = remember { Mutex() }

    // 1. Data: collected once; each screen reads only what it shows, so a change redraws just those
    // Stays current past midnight, so "today" queries roll over instead of freezing at app start
    val currentDate by rememberCurrentDate()
    val appData = rememberAppData(weightDao, workoutDao, foodDao, currentDate)
    // Collected only while the full history screen is open
    val allSessionsFlow = remember(workoutDao) { workoutDao.getAllSessions() }

    val openWorkoutRequest by openWorkoutRequests.collectAsState()
    val openWeightEntryRequest by openWeightEntryRequests.collectAsState()
    val openCheckInRequest by openCheckInRequests.collectAsState()

    // 2. High-Frequency Logic (Remembered/Isolated)
    var isProfileLoaded by remember { mutableStateOf(false) }
    var currentCalorieTarget by remember { mutableIntStateOf(2000) }

    LaunchedEffect(Unit) {
        weightDao.getUserProfile().collect { isProfileLoaded = true }
    }

    LaunchedEffect(savedProfile) {
        savedProfile?.let { currentCalorieTarget = it.currentCalorieTarget }
    }

    // Reschedule notifications only when their settings change, not on every profile save
    val reminderSettings = savedProfile?.let { Triple(it.remindersEnabled, it.morningReminderTime, it.eveningReminderTime) }
    LaunchedEffect(reminderSettings) {
        WorkScheduler.scheduleDailyReminders(context.applicationContext, savedProfile ?: return@LaunchedEffect)
    }
    var scheduledCheckInTime by remember { mutableStateOf<java.time.LocalTime?>(null) }
    val checkInTime = savedProfile?.let { WorkScheduler.reminderTime(it, WorkScheduler.ReminderSlot.MORNING) }
    LaunchedEffect(checkInTime) {
        if (checkInTime == null) return@LaunchedEffect
        // Keep an already-queued check-in on app start; replace it only when the time actually changed
        WorkScheduler.scheduleWeeklyCheckIn(
            context.applicationContext,
            checkInTime,
            replace = scheduledCheckInTime != null && scheduledCheckInTime != checkInTime
        )
        scheduledCheckInTime = checkInTime
    }

    if (!isProfileLoaded) {
        LoadingScreen()
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
                currentCalorieTarget = currentCalorieTarget,
                savedProfile = savedProfile,
                data = appData,
                allSessionsFlow = allSessionsFlow,
                hasActiveWorkout = savedProfile?.hasActiveWorkout == true,
                openWorkoutRequest = openWorkoutRequest,
                openWeightEntryRequest = openWeightEntryRequest,
                openCheckInRequest = openCheckInRequest,
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
                        // Without any logged weight there's nothing to recalculate from, so keep the current estimate
                        val latestWeightKg = appData.recentWeights.firstOrNull()?.weight
                        if (latestWeightKg == null) {
                            weightDao.saveProfile(updatedProfile)
                            return@launch
                        }
                        val recalculated = algorithm.calculateInitialMaintenance(
                            latestWeightKg,
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
                            weightDao.saveProfile(p.copy(
                                currentCalorieTarget = p.pendingCalorieTarget ?: p.currentCalorieTarget, 
                                pendingCalorieTarget = null, 
                                pendingAdjustmentReason = null,
                                checkInDue = false
                            ))
                        }
                    }
                },
                onAdjustmentDismissed = {
                    savedProfile?.let { p ->
                        coroutineScope.launch { 
                            weightDao.saveProfile(p.copy(
                                pendingCalorieTarget = null, 
                                pendingAdjustmentReason = null,
                                checkInDue = false
                            )) 
                        }
                    }
                },
                onFoodLogged = { foodId, qty, target ->
                    coroutineScope.launch {
                        foodDao.insertFoodLog(DailyFoodLog(date = target.date, foodItemId = foodId, quantity = qty, mealSlot = target.slot))
                    }
                },
                onFoodLogDeleted = { id, isMeal ->
                    coroutineScope.launch { if (isMeal) foodDao.deleteDailyMealLog(id) else foodDao.deleteFoodLog(id) }
                },
                // Only the amount changes: the entry keeps its day and section
                onFoodLogUpdated = { id, q -> coroutineScope.launch { foodDao.updateFoodLogQuantity(id, q) } },
                onFoodLogMoved = { id, isMeal, slot ->
                    coroutineScope.launch { if (isMeal) foodDao.setMealLogSlot(id, slot) else foodDao.setFoodLogSlot(id, slot) }
                },
                copyDiarySection = { fromDate, toDate, slot -> foodDao.copySection(fromDate, toDate, slot) },
                diaryForDate = { date -> foodDao.diaryFor(date) },
                onLogMeal = { mealId, target ->
                    coroutineScope.launch {
                        val meal = foodDao.getAllMeals().first().find { it.id == mealId } ?: return@launch
                        val items = foodDao.getItemsForMeal(mealId).first()
                        foodDao.insertDailyMealLog(DailyMealLog(
                            date = target.date, mealId = mealId, name = meal.name, mealSlot = target.slot,
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
                onFoodCreated = { item ->
                    coroutineScope.launch {
                        // Edits update in place; inserting over an existing id would cascade-delete its logs
                        if (item.id == 0L) foodDao.insertFoodItem(item) else foodDao.updateFoodItem(item)
                    }
                },
                onMealUpdated = { mealId, name, items ->
                    coroutineScope.launch {
                        val existing = foodDao.getAllMeals().first().find { it.id == mealId } ?: return@launch
                        foodDao.updateMealContents(
                            existing.copy(name = name),
                            items.map { (f, q) -> MealFoodItem(mealId = mealId, foodItemId = f.id, quantity = q) }
                        )
                    }
                },
                onMealDeleted = { mealId -> coroutineScope.launch { foodDao.deleteMeal(mealId) } },
                getMealItems = { mealId ->
                    val foods = foodDao.getAllFoodItems().first().associateBy { it.id }
                    foodDao.getItemsForMeal(mealId).first().mapNotNull { row -> foods[row.foodItemId]?.let { it to row.quantity } }
                },
                onQuickLog = { item, target ->
                    coroutineScope.launch {
                        val fid = foodDao.insertFoodItem(item)
                        foodDao.insertFoodLog(DailyFoodLog(date = target.date, foodItemId = fid, quantity = 1.0, mealSlot = target.slot))
                    }
                },
                // Returns the new id so the food can be logged straight away
                onNewFoodItemCreated = { item -> foodDao.insertFoodItem(item) },
                onGetFoodByBarcode = { bc -> foodDao.getFoodItemByBarcode(bc) },
                onUpdateActiveWorkout = { tid, sets ->
                    coroutineScope.launch { activeWorkoutMutex.withLock {
                        // Read the profile fresh: this callback can be held by long-lived effects,
                        // and a stale snapshot would reset the workout start time on every save.
                        val currentProfile = weightDao.getUserProfile().first() ?: return@withLock
                        val startTime = currentProfile.activeWorkoutStartTime ?: System.currentTimeMillis()
                        
                        weightDao.saveProfile(currentProfile.copy(
                            activeWorkoutTemplateId = tid, 
                            hasActiveWorkout = true,
                            activeWorkoutStartTime = startTime
                        ))
                        
                        workoutDao.replaceActiveSets(sets.map { ps ->
                            ActiveWorkoutSet(
                                exerciseId = ps.exerciseId,
                                setNumber = ps.setNumber,
                                weightKg = ps.weightKg,
                                reps = ps.reps,
                                notes = ps.notes,
                                isCompleted = ps.isCompleted,
                                durationSeconds = ps.durationSeconds,
                                distanceKm = ps.distanceKm,
                                targetWeightKg = ps.targetWeightKg,
                                targetReps = ps.targetReps,
                                targetDurationSeconds = ps.targetDurationSeconds,
                                targetDistanceKm = ps.targetDistanceKm,
                                orderIndex = ps.orderIndex
                            )
                        })
                    } }
                },
                onClearActiveWorkout = {
                    coroutineScope.launch { activeWorkoutMutex.withLock {
                        val currentProfile = weightDao.getUserProfile().first() ?: return@withLock
                        weightDao.saveProfile(currentProfile.copy(
                            activeWorkoutTemplateId = null,
                            hasActiveWorkout = false,
                            activeWorkoutStartTime = null
                        ))
                        workoutDao.clearActiveSets()
                    } }
                },
                onCheckInCompleted = { w, bf, ph, review ->
                    coroutineScope.launch {
                        val p = weightDao.getUserProfile().first() ?: return@launch
                        val now = LocalDate.now().toString()
                        // Copy photos into app storage: camera shots live in cache and gallery links expire
                        val (front, back, side) = listOf("front", "back", "side").mapIndexed { i, label ->
                            ph.getOrNull(i)?.let { MediaUtils.persistProgressPhoto(context.applicationContext, it, label) }
                        }
                        weightDao.insertWeight(WeightEntry(date = now, weight = w, calorieTargetAtEntry = review.newTarget))
                        // Saves exactly the verdict the summary showed (computed with this check-in's weight)
                        weightDao.insertCheckIn(CheckIn(date = now, weight = w, frontPhotoPath = front, backPhotoPath = back, sidePhotoPath = side, calorieTargetBefore = p.currentCalorieTarget, calorieTargetAfter = review.newTarget, adjustmentReason = review.reason, bodyFatPercentage = bf))
                        weightDao.saveProfile(p.copy(
                            bodyFatPercentage = bf ?: p.bodyFatPercentage,
                            currentCalorieTarget = review.newTarget,
                            estimatedMaintenanceCalories = review.estimatedMaintenance,
                            pendingCalorieTarget = null, pendingAdjustmentReason = null,
                            checkInDue = false, lastCheckInDate = now
                        ))
                        NotificationHelper.cancelWeighInReminder(context.applicationContext)
                    }
                },
                getSetsForSession = { sid -> workoutDao.getSetsForSession(sid).first() },
                getExercisesForTemplate = { tid -> workoutDao.getExercisesForTemplateOnce(tid) },
                getSetsForTemplate = { tid -> workoutDao.getSetsForTemplate(tid) },
                onSessionSaved = { date, setsList, tid, exSid, updateRoutine ->
                    coroutineScope.launch {
                        // Placeholders are already resolved by ActiveSessionScreen; drop "no sets yet" markers
                        val historySets = setsList.filter { it.setNumber > 0 }

                        // 1. Save Session (always keeps its link to the routine it came from)
                        if (historySets.isNotEmpty()) {
                            val session = WorkoutSession(
                                id = exSid ?: 0,
                                date = date,
                                templateId = tid,
                                totalVolumeKg = historySets.sumOf { it.weightKg * it.reps },
                                exerciseCount = historySets.map { it.exerciseId }.distinct().size,
                                setCount = historySets.size
                            )
                            val sid = if (exSid != null) {
                                workoutDao.updateSession(session)
                                workoutDao.deleteSetsBySessionId(exSid)
                                exSid
                            } else {
                                workoutDao.insertSession(session)
                            }

                            // 2. Save Logged Sets (History)
                            historySets.forEach { ps ->
                                workoutDao.insertSet(LoggedSet(
                                    sessionId = sid,
                                    exerciseId = ps.exerciseId,
                                    setNumber = ps.setNumber,
                                    weightKg = ps.weightKg,
                                    reps = ps.reps,
                                    notes = ps.notes,
                                    durationSeconds = ps.durationSeconds,
                                    distanceKm = ps.distanceKm
                                ))
                            }
                        }

                        // 3. Update Routine: today's exercises, order and numbers become next time's targets
                        if (updateRoutine && tid != null && historySets.isNotEmpty()) {
                            val routine = historySets.groupBy { it.exerciseId }.entries.mapIndexed { idx, (eid, sets) ->
                                val first = sets.first()
                                TemplateExercise(
                                    templateId = tid,
                                    exerciseId = eid,
                                    targetSets = sets.size,
                                    targetReps = first.reps,
                                    targetWeightKg = first.weightKg,
                                    orderIndex = idx,
                                    targetDurationSeconds = first.durationSeconds,
                                    targetDistanceKm = first.distanceKm
                                ) to sets.map { rs ->
                                    TemplateSet(
                                        templateExerciseId = 0,
                                        setNumber = rs.setNumber,
                                        targetReps = rs.reps,
                                        targetWeightKg = rs.weightKg,
                                        targetDurationSeconds = rs.durationSeconds,
                                        targetDistanceKm = rs.distanceKm
                                    )
                                }
                            }
                            workoutDao.replaceTemplateContents(tid, routine)
                        }
                    }
                },
                onSessionDeleted = { sid -> coroutineScope.launch { workoutDao.deleteSession(sid) } },
                onCreateExercise = { n, m, t, oc -> coroutineScope.launch { val ex = Exercise(name = n, muscleGroup = m, exerciseType = t); val nid = workoutDao.insertExercise(ex); oc(ex.copy(id = nid)) } },
                onTemplateUpdated = { tid, n, nt, exs ->
                    coroutineScope.launch {
                        workoutDao.updateTemplate(WorkoutTemplate(id = tid, name = n, notes = nt))
                        val existing = workoutDao.getExercisesForTemplateOnce(tid)
                        val existingSets = workoutDao.getSetsForTemplate(tid).groupBy { it.templateExerciseId }
                        val contents = exs.mapIndexed { i, input ->
                            val old = existing.firstOrNull { it.exerciseId == input.exercise.id }
                            val oldSets = old?.let { existingSets[it.id] }.orEmpty()
                            // Exercises whose numbers weren't touched keep their per-set targets;
                            // edited ones get the dialog's uniform sets × reps × weight
                            val unchanged = oldSets.isNotEmpty() && oldSets.size == input.targetSets &&
                                oldSets.first().targetReps == input.targetReps && oldSets.first().targetWeightKg == input.targetWeightKg
                            val sets = if (unchanged) oldSets else List(input.targetSets) { sn ->
                                TemplateSet(templateExerciseId = 0, setNumber = sn + 1, targetReps = input.targetReps, targetWeightKg = input.targetWeightKg)
                            }
                            TemplateExercise(
                                templateId = tid, exerciseId = input.exercise.id,
                                targetSets = input.targetSets, targetReps = input.targetReps, targetWeightKg = input.targetWeightKg,
                                orderIndex = i,
                                targetDurationSeconds = old?.targetDurationSeconds, targetDistanceKm = old?.targetDistanceKm
                            ) to sets
                        }
                        workoutDao.replaceTemplateContents(tid, contents)
                    }
                },
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
                onOnboardingCompleted = { w, h, a, m, s, g, cc, us, bf, hc, onlineLookup ->
                    val main = algorithm.calculateInitialMaintenance(w, h, a, m, s, bf)
                    val init = cc ?: when (g) { Goal.CUT -> main - 500; Goal.BULK -> main + 300; Goal.MAINTAIN -> main; Goal.REVERSE -> main - 300 }
                    coroutineScope.launch {
                        weightDao.saveProfile(UserProfile(height = h, age = a, isMale = m, averageDailySteps = s, estimatedMaintenanceCalories = main, goal = g, currentCalorieTarget = init, unitSystem = us, bodyFatPercentage = bf, useHealthConnect = hc, useExternalApi = onlineLookup))
                        weightDao.insertWeight(WeightEntry(date = LocalDate.now().toString(), weight = w, calorieTargetAtEntry = init))
                    }
                },
                getExerciseHistory = { eid: Long -> workoutDao.getSetsForExercise(eid) }
            )
        }
    }
}
