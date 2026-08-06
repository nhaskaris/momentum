package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.LoggedSet
import com.eliteonetube.momentum.data.TemplateExercise
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.data.WorkoutSession
import com.eliteonetube.momentum.data.WorkoutTemplate
import com.eliteonetube.momentum.ui.theme.workout.TemplateExerciseInput
import com.eliteonetube.momentum.ui.workout.PendingSet
import com.eliteonetube.momentum.ui.workout.WorkoutsScreen
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
    getSetsForSession: suspend (Long) -> List<LoggedSet>,
    getExercisesForTemplate: suspend (Long) -> List<TemplateExercise> = { emptyList() },
    onSessionSaved: (date: String, sets: List<PendingSet>, templateId: Long?) -> Unit,
    onSessionDeleted: (Long) -> Unit,
    onTemplateCreated: (String, String?, List<TemplateExerciseInput>) -> Unit = { _, _, _ -> },
    onTemplateDeleted: (Long) -> Unit = {},
    onCreateExercise: (String, String, (Exercise) -> Unit) -> Unit = { _, _, _ -> },
    onOnboardingCompleted: (Double, Double, Int, Boolean, Int, Goal, Int?, UnitSystem) -> Unit
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

    var showHistoryModal by remember { mutableStateOf(false) }

    // State to track if user is inside an active workout session
    var isSessionActive by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { AppTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    val currentTab = AppTab.entries[pagerState.currentPage]

    Scaffold(
        bottomBar = {
            // Hide bottom navigation bar completely during an active session
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
            // Disable swipe navigation between tabs when a workout is active
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
                    onAdjustmentDismissed = onAdjustmentDismissed
                )
                AppTab.WORKOUTS -> WorkoutsScreen(
                    recentSessions = recentSessions,
                    allExercises = allExercises,
                    allTemplates = allTemplates,
                    unitSystem = savedProfile.unitSystem,
                    getSetsForSession = getSetsForSession,
                    getExercisesForTemplate = getExercisesForTemplate,
                    onSessionSaved = { date, sets, templateId ->
                        isSessionActive = false
                        onSessionSaved(date, sets, templateId)
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
                    onWeightClick = { showHistoryModal = true },
                    onProfileUpdated = onProfileUpdated,
                    onGoalChanged = onGoalChanged
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