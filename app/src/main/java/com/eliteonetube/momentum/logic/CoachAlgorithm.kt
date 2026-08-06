package com.eliteonetube.momentum.logic

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class WeeklyAdjustment(
    val newTarget: Int,
    val deltaCalories: Int,
    val weekChangeKg: Double,
    val reason: String
)

class CoachAlgorithm {

    data class GoalTransition(val newTarget: Int, val explanation: String)

    fun calculateInitialMaintenance(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        isMale: Boolean,
        averageDailySteps: Int,
        bodyFatPercentage: Double? = null
    ): Int {
        val bmr = if (bodyFatPercentage != null && bodyFatPercentage > 0) {
            // Katch-McArdle Formula
            val lbm = weightKg * (100 - bodyFatPercentage) / 100.0
            370 + (21.6 * lbm)
        } else {
            // Mifflin-St Jeor Equation
            val genderBonus = if (isMale) 5 else -161
            (10 * weightKg) + (6.25 * heightCm) - (5 * age) + genderBonus
        }

        val sedentaryMaintenance = bmr * 1.2
        val stepCaloriesBurned = (averageDailySteps / 1000.0) * 35.0
        return (sedentaryMaintenance + stepCaloriesBurned).roundToInt()
    }

    fun calculateGoalTransition(
        previousGoal: Goal,
        newGoal: Goal,
        currentCalorieTarget: Int,
        estimatedMaintenance: Int
    ): GoalTransition {
        if (previousGoal == newGoal) {
            return GoalTransition(currentCalorieTarget, "You're already on this goal — no change.")
        }

        return when (newGoal) {
            Goal.MAINTAIN -> GoalTransition(
                estimatedMaintenance,
                "Calories reset to your estimated maintenance of $estimatedMaintenance kcal."
            )
            Goal.REVERSE -> GoalTransition(
                currentCalorieTarget,
                "You'll keep eating $currentCalorieTarget kcal for now — Momentum will gradually raise it back toward your estimated maintenance of $estimatedMaintenance kcal."
            )
            Goal.CUT -> {
                val target = (currentCalorieTarget - 250).coerceAtMost(estimatedMaintenance - 100)
                GoalTransition(target, "Starting at $target kcal — a deficit from where you are now.")
            }
            Goal.BULK -> {
                val target = currentCalorieTarget + 200
                GoalTransition(target, "Starting at $target kcal — a surplus from where you are now.")
            }
        }
    }

    /**
     * Weekly check: compares the last 7 days against the 7 before that and
     * decides whether the calorie target needs adjusting for the goal.
     * Returns null if there isn't enough data yet (fewer than 3 entries per week).
     */
    fun calculateWeeklyAdjustment(entries: List<WeightEntry>, currentCalories: Int, goal: Goal, estimatedMaintenance: Int): WeeklyAdjustment? {
        val today = LocalDate.now()
        val week1Weights = mutableListOf<Double>()
        val week2Weights = mutableListOf<Double>()

        for (entry in entries) {
            val entryDate = try {
                LocalDate.parse(entry.date)
            } catch (e: Exception) {
                continue
            }
            val daysAgo = ChronoUnit.DAYS.between(entryDate, today)
            if (daysAgo in 0..6) week2Weights.add(entry.weight)
            else if (daysAgo in 7..14) week1Weights.add(entry.weight)
        }

        if (week1Weights.size < 3 || week2Weights.size < 3) return null

        val week1Average = week1Weights.average()
        val week2Average = week2Weights.average()
        val weightChange = week2Average - week1Average

        val (newTarget, reason) = when (goal) {
            Goal.CUT -> when {
                weightChange > -0.2 -> (currentCalories - 150) to "Weight loss stalled this week — cutting 150 kcal to keep the trend moving."
                weightChange < -1.2 -> (currentCalories + 150) to "Losing faster than planned — adding 150 kcal back to keep it sustainable."
                else -> currentCalories to "Right on track this week — no change needed."
            }
            Goal.BULK -> when {
                weightChange < 0.1 -> (currentCalories + 150) to "Barely any gain this week — adding 150 kcal to support growth."
                weightChange > 0.6 -> (currentCalories - 100) to "Gaining faster than ideal — trimming 100 kcal to reduce excess fat gain."
                else -> currentCalories to "Right on track this week — no change needed."
            }
            Goal.MAINTAIN -> when {
                weightChange > 0.4 -> (currentCalories - 100) to "Trending up while maintaining — cutting 100 kcal to level off."
                weightChange < -0.4 -> (currentCalories + 100) to "Trending down while maintaining — adding 100 kcal to level off."
                else -> currentCalories to "Holding steady this week — no change needed."
            }
            Goal.REVERSE -> {
                if (currentCalories >= estimatedMaintenance) {
                    currentCalories to "You've reached your estimated maintenance — consider switching your goal to Maintain."
                } else {
                    when {
                        weightChange > 0.5 -> currentCalories to "Gaining a bit fast for a reverse — holding calories steady this week to let things settle."
                        else -> (currentCalories + 100).coerceAtMost(estimatedMaintenance) to "Reverse dieting — adding 100 kcal to keep gradually rebuilding your intake."
                    }
                }
            }
        }

        return WeeklyAdjustment(
            newTarget = newTarget,
            deltaCalories = newTarget - currentCalories,
            weekChangeKg = weightChange,
            reason = reason
        )
    }
}