package com.eliteonetube.momentum.logic.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliteonetube.momentum.data.WeightDao
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.algorithm.WeeklyReview
import com.eliteonetube.momentum.logic.algorithm.WeeklyAdjustment
import kotlinx.coroutines.flow.first

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

        val review = WeeklyReview.run(profile, entries, averageDailySteps = effectiveAverageSteps)

        dao.saveProfile(
            profile.copy(
                estimatedMaintenanceCalories = review.estimatedMaintenance,
                // A preview for the notification and Dashboard; the check-in re-runs the review with
                // the weight entered there, and that result is what gets applied
                pendingCalorieTarget = review.newTarget,
                pendingAdjustmentReason = review.reason,
                checkInDue = true
            )
        )

        NotificationHelper.showAdjustmentNotification(
            applicationContext,
            WeeklyAdjustment(review.newTarget, review.newTarget - profile.currentCalorieTarget, 0.0, review.reason)
        )
    }
}
