package com.eliteonetube.momentum.logic.algorithm

import com.eliteonetube.momentum.data.DailyMealLog
import com.eliteonetube.momentum.data.FoodLogWithItem
import com.eliteonetube.momentum.data.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** What was actually eaten over the week before a check-in, from the days that were properly logged. */
data class WeekIntake(val averageCalories: Int, val daysLogged: Int)

/**
 * Reads the food diary for the weekly review, so the coach doesn't adjust a target that wasn't
 * being followed: eating well over target explains a stalled cut better than the target does.
 */
object IntakeCheck {
    /** A day below this share of the target is taken as partly logged, not as a day of eating very little. */
    const val MIN_DAY_SHARE_OF_TARGET = 0.5
    const val MIN_LOGGED_DAYS = 5
    /** Averages within this share of the target count as on plan; smaller gaps are logging noise. */
    const val OFF_PLAN_SHARE = 0.10

    /** Calories per day (ISO date) across individually logged foods and logged meals. */
    fun dailyCalories(foodLogs: List<FoodLogWithItem>, mealLogs: List<DailyMealLog>): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        foodLogs.forEach { totals[it.date] = (totals[it.date] ?: 0.0) + it.calories * it.quantity }
        mealLogs.forEach { totals[it.date] = (totals[it.date] ?: 0.0) + it.calories }
        return totals
    }

    /**
     * The average over the 7 full days before [today] (today's eating isn't finished), counting
     * only properly logged days. Null when fewer than [MIN_LOGGED_DAYS] were, so the review falls
     * back to weight alone.
     */
    fun weekIntake(dailyCalories: Map<String, Double>, calorieTarget: Int, today: LocalDate): WeekIntake? {
        val loggedDays = (1..7).mapNotNull { daysAgo -> dailyCalories[today.minusDays(daysAgo.toLong()).toString()] }
            .filter { it >= calorieTarget * MIN_DAY_SHARE_OF_TARGET }
        if (loggedDays.size < MIN_LOGGED_DAYS) return null
        return WeekIntake(loggedDays.average().roundToInt(), loggedDays.size)
    }

    /**
     * True when the target changed during the intake week, e.g. the goal was switched on Wednesday:
     * the week's eating can't be judged against one target then. Each weigh-in records the target
     * active that day.
     */
    fun targetChangedDuringWeek(entries: List<WeightEntry>, calorieTarget: Int, today: LocalDate): Boolean =
        entries.any { entry ->
            val daysAgo = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(entry.date), today) }.getOrDefault(-1)
            daysAgo in 1..7 && entry.calorieTargetAtEntry != null && entry.calorieTargetAtEntry != calorieTarget
        }

    fun ateOverTarget(intake: WeekIntake, calorieTarget: Int) = intake.averageCalories > calorieTarget * (1 + OFF_PLAN_SHARE)

    fun ateUnderTarget(intake: WeekIntake, calorieTarget: Int) = intake.averageCalories < calorieTarget * (1 - OFF_PLAN_SHARE)
}
