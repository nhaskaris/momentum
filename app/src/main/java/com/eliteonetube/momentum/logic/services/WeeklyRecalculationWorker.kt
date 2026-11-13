package com.eliteonetube.momentum.logic.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliteonetube.momentum.data.FoodDao
import com.eliteonetube.momentum.data.WeightDao
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.algorithm.IntakeCheck
import com.eliteonetube.momentum.logic.algorithm.WeeklyReview
import com.eliteonetube.momentum.logic.algorithm.WeeklyAdjustment
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class WeeklyRecalculationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = WeightDatabase.getInstance(applicationContext)
        val dao = database.weightDao()
        try {
            recalculate(dao, database.foodDao())
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

    private suspend fun recalculate(dao: WeightDao, foodDao: FoodDao) {
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

        // Same food data the check-in screen uses, so the notification's preview matches its verdict
        val weekAgo = LocalDate.now().minusDays(7).toString()
        val dailyCalories = IntakeCheck.dailyCalories(
            foodDao.getRecentFoodLogs(weekAgo).first(),
            foodDao.getRecentMealLogs(weekAgo).first()
        )

        val review = WeeklyReview.run(profile, entries, averageDailySteps = effectiveAverageSteps, dailyCalories = dailyCalories)

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
