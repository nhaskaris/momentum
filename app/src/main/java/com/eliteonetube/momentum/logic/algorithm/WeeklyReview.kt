package com.eliteonetube.momentum.logic.algorithm

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
    val hadEnoughData: Boolean
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
        today: LocalDate = LocalDate.now()
    ): WeeklyReviewResult {
        val algorithm = CoachAlgorithm()

        // Same windows as CoachAlgorithm.calculateWeeklyAdjustment: days 0–6 vs 7–14 ago
        val thisWeek = entries.filter { daysAgo(it, today) in 0..6 }
        val lastWeekCount = entries.count { daysAgo(it, today) in 7..14 }

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

        val adjustment = algorithm.calculateWeeklyAdjustment(
            entries = entries,
            currentCalories = profile.currentCalorieTarget,
            goal = profile.goal,
            estimatedMaintenance = maintenance
        )

        return if (adjustment != null) {
            WeeklyReviewResult(adjustment.newTarget, adjustment.reason, maintenance, hadEnoughData = true)
        } else {
            WeeklyReviewResult(
                newTarget = profile.currentCalorieTarget,
                reason = "Only ${weighIns(thisWeek.size)} this week and ${weighIns(lastWeekCount)} the week before — " +
                    "I need $MIN_WEIGH_INS_PER_WEEK in each to judge your trend, so your target stays at " +
                    "${profile.currentCalorieTarget} kcal. More weigh-ins next week will let me adjust it.",
                estimatedMaintenance = maintenance,
                hadEnoughData = false
            )
        }
    }

    private fun weighIns(count: Int) = if (count == 1) "1 weigh-in" else "$count weigh-ins"

    private fun daysAgo(entry: WeightEntry, today: LocalDate): Long =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(entry.date), today) }.getOrDefault(-1)
}
