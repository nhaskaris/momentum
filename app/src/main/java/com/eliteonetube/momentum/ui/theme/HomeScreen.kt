package com.eliteonetube.momentum.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.theme.nutrition.FoodScannerScreen
import com.eliteonetube.momentum.ui.theme.nutrition.FoodReviewDialog
import com.eliteonetube.momentum.ui.theme.nutrition.LogQuantityDialog
import com.eliteonetube.momentum.logic.ScannedNutrition
import com.eliteonetube.momentum.logic.ExternalFoodApi
import com.eliteonetube.momentum.logic.HelperAssistant
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
import com.eliteonetube.momentum.ui.workout.PendingSet
import com.eliteonetube.momentum.ui.workout.WorkoutsScreen
import com.eliteonetube.momentum.ui.statistics.StatisticsScreen
import com.eliteonetube.momentum.ui.theme.onboarding.IntroScreen
import com.eliteonetube.momentum.ui.theme.onboarding.OnboardingScreen
import com.eliteonetube.momentum.ui.theme.dashboard.MascotMood
import com.eliteonetube.momentum.ui.theme.dashboard.MomentumMascot
import com.eliteonetube.momentum.ui.theme.MomentumDark
import com.eliteonetube.momentum.ui.theme.bounceClick
import com.eliteonetube.momentum.logic.HealthConnectManager
import com.eliteonetube.momentum.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HomeScreen(
    currentCalorieTarget: Int,
    savedProfile: UserProfile?,
    recentWeights: List<WeightEntry>,
    allWeights: List<WeightEntry> = emptyList(),
    recentSessions: List<WorkoutSession>,
    recentNutrition: List<DailyNutrition> = emptyList(),
    allExercises: List<Exercise>,
    allTemplates: List<WorkoutTemplate> = emptyList(),
    allCheckIns: List<CheckIn> = emptyList(),
    allMeals: List<Meal> = emptyList(),
    todayFoodLogs: List<FoodLogWithItem> = emptyList(),
    allFoodItems: List<FoodItem> = emptyList(),
    activeSets: List<ActiveWorkoutSet> = emptyList(),
    hasActiveWorkout: Boolean = false,
    openWorkoutRequest: Int = 0,
    openWeightEntryRequest: Int = 0,
    currentStreak: Int,
    totalDaysLogged: Int,
    loggedDates: Set<LocalDate>,
    onWeightSubmitted: (Double) -> Unit,
    onPastWeightSubmitted: (date: String, weight: Double) -> Unit,
    onWeightDeleted: (String) -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onGoalChanged: (Goal) -> Unit,
    onAdjustmentAccepted: () -> Unit,
    onAdjustmentDismissed: () -> Unit,
    onFoodLogged: (Long, Double) -> Unit,
    onFoodLogDeleted: (Long, Boolean) -> Unit,
    onFoodLogUpdated: (Long, Long, Double) -> Unit,
    onLogMeal: (Long) -> Unit,
    onMealCreated: (String, List<Pair<FoodItem, Double>>) -> Unit,
    onFoodCreated: (FoodItem) -> Unit,
    onQuickLog: (FoodItem) -> Unit,
    onNewFoodItemCreated: (FoodItem) -> Unit,
    onUpdateActiveWorkout: (Long?, List<PendingSet>) -> Unit,
    onClearActiveWorkout: () -> Unit,
    onGetFoodByBarcode: suspend (String) -> FoodItem?,
    getExerciseHistory: suspend (Long) -> List<LoggedSet>,
    onCheckInCompleted: (Double, List<android.net.Uri?>) -> Unit,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    getExercisesForTemplate: suspend (Long) -> List<TemplateExercise> = { emptyList() },
    getSetsForTemplateExercise: suspend (Long) -> List<TemplateSet> = { emptyList() },
    onSessionSaved: (date: String, sets: List<PendingSet>, templateId: Long?, sessionId: Long?) -> Unit,
    onSessionDeleted: (Long) -> Unit,
    onTemplateCreated: (String, String?, List<TemplateExerciseInput>) -> Unit = { _, _, _ -> },
    onTemplateDeleted: (Long) -> Unit = {},
    onCreateExercise: (String, String, ExerciseType, (Exercise) -> Unit) -> Unit = { _, _, _, _ -> },
    onOnboardingCompleted: (Double, Double, Int, Boolean, Int, Goal, Int?, UnitSystem, Double?, Boolean) -> Unit
) {
    val context = LocalContext.current
    if (savedProfile == null) {
        var showIntro by remember { mutableStateOf(true) }
        if (showIntro) {
            IntroScreen(onFinished = { showIntro = false })
        } else {
            OnboardingScreen(onOnboardingCompleted)
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { AppTab.entries.size })

    var hasAutoSwitched by remember { mutableStateOf(false) }
    LaunchedEffect(activeSets.isNotEmpty(), hasActiveWorkout) {
        if ((activeSets.isNotEmpty() || hasActiveWorkout) && !hasAutoSwitched) {
            pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.WORKOUTS))
            hasAutoSwitched = true
        }
    }

    LaunchedEffect(openWorkoutRequest) {
        if (openWorkoutRequest > 0) {
            pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.WORKOUTS))
        }
    }

    LaunchedEffect(openWeightEntryRequest) {
        if (openWeightEntryRequest > 0) {
            pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.DASHBOARD))
        }
    }
    
    var showCheckIn by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf<ScannedNutrition?>(null) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }
    var itemToLogAfterScan by remember { mutableStateOf<FoodItem?>(null) }
    var logToEdit by remember { mutableStateOf<FoodLogWithItem?>(null) }

    var mascotMood by remember(savedProfile.checkInDue) {
        mutableStateOf(if (savedProfile.checkInDue) MascotMood.ALERT else MascotMood.IDLE)
    }
    var helperMessage by remember { mutableStateOf<String?>(null) }
    var isAskMode by remember { mutableStateOf(false) }
    var userQuestion by remember { mutableStateOf("") }

    LaunchedEffect(helperMessage, isAskMode) {
        if (helperMessage != null && !isAskMode) {
            delay(6000)
            helperMessage = null
        }
    }
    
    val currentTab = AppTab.entries[pagerState.currentPage]
    LaunchedEffect(currentTab, hasActiveWorkout, savedProfile.checkInDue) {
        mascotMood = when {
            hasActiveWorkout && currentTab == AppTab.WORKOUTS -> MascotMood.HAPPY
            savedProfile.checkInDue -> MascotMood.ALERT
            else -> MascotMood.IDLE
        }
    }

    var isSessionActive by remember { mutableStateOf(activeSets.isNotEmpty() || hasActiveWorkout) }
    LaunchedEffect(activeSets.isNotEmpty(), hasActiveWorkout) {
        if (activeSets.isNotEmpty() || hasActiveWorkout) {
            isSessionActive = true
        }
    }

    val healthConnectManager = remember { HealthConnectManager(context) }
    LaunchedEffect(savedProfile.useHealthConnect) {
        if (savedProfile.useHealthConnect) {
            val avgSteps = healthConnectManager.fetchAverageStepsLast7Days()
            if (avgSteps != null && avgSteps != savedProfile.averageDailySteps) {
                onProfileUpdated(savedProfile.copy(averageDailySteps = avgSteps))
            }
        }
    }

    if (showCheckIn) {
        CheckInScreen(
            profile = savedProfile,
            recentWeights = recentWeights,
            onComplete = { weight, photos ->
                showCheckIn = false
                onCheckInCompleted(weight, photos)
            },
            onCancel = { showCheckIn = false }
        )
        return
    }

    if (showGallery) {
        ProgressGalleryScreen(
            checkIns = allCheckIns,
            onBack = { showGallery = false }
        )
        return
    }

    if (showScanner) {
        FoodScannerScreen(
            apiEnabled = savedProfile.useExternalApi,
            onResult = { result, barcode ->
                scannedResult = result
                pendingBarcode = barcode
                showScanner = false
            },
            onBarcodeScanned = { barcode ->
                val localItem = onGetFoodByBarcode(barcode)
                if (localItem != null) {
                    itemToLogAfterScan = localItem
                    showScanner = false
                    pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.NUTRITION))
                } else if (savedProfile.useExternalApi) {
                    val externalItem = ExternalFoodApi.fetchByBarcode(barcode)
                    if (externalItem != null) {
                        scannedResult = ScannedNutrition(
                            name = externalItem.name,
                            calories = externalItem.calories,
                            protein = externalItem.protein,
                            fat = externalItem.fat,
                            carbs = externalItem.carbs
                        )
                        pendingBarcode = barcode
                        showScanner = false
                        pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.NUTRITION))
                    }
                }
            },
            onBack = { showScanner = false }
        )
        return
    }

    scannedResult?.let { result ->
        FoodReviewDialog(
            scanned = result,
            barcode = pendingBarcode,
            onDismiss = { scannedResult = null },
            onSave = { item ->
                onNewFoodItemCreated(item)
                scannedResult = null
                pendingBarcode = null
                itemToLogAfterScan = item
                coroutineScope.launch {
                    pagerState.scrollToPage(AppTab.entries.indexOf(AppTab.NUTRITION))
                }
            }
        )
    }

    itemToLogAfterScan?.let { item ->
        LogQuantityDialog(
            foodItem = item,
            onDismiss = { itemToLogAfterScan = null },
            onConfirm = { multiplier ->
                coroutineScope.launch {
                    val finalId = if (item.id == 0L) {
                        allFoodItems.find { it.name == item.name && it.barcode == item.barcode }?.id ?: 0L
                    } else {
                        item.id
                    }
                    onFoodLogged(finalId, multiplier)
                    itemToLogAfterScan = null
                }
            }
        )
    }

    logToEdit?.let { log ->
        LogQuantityDialog(
            foodItem = FoodItem(
                id = log.foodItemId, 
                name = log.name, 
                calories = log.calories, 
                protein = log.protein, 
                fat = log.fat, 
                carbs = log.carbs,
                servingAmount = log.servingAmount,
                servingUnit = log.servingUnit
            ),
            initialQuantity = log.quantity,
            onDismiss = { logToEdit = null },
            onConfirm = { multiplier ->
                onFoodLogUpdated(log.id, log.foodItemId, multiplier)
                logToEdit = null
            }
        )
    }

    var showHistoryModal by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !(isSessionActive && currentTab == AppTab.WORKOUTS),
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (AppTab.entries[page]) {
                AppTab.DASHBOARD -> MainDashboard(
                    calorieTarget = currentCalorieTarget,
                    recentWeights = recentWeights,
                    profile = savedProfile,
                    currentStreak = currentStreak,
                    totalDaysLogged = totalDaysLogged,
                    loggedDates = loggedDates,
                    onWeightSubmitted = { enteredWeight ->
                        coroutineScope.launch {
                            onWeightSubmitted(enteredWeight)
                            mascotMood = MascotMood.HAPPY
                            helperMessage = "Logged! Great job keeping the momentum."
                            delay(3000)
                            mascotMood = if (savedProfile.checkInDue) MascotMood.ALERT else MascotMood.IDLE
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onAdjustmentAccepted = {
                        coroutineScope.launch {
                            onAdjustmentAccepted()
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onAdjustmentDismissed = onAdjustmentDismissed,
                    onStartCheckIn = { showCheckIn = true }
                )
                AppTab.STATISTICS -> StatisticsScreen(
                    weights = allWeights,
                    recentSessions = recentSessions,
                    recentNutrition = recentNutrition,
                    unitSystem = savedProfile.unitSystem
                )
                AppTab.NUTRITION -> NutritionScreen(
                    calorieTarget = currentCalorieTarget,
                    profile = savedProfile,
                    recentWeights = recentWeights,
                    todayLogs = todayFoodLogs,
                    allFoodItems = allFoodItems,
                    allMeals = allMeals,
                    onLogFood = { id, qty ->
                        coroutineScope.launch {
                            onFoodLogged(id, qty)
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onLogMeal = { mealId ->
                        coroutineScope.launch {
                            onLogMeal(mealId)
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onDeleteLog = { id, isMeal ->
                        coroutineScope.launch {
                            onFoodLogDeleted(id, isMeal)
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onEditLog = { log -> logToEdit = log },
                    onQuickLog = { item ->
                        coroutineScope.launch {
                            onQuickLog(item)
                            WidgetUpdater.refresh(context)
                        }
                    },
                    onMealCreated = onMealCreated,
                    onFoodCreated = onFoodCreated,
                    onStartScan = { showScanner = true }
                )
                AppTab.WORKOUTS -> WorkoutsScreen(
                    recentSessions = recentSessions,
                    allExercises = allExercises,
                    allTemplates = allTemplates,
                    activeSets = activeSets,
                    hasActiveWorkout = hasActiveWorkout,
                    activeTemplateId = savedProfile.activeWorkoutTemplateId,
                    activeWorkoutStartTime = savedProfile.activeWorkoutStartTime,
                    unitSystem = savedProfile.unitSystem,
                    getSetsForSession = getSetsForSession,
                    getExerciseHistory = getExerciseHistory,
                    getExercisesForTemplate = getExercisesForTemplate,
                    getSetsForTemplateExercise = getSetsForTemplateExercise,
                    onSessionSaved = { date, sets, tid, sid ->
                        onClearActiveWorkout()
                        isSessionActive = false
                        onSessionSaved(date, sets, tid, sid)
                    },
                    onSessionDeleted = onSessionDeleted,
                    onTemplateCreated = onTemplateCreated,
                    onTemplateDeleted = onTemplateDeleted,
                    onSessionStateChanged = { isSessionActive = it },
                    onUpdateActiveWorkout = onUpdateActiveWorkout,
                    onClearActiveWorkout = onClearActiveWorkout,
                    onCreateExercise = onCreateExercise
                )
                AppTab.PROFILE -> ProfileScreen(
                    profile = savedProfile,
                    recentWeights = recentWeights,
                    allCheckIns = allCheckIns,
                    onWeightClick = { showHistoryModal = true },
                    onProfileUpdated = onProfileUpdated,
                    onGoalChanged = onGoalChanged,
                    onViewGallery = { showGallery = true },
                    onWeightsImported = { entries ->
                        coroutineScope.launch {
                            entries.forEach { onPastWeightSubmitted(it.date, it.weight) }
                        }
                    }
                )
            }
        }

        val isOnWorkoutsTab = currentTab == AppTab.WORKOUTS
        val shouldShowNav = !isSessionActive || !isOnWorkoutsTab
        
        if (shouldShowNav) {
            MascotAndNavigation(
                currentTab = currentTab,
                mascotMood = mascotMood,
                helperMessage = helperMessage,
                isAskMode = isAskMode,
                userQuestion = userQuestion,
                onTabSelected = { tab ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(AppTab.entries.indexOf(tab))
                    }
                },
                onHelperDismiss = { helperMessage = null; isAskMode = false },
                onAskModeToggle = { isAskMode = true },
                onUserQuestionChange = { userQuestion = it },
                onSendQuestion = { 
                    helperMessage = HelperAssistant.ask(userQuestion)
                    userQuestion = ""
                    isAskMode = false
                },
                onMascotClick = {
                    if (helperMessage != null || isAskMode) {
                        helperMessage = null
                        isAskMode = false
                    } else {
                        helperMessage = getMascotMessage(currentTab, currentStreak, recentWeights, todayFoodLogs, hasActiveWorkout)
                    }
                }
            )
        }
    }

    if (showHistoryModal) {
        WeightHistoryBottomSheet(
            entries = recentWeights,
            unitSystem = savedProfile.unitSystem,
            onDismiss = { showHistoryModal = false },
            onPastWeightSubmitted = onPastWeightSubmitted,
            onWeightDeleted = onWeightDeleted
        )
    }
}

@Composable
private fun MascotAndNavigation(
    currentTab: AppTab,
    mascotMood: MascotMood,
    helperMessage: String?,
    isAskMode: Boolean,
    userQuestion: String,
    onTabSelected: (AppTab) -> Unit,
    onHelperDismiss: () -> Unit,
    onAskModeToggle: () -> Unit,
    onUserQuestionChange: (String) -> Unit,
    onSendQuestion: () -> Unit,
    onMascotClick: () -> Unit
) {
    val isOnStatsTab = currentTab == AppTab.STATISTICS

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isOnStatsTab) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    AnimatedVisibility(
                        visible = helperMessage != null || isAskMode,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End) + scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End) + scaleOut(transformOrigin = TransformOrigin(1f, 0.5f))
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp),
                            shadowElevation = 8.dp,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.padding(end = 12.dp).widthIn(max = 240.dp)
                        ) {
                            AnimatedContent(targetState = isAskMode, label = "AskMode") { askMode ->
                                if (askMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                        OutlinedTextField(
                                            value = userQuestion,
                                            onValueChange = onUserQuestionChange,
                                            placeholder = { Text("Ask me...", style = MaterialTheme.typography.bodySmall) },
                                            modifier = Modifier.weight(1f),
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.primary)
                                        )
                                        IconButton(onClick = onSendQuestion, enabled = userQuestion.isNotBlank()) {
                                            Icon(Icons.Default.Send, null, tint = if (userQuestion.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onHelperDismiss() }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(text = helperMessage ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                        IconButton(onClick = onAskModeToggle, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.size(56.dp).bounceClick(onMascotClick)) {
                        MomentumMascot(mood = mascotMood, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            BottomNavBar(currentTab = currentTab, onTabSelected = onTabSelected)
        }
    }
}

private fun getMascotMessage(
    currentTab: AppTab,
    currentStreak: Int,
    recentWeights: List<WeightEntry>,
    todayFoodLogs: List<FoodLogWithItem>,
    hasActiveWorkout: Boolean
): String {
    return when (currentTab) {
        AppTab.DASHBOARD -> if (currentStreak > 0) "You've got a $currentStreak day streak! Keep it going!" else "Let's log your weight to start a new streak!"
        AppTab.STATISTICS -> {
            val recent = recentWeights.take(2)
            if (recent.size >= 2) {
                val change = recent[0].weight - recent[1].weight
                if (change < 0) "Weight is trending down—I'm watching the curve!"
                else if (change > 0) "Weight shifted up slightly, likely water—tracking the average."
                else "Holding steady! Perfect for maintenance."
            } else "Log more weights so I can analyze your metabolic trends!"
        }
        AppTab.NUTRITION -> {
            val protein = todayFoodLogs.sumOf { it.protein * it.quantity }
            if (protein < 50) "Make sure you're hitting your protein today!" else "Great job on the protein intake! Your muscles will thank me."
        }
        AppTab.WORKOUTS -> if (hasActiveWorkout) "You're in the zone! Focus on every rep." else "Ready to Train? Pick a routine and let's get it."
        AppTab.PROFILE -> "Everything is private. Your data never leaves this phone!"
    }
}
