package com.eliteonetube.momentum.logic.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliteonetube.momentum.data.WeightDao
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.algorithm.CoachAlgorithm
import com.eliteonetube.momentum.logic.algorithm.WeeklyAdjustment
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class WeeklyRecalculationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = WeightDatabase.getInstance(applicationContext).weightDao()
        try {
            recalculate(dao)
        } catch (e: Exception) {
            // No retry: a retried run would queue a second "next Monday" check-in
            Log.e("WeeklyRecalculation", "Weekly check-in failed", e)
        }

        // One-off job: queue next Monday at the (possibly updated) morning reminder time
        val profile = runCatching { dao.getUserProfile().first() }.getOrNull()
        val time = profile?.let { WorkScheduler.reminderTime(it, WorkScheduler.ReminderSlot.MORNING) }
            ?: WorkScheduler.ReminderSlot.MORNING.defaultTime
        WorkScheduler.scheduleNextWeeklyCheckIn(applicationContext, time)
        return Result.success()
    }

    private suspend fun recalculate(dao: WeightDao) {
        val profile = dao.getUserProfile().first() ?: return
        val entries = dao.getLastTwoWeeks().first()

        var effectiveAverageSteps = profile.averageDailySteps
        if (profile.useHealthConnect) {
            val healthConnectManager = HealthConnectManager(applicationContext)
            val hcSteps = healthConnectManager.fetchAverageStepsLast7Days()
            if (hcSteps != null) {
                effectiveAverageSteps = hcSteps
            }
        }

        // Recalculate maintenance off this week's average weight
        val today = LocalDate.now()
        val thisWeekWeights = entries.mapNotNull { entry ->
            val date = try { LocalDate.parse(entry.date) } catch (e: Exception) { return@mapNotNull null }
            if (ChronoUnit.DAYS.between(date, today) in 0..6) entry.weight else null
        }

        val recalculatedMaintenance = if (thisWeekWeights.size >= 3) {
            CoachAlgorithm().calculateInitialMaintenance(
                weightKg = thisWeekWeights.average(),
                heightCm = profile.height,
                age = profile.age,
                isMale = profile.isMale,
                averageDailySteps = effectiveAverageSteps,
                bodyFatPercentage = profile.bodyFatPercentage
            )
        } else {
            profile.estimatedMaintenanceCalories
        }

        val adjustment = CoachAlgorithm().calculateWeeklyAdjustment(
            entries = entries,
            currentCalories = profile.currentCalorieTarget,
            goal = profile.goal,
            estimatedMaintenance = recalculatedMaintenance
        )

        val reason = adjustment?.reason ?: "Right on track — no change needed."
        val nextTarget = adjustment?.newTarget ?: profile.currentCalorieTarget

        dao.saveProfile(
            profile.copy(
                estimatedMaintenanceCalories = recalculatedMaintenance,
                pendingCalorieTarget = nextTarget,
                pendingAdjustmentReason = reason,
                checkInDue = true
            )
        )

        NotificationHelper.showAdjustmentNotification(
            applicationContext,
            adjustment ?: WeeklyAdjustment(nextTarget, 0, 0.0, reason)
        )
    }
}
