package com.eliteonetube.momentum.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.logic.algorithm.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * The app's database-backed data, collected once and read lazily.
 *
 * Each property reads its State only when accessed, so a database change recomposes just the
 * composables that read that property (e.g. logging food redraws the Eat tab and the Dashboard
 * ring, not Train or Profile). Read these where they're used — copying one into a local at the
 * top of a screen would make that whole screen recompose on every change again.
 */
class AppData(
    private val recentWeightsState: State<List<WeightEntry>>,
    private val allWeightsState: State<List<WeightEntry>>,
    private val allWeightDatesState: State<List<String>>,
    private val allCheckInsState: State<List<CheckIn>>,
    private val recentSessionsState: State<List<WorkoutSession>>,
    private val templateSummariesState: State<List<TemplateSummary>>,
    private val allExercisesState: State<List<Exercise>>,
    private val allTemplatesState: State<List<WorkoutTemplate>>,
    private val activeSetsState: State<List<ActiveWorkoutSet>>,
    private val workoutStatsState: State<WorkoutStats?>,
    private val recentNutritionState: State<List<DailyNutrition>>,
    private val todayFoodLogsState: State<List<FoodLogWithItem>>,
    private val allFoodItemsState: State<List<FoodItem>>,
    private val recentFoodIdsState: State<List<Long>>,
    private val allMealsState: State<List<Meal>>
) {
    val recentWeights: List<WeightEntry> get() = recentWeightsState.value
    val allWeights: List<WeightEntry> get() = allWeightsState.value
    val allCheckIns: List<CheckIn> get() = allCheckInsState.value
    val recentSessions: List<WorkoutSession> get() = recentSessionsState.value
    val templateSummaries: List<TemplateSummary> get() = templateSummariesState.value
    val allExercises: List<Exercise> get() = allExercisesState.value
    val allTemplates: List<WorkoutTemplate> get() = allTemplatesState.value
    val activeSets: List<ActiveWorkoutSet> get() = activeSetsState.value
    val workoutStats: WorkoutStats? get() = workoutStatsState.value
    val recentNutrition: List<DailyNutrition> get() = recentNutritionState.value
    val todayFoodLogs: List<FoodLogWithItem> get() = todayFoodLogsState.value
    val allFoodItems: List<FoodItem> get() = allFoodItemsState.value
    val recentFoodIds: List<Long> get() = recentFoodIdsState.value
    val allMeals: List<Meal> get() = allMealsState.value

    // Derived once per change of the weight dates, not on every read
    private val currentStreakState = derivedStateOf { StreakCalculator.currentStreak(allWeightDatesState.value) }
    private val loggedDatesState = derivedStateOf {
        allWeightDatesState.value.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    }
    val currentStreak: Int get() = currentStreakState.value
    val loggedDates: Set<LocalDate> get() = loggedDatesState.value
    val totalDaysLogged: Int get() = allWeightDatesState.value.size
}

/**
 * Collects every app-wide query once. Flows are remembered: DAO calls return a new Flow each
 * time, and collecting a new Flow restarts it, so un-remembered calls re-ran every query on each
 * recomposition.
 */
@Composable
fun rememberAppData(weightDao: WeightDao, workoutDao: WorkoutDao, foodDao: FoodDao, currentDate: LocalDate): AppData {
    val today = currentDate.toString()
    val thirtyDaysAgo = currentDate.minusDays(30).toString()

    val recentNutritionFlow = remember(thirtyDaysAgo) {
        combine(foodDao.getRecentFoodLogs(thirtyDaysAgo), foodDao.getRecentMealLogs(thirtyDaysAgo)) { items, meals ->
            val allLogs = items + meals.map { it.asLogItem() }
            allLogs.groupBy { it.date }.map { (date, logs) ->
                DailyNutrition(
                    date = date,
                    totalCalories = logs.sumOf { it.calories * it.quantity },
                    totalProtein = logs.sumOf { it.protein * it.quantity },
                    totalCarbs = logs.sumOf { it.carbs * it.quantity },
                    totalFat = logs.sumOf { it.fat * it.quantity }
                )
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)
    }
    // All-time workout totals and records for Analytics (recentSessions is capped at 30)
    val workoutStatsFlow = remember(thirtyDaysAgo) {
        combine(
            workoutDao.getWorkoutTotals(thirtyDaysAgo),
            workoutDao.getHeaviestSession(),
            workoutDao.getMostVariedSession()
        ) { totals, heaviest, mostVaried -> WorkoutStats(totals, heaviest, mostVaried) }
    }
    val todayFoodLogsFlow = remember(today) {
        combine(foodDao.getFoodLogsForDate(today), foodDao.getDailyMealLogsForDate(today)) { items, meals ->
            items + meals.map { it.asLogItem() }
        }.flowOn(Dispatchers.Default)
    }

    val recentWeights = remember { weightDao.getLastTwoWeeks() }.collectAsState(initial = emptyList())
    val allWeights = remember { weightDao.getAllWeights() }.collectAsState(initial = emptyList())
    val allWeightDates = remember { weightDao.getAllWeightDates() }.collectAsState(initial = emptyList())
    val allCheckIns = remember { weightDao.getAllCheckIns() }.collectAsState(initial = emptyList())
    val recentSessions = remember { workoutDao.getRecentSessions() }.collectAsState(initial = emptyList())
    val templateSummaries = remember { workoutDao.getTemplateSummaries() }.collectAsState(initial = emptyList())
    val allExercises = remember { workoutDao.getAllExercises() }.collectAsState(initial = emptyList())
    val allTemplates = remember { workoutDao.getAllTemplates() }.collectAsState(initial = emptyList())
    val activeSets = remember { workoutDao.getActiveSets().distinctUntilChanged() }.collectAsState(initial = emptyList())
    val workoutStats = workoutStatsFlow.collectAsState(initial = null)
    val recentNutrition = recentNutritionFlow.collectAsState(initial = emptyList())
    val todayFoodLogs = todayFoodLogsFlow.collectAsState(initial = emptyList())
    val allFoodItems = remember { foodDao.getAllFoodItems() }.collectAsState(initial = emptyList())
    val recentFoodIds = remember { foodDao.getRecentFoodIds() }.collectAsState(initial = emptyList())
    val allMeals = remember { foodDao.getAllMeals() }.collectAsState(initial = emptyList())

    // The State objects above keep their identity across recompositions, so one holder suffices
    return remember {
        AppData(
            recentWeights, allWeights, allWeightDates, allCheckIns,
            recentSessions, templateSummaries, allExercises, allTemplates, activeSets, workoutStats,
            recentNutrition, todayFoodLogs, allFoodItems, recentFoodIds, allMeals
        )
    }
}

/** A logged meal shown alongside individual foods in diary lists and totals. */
private fun DailyMealLog.asLogItem() = FoodLogWithItem(
    id = id,
    date = date,
    foodItemId = mealId,
    quantity = 1.0,
    name = name,
    calories = calories,
    protein = protein,
    fat = fat,
    carbs = carbs,
    isMeal = true
)
