package com.eliteonetube.momentum.logic

import android.content.Context
import androidx.room3.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliteonetube.momentum.data.WeightDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class WeighInReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = Room.databaseBuilder<WeightDatabase>(
                context = applicationContext,
                name = "weight_tracker_db",
            ).addMigrations(WeightDatabase.MIGRATION_12_13, WeightDatabase.MIGRATION_13_14)
             .fallbackToDestructiveMigration().build()

            val dao = database.weightDao()
            val profile = dao.getUserProfile().first() ?: return Result.success()
            val today = LocalDate.now().toString()
            val loggedToday = dao.getLastTwoWeeks().first().any { it.date == today }

            if (!loggedToday) {
                NotificationHelper.showWeighInReminder(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}