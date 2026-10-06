package com.eliteonetube.momentum.logic.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.eliteonetube.momentum.data.UserProfile
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Notifications run as one-off jobs that schedule their own next run, instead of periodic work:
 * periodic jobs only repeat roughly every interval and drift away from the time the user picked.
 */
object WorkScheduler {
    enum class ReminderSlot(val workName: String, val defaultTime: LocalTime) {
        MORNING("reminder_morning", LocalTime.of(8, 30)),
        EVENING("reminder_evening", LocalTime.of(20, 0))
    }

    const val KEY_SLOT = "slot"
    private const val WEEKLY_CHECK_IN = "weekly_check_in"

    // Names used by the old periodic jobs, cancelled so they don't fire alongside the new ones
    private val legacyWorkNames = listOf("daily_weighin_morning", "daily_weighin_evening", "weekly_calorie_recalculation")

    fun reminderTime(profile: UserProfile, slot: ReminderSlot): LocalTime {
        val raw = if (slot == ReminderSlot.MORNING) profile.morningReminderTime else profile.eveningReminderTime
        return runCatching { LocalTime.parse(raw) }.getOrDefault(slot.defaultTime)
    }

    /** (Re)schedules both daily weigh-in reminders from the profile, or cancels them when turned off. */
    fun scheduleDailyReminders(context: Context, profile: UserProfile?) {
        val workManager = WorkManager.getInstance(context)
        legacyWorkNames.take(2).forEach { workManager.cancelUniqueWork(it) }

        if (profile == null || !profile.remindersEnabled) {
            ReminderSlot.entries.forEach { workManager.cancelUniqueWork(it.workName) }
            return
        }
        ReminderSlot.entries.forEach { slot ->
            scheduleReminder(context, slot, reminderTime(profile, slot), ExistingWorkPolicy.REPLACE)
        }
    }

    /** Called by the reminder worker itself to queue tomorrow's run after the current one. */
    fun scheduleNextReminder(context: Context, slot: ReminderSlot, time: LocalTime) {
        scheduleReminder(context, slot, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun scheduleReminder(context: Context, slot: ReminderSlot, time: LocalTime, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<WeighInReminderWorker>()
            .setInitialDelay(secondsUntil(nextDaily(time)), TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_SLOT to slot.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(slot.workName, policy, request)
    }

    /**
     * The weekly check-in runs on Mondays at the morning reminder time (it used to be 4:30 am).
     * [replace] = true when the time changed; otherwise an already-queued run is kept, so opening
     * the app can't cancel a check-in that's in progress.
     */
    fun scheduleWeeklyCheckIn(context: Context, time: LocalTime, replace: Boolean) {
        WorkManager.getInstance(context).cancelUniqueWork(legacyWorkNames[2])
        enqueueWeeklyCheckIn(context, time, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
    }

    /** Called by the weekly worker to queue next Monday's run after the current one. */
    fun scheduleNextWeeklyCheckIn(context: Context, time: LocalTime) {
        enqueueWeeklyCheckIn(context, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueWeeklyCheckIn(context: Context, time: LocalTime, policy: ExistingWorkPolicy) {
        val now = LocalDateTime.now()
        var nextRun = now.with(time).withSecond(0).withNano(0)
        if (now.dayOfWeek != DayOfWeek.MONDAY || !nextRun.isAfter(now)) {
            nextRun = nextRun.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        val request = OneTimeWorkRequestBuilder<WeeklyRecalculationWorker>()
            .setInitialDelay(secondsUntil(nextRun), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WEEKLY_CHECK_IN, policy, request)
    }

    private fun nextDaily(time: LocalTime): LocalDateTime {
        val now = LocalDateTime.now()
        val today = now.with(time).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    // Seconds, rounded up: a job that fires even slightly early would see "today" still ahead
    // and queue a second notification for the same slot
    private fun secondsUntil(target: LocalDateTime): Long =
        (Duration.between(LocalDateTime.now(), target).toMillis() / 1000 + 1).coerceAtLeast(0)
}
