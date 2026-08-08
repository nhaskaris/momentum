package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.theme.nutrition.FoodScannerScreen
import com.eliteonetube.momentum.ui.theme.nutrition.FoodReviewDialog
import com.eliteonetube.momentum.ui.theme.nutrition.LogQuantityDialog
import com.eliteonetube.momentum.logic.ScannedNutrition
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
import com.eliteonetube.momentum.ui.workout.PendingSet
import com.eliteonetube.momentum.ui.workout.WorkoutsScreen
import com.eliteonetube.momentum.ui.theme.onboarding.IntroScreen
import com.eliteonetube.momentum.ui.theme.onboarding.OnboardingScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HomeScreen(
    currentCalorieTarget: Int,
    savedProfile: UserProfile?,
    recentWeights: List<WeightEntry>,
    recentSessions: List<WorkoutSession>,
    allExercises: List<Exercise>,
    allTemplates: List<WorkoutTemplate> = emptyList(),
    allCheckIns: List<CheckIn> = emptyList(),
    todayFoodLogs: List<FoodLogWithItem> = emptyList(),
    allFoodItems: List<FoodItem> = emptyList(),
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
    onFoodLogDeleted: (Long) -> Unit,
    onFoodLogUpdated: (Long, Long, Double) -> Unit,
    onNewFoodItemCreated: (FoodItem) -> Unit,
    onGetFoodByBarcode: suspend (String) -> FoodItem?,
    onCheckInCompleted: (Double, List<android.net.Uri?>) -> Unit,
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    getExercisesForTemplate: suspend (Long) -> List<TemplateExercise> = { emptyList() },
    onSessionSaved: (date: String, sets: List<PendingSet>, templateId: Long?, sessionId: Long?) -> Unit,
    onSessionDeleted: (Long) -> Unit,
    onTemplateCreated: (String, String?, List<TemplateExerciseInput>) -> Unit = { _, _, _ -> },
    onTemplateDeleted: (Long) -> Unit = {},
    onCreateExercise: (String, String, (Exercise) -> Unit) -> Unit = { _, _, _ -> },
    onOnboardingCompleted: (Double, Double, Int, Boolean, Int, Goal, Int?, UnitSystem, Double?, Boolean) -> Unit
) {
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
    
    var showCheckIn by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf<ScannedNutrition?>(null) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }
    var itemToLogAfterScan by remember { mutableStateOf<FoodItem?>(null) }
    var logToEdit by remember { mutableStateOf<FoodLogWithItem?>(null) }

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
            onResult = { result, barcode ->
                scannedResult = result
                pendingBarcode = barcode
                showScanner = false
            },
            onBarcodeScanned = { barcode ->
                coroutineScope.launch {
                    val item = onGetFoodByBarcode(barcode)
                    if (item != null) {
                        itemToLogAfterScan = item
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
                    
                    if (finalId != 0L) {
                        onFoodLogged(finalId, multiplier)
                        itemToLogAfterScan = null
                    } else {
                        onFoodLogged(item.id, multiplier)
                        itemToLogAfterScan = null
                    }
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
                carbs = log.carbs
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

    var isSessionActive by remember { mutableStateOf(false) }

    val currentTab = AppTab.entries[pagerState.currentPage]

    Scaffold(
        bottomBar = {
            if (!isSessionActive) {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(AppTab.entries.indexOf(tab))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSessionActive,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) { page ->
            when (AppTab.entries[page]) {
                AppTab.DASHBOARD -> MainDashboard(
                    calorieTarget = currentCalorieTarget,
                    recentWeights = recentWeights,
                    profile = savedProfile,
                    currentStreak = currentStreak,
                    totalDaysLogged = totalDaysLogged,
                    loggedDates = loggedDates,
                    onWeightSubmitted = onWeightSubmitted,
                    onAdjustmentAccepted = onAdjustmentAccepted,
                    onAdjustmentDismissed = onAdjustmentDismissed,
                    onStartCheckIn = { showCheckIn = true }
                )
                AppTab.NUTRITION -> NutritionScreen(
                    calorieTarget = currentCalorieTarget,
                    profile = savedProfile,
                    recentWeights = recentWeights,
                    todayLogs = todayFoodLogs,
                    allFoodItems = allFoodItems,
                    onLogFood = onFoodLogged,
                    onDeleteLog = onFoodLogDeleted,
                    onEditLog = { logToEdit = it },
                    onStartScan = { showScanner = true }
                )
                AppTab.WORKOUTS -> WorkoutsScreen(
                    recentSessions = recentSessions,
                    allExercises = allExercises,
                    allTemplates = allTemplates,
                    unitSystem = savedProfile.unitSystem,
                    getSetsForSession = getSetsForSession,
                    getExercisesForTemplate = getExercisesForTemplate,
                    onSessionSaved = { date: String, sets: List<PendingSet>, templateId: Long?, sessionId: Long? ->
                        isSessionActive = false
                        onSessionSaved(date, sets, templateId, sessionId)
                    },
                    onSessionDeleted = onSessionDeleted,
                    onTemplateCreated = onTemplateCreated,
                    onTemplateDeleted = onTemplateDeleted,
                    onSessionStateChanged = { active ->
                        isSessionActive = active
                    },
                    onCreateExercise = onCreateExercise
                )
                AppTab.PROFILE -> ProfileScreen(
                    profile = savedProfile,
                    recentWeights = recentWeights,
                    allCheckIns = allCheckIns,
                    onWeightClick = { showHistoryModal = true },
                    onProfileUpdated = onProfileUpdated,
                    onGoalChanged = onGoalChanged,
                    onViewGallery = { showGallery = true }
                )
            }
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
