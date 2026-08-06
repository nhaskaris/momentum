package com.eliteonetube.momentum.logic

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

    fun scheduleDailyWeighInReminder(context: Context) {
        val now = LocalDateTime.now()
        var nextRun = now.with(LocalTime.of(20, 0)) // 8:00 PM local time
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<WeighInReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_weighin_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}