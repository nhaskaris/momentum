package com.eliteonetube.momentum.logic.algorithm

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The outcome of a weekly check-in: what the target becomes and why. */
data class WeeklyReviewResult(
    val newTarget: Int,
    val reason: String,
    val estimatedMaintenance: Int,
    /** False when there weren't enough weigh-ins to judge, so the target is left alone. */
    val hadEnoughData: Boolean,
    /** Usually the profile's goal; Maintain once a reverse diet reaches maintenance. */
    val newGoal: Goal,
    /** True when the cut's calorie floor decided the verdict. */
    val hitCutFloor: Boolean = false,
    /** The week's eating, when enough days were logged to judge it. */
    val intake: WeekIntake? = null,
    /** True when the target was left alone because the eating, not the target, explains the result. */
    val heldForIntake: Boolean = false
)

/**
 * Runs the weekly review. Shared by the Monday background job (for the notification) and the
 * check-in screen, which runs it again with the weight entered during the check-in so the verdict
 * reflects it.
 */
object WeeklyReview {
    private const val MIN_WEIGH_INS_PER_WEEK = 3

    fun run(
        profile: UserProfile,
        entries: List<WeightEntry>,
        averageDailySteps: Int = profile.averageDailySteps,
        bodyFatPercentage: Double? = profile.bodyFatPercentage,
        /** Calories eaten per day (ISO date); see [IntakeCheck.dailyCalories]. Empty leaves food out. */
        dailyCalories: Map<String, Double> = emptyMap(),
        today: LocalDate = LocalDate.now()
    ): WeeklyReviewResult {
        val algorithm = CoachAlgorithm()

        // Same windows as CoachAlgorithm.calculateWeeklyAdjustment: days 0–6 vs 7–13 ago
        val thisWeek = entries.filter { daysAgo(it, today) in 0..6 }
        val lastWeekCount = entries.count { daysAgo(it, today) in 7..13 }

        // Maintenance follows this week's average weight once there's enough of it
        val maintenance = if (thisWeek.size >= MIN_WEIGH_INS_PER_WEEK) {
            algorithm.calculateInitialMaintenance(
                weightKg = thisWeek.map { it.weight }.average(),
                heightCm = profile.height,
                age = profile.age,
                isMale = profile.isMale,
                averageDailySteps = averageDailySteps,
                bodyFatPercentage = bodyFatPercentage
            )
        } else {
            profile.estimatedMaintenanceCalories
        }

        // A week eaten against two different targets can't be judged, so food is left out of it
        val intake = if (IntakeCheck.targetChangedDuringWeek(entries, profile.currentCalorieTarget, today)) null
        else IntakeCheck.weekIntake(dailyCalories, profile.currentCalorieTarget, today)

        val adjustment = algorithm.calculateWeeklyAdjustment(
            entries = entries,
            currentCalories = profile.currentCalorieTarget,
            goal = profile.goal,
            estimatedMaintenance = maintenance,
            // An adjustment needs 3+ weigh-ins this week, so the floor follows this week's average too
            cutFloor = currentWeightKg(entries, today)?.let { cutFloor(profile, it, bodyFatPercentage) } ?: 0,
            intake = intake
        )

        return if (adjustment != null) {
            WeeklyReviewResult(
                adjustment.newTarget, adjustment.reason, maintenance, hadEnoughData = true,
                newGoal = algorithm.goalAfterReview(profile.goal, adjustment.newTarget, maintenance),
                hitCutFloor = adjustment.hitCutFloor,
                intake = intake,
                heldForIntake = adjustment.heldForIntake
            )
        } else {
            WeeklyReviewResult(
                newTarget = profile.currentCalorieTarget,
                reason = "Only ${weighIns(thisWeek.size)} this week and ${weighIns(lastWeekCount)} the week before — " +
                    "I need $MIN_WEIGH_INS_PER_WEEK in each to judge your trend, so your target stays at " +
                    "${profile.currentCalorieTarget} kcal. More weigh-ins next week will let me adjust it.",
                estimatedMaintenance = maintenance,
                hadEnoughData = false,
                newGoal = profile.goal
            )
        }
    }

    /**
     * The weight to base estimates on: this week's average once there are enough weigh-ins to smooth
     * out water swings, otherwise the latest weigh-in. Null with no weigh-ins at all.
     */
    fun currentWeightKg(entries: List<WeightEntry>, today: LocalDate = LocalDate.now()): Double? {
        val thisWeek = entries.filter { daysAgo(it, today) in 0..6 }
        return if (thisWeek.size >= MIN_WEIGH_INS_PER_WEEK) thisWeek.map { it.weight }.average()
        else entries.filter { daysAgo(it, today) >= 0 }.maxByOrNull { it.date }?.weight
    }

    /** The lowest target a cut may set for this profile at [weightKg]. */
    fun cutFloor(profile: UserProfile, weightKg: Double, bodyFatPercentage: Double? = profile.bodyFatPercentage): Int {
        val algorithm = CoachAlgorithm()
        val bmr = algorithm.calculateBmr(weightKg, profile.height, profile.age, profile.isMale, bodyFatPercentage)
        return algorithm.calculateCutFloor(bmr, profile.isMale)
    }

    /** [cutFloor] at [currentWeightKg]; 0 (no floor to apply) before the first weigh-in. */
    fun cutFloor(profile: UserProfile, entries: List<WeightEntry>, today: LocalDate = LocalDate.now()): Int =
        currentWeightKg(entries, today)?.let { cutFloor(profile, it) } ?: 0

    private fun weighIns(count: Int) = if (count == 1) "1 weigh-in" else "$count weigh-ins"

    private fun daysAgo(entry: WeightEntry, today: LocalDate): Long =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(entry.date), today) }.getOrDefault(-1)
}
