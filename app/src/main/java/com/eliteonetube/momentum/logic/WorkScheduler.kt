package com.eliteonetube.momentum.logic

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eliteonetube.momentum.data.UserProfile
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object WorkScheduler {
    fun scheduleWeeklyRecalculation(context: Context) {
        val now = LocalDateTime.now()
        var nextRun = now.with(LocalTime.of(4, 30))
        if (now.dayOfWeek != DayOfWeek.MONDAY || now.isAfter(nextRun)) {
            nextRun = nextRun.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        val initialDelayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<WeeklyRecalculationWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weekly_calorie_recalculation",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleDailyReminders(context: Context, profile: UserProfile?) {
        if (profile == null || !profile.remindersEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork("daily_weighin_morning")
            WorkManager.getInstance(context).cancelUniqueWork("daily_weighin_evening")
            return
        }

        // Morning nudge
        val morningTime = try {
            LocalTime.parse(profile.morningReminderTime)
        } catch (e: Exception) {
            LocalTime.of(8, 30)
        }
        scheduleReminder(context, "daily_weighin_morning", morningTime.hour, morningTime.minute)
        
        // Evening nudge
        val eveningTime = try {
            LocalTime.parse(profile.eveningReminderTime)
        } catch (e: Exception) {
            LocalTime.of(20, 0)
        }
        scheduleReminder(context, "daily_weighin_evening", eveningTime.hour, eveningTime.minute)
    }

    private fun scheduleReminder(context: Context, tag: String, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var nextRun = now.with(LocalTime.of(hour, minute))
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<WeighInReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            tag,
            ExistingPeriodicWorkPolicy.REPLACE, // Use REPLACE to update times
            request
        )
    }

    @Deprecated("Use scheduleDailyReminders instead")
    fun scheduleDailyWeighInReminder(context: Context) {
        // This is now handled via the profile-aware version
    }
}
