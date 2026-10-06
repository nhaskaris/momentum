package com.eliteonetube.momentum.logic.services

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
        val slot = inputData.getString(WorkScheduler.KEY_SLOT)
            ?.let { name -> WorkScheduler.ReminderSlot.entries.firstOrNull { it.name == name } }

        try {
            val dao = WeightDatabase.getInstance(applicationContext).weightDao()
            val profile = dao.getUserProfile().first() ?: return Result.success()
            if (!profile.remindersEnabled) return Result.success()

            if (!dao.hasWeightForDate(LocalDate.now().toString())) {
                NotificationHelper.showWeighInReminder(applicationContext)
            }

            // Queue tomorrow's reminder for this slot (one-off jobs don't repeat by themselves)
            if (slot != null) {
                WorkScheduler.scheduleNextReminder(applicationContext, slot, WorkScheduler.reminderTime(profile, slot))
            }
        } catch (e: Exception) {
            // No retry: a retried run would queue a second "next" reminder. Opening the app reschedules anyway.
            Log.e("WeighInReminderWorker", "Error running weigh-in reminder", e)
        }
        return Result.success()
    }
}
