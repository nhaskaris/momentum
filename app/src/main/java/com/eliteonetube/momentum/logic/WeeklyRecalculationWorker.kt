package com.eliteonetube.momentum.logic

import android.content.Context
import androidx.room3.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliteonetube.momentum.data.WeightDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class WeeklyRecalculationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = Room.databaseBuilder<WeightDatabase>(
                context = applicationContext,
                name = "weight_tracker_db",
            ).addMigrations(
                WeightDatabase.MIGRATION_12_13,
                WeightDatabase.MIGRATION_13_14,
                WeightDatabase.MIGRATION_14_15,
                WeightDatabase.MIGRATION_15_16,
                WeightDatabase.MIGRATION_16_17
            ).fallbackToDestructiveMigration().build()

            val dao = database.weightDao()
            val profile = dao.getUserProfile().first() ?: return Result.success()
            val entries = dao.getLastTwoWeeks().first()

            var effectiveAverageSteps = profile.averageDailySteps
            if (profile.useHealthConnect) {
                val healthConnectManager = HealthConnectManager(applicationContext)
                val hcSteps = healthConnectManager.fetchAverageStepsLast7Days()
                if (hcSteps != null) {
                    effectiveAverageSteps = hcSteps
                }
            }

            // Recalculate maintenance off this week's average weight, not a single reading —
            // smooths out day-to-day noise the same way the trend comparison does.
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

            if (adjustment == null) {
                if (recalculatedMaintenance != profile.estimatedMaintenanceCalories) {
                    dao.saveProfile(profile.copy(estimatedMaintenanceCalories = recalculatedMaintenance))
                }
                return Result.success()
            }

            dao.saveProfile(
                profile.copy(
                    estimatedMaintenanceCalories = recalculatedMaintenance,
                    pendingCalorieTarget = if (adjustment.deltaCalories != 0) adjustment.newTarget else null,
                    pendingAdjustmentReason = adjustment.reason
                )
            )

            NotificationHelper.showAdjustmentNotification(applicationContext, adjustment)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}