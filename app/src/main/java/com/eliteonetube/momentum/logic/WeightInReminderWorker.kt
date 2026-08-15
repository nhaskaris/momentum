package com.eliteonetube.momentum.logic

import android.content.Context
import android.util.Log
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
            val database = WeightDatabase.getInstance(applicationContext)
            val dao = database.weightDao()
            
            val profile = dao.getUserProfile().first() ?: return Result.success()
            val today = LocalDate.now().toString()
            val loggedToday = dao.hasWeightForDate(today)

            if (!loggedToday) {
                NotificationHelper.showWeighInReminder(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WeighInReminderWorker", "Error running weigh-in reminder", e)
            Result.retry()
        }
    }
}
