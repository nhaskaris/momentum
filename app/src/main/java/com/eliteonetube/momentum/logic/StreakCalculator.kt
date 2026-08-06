package com.eliteonetube.momentum.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StreakCalculator {
    /**
     * Current consecutive-day streak ending today or yesterday.
     * Logging today isn't required to keep a streak "alive" — if the most
     * recent entry was yesterday, the streak still counts (today just hasn't
     * happened yet), matching how most habit-tracking apps behave.
     */
    fun currentStreak(sortedDates: List<String>): Int {
        if (sortedDates.isEmpty()) return 0

        val parsedDates = sortedDates.mapNotNull {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }.toSortedSet()

        if (parsedDates.isEmpty()) return 0

        val today = LocalDate.now()
        val mostRecent = parsedDates.last()
        val gapFromToday = ChronoUnit.DAYS.between(mostRecent, today)

        // Streak is broken if the most recent entry is more than 1 day old
        if (gapFromToday > 1) return 0

        var streak = 1
        var cursor = mostRecent
        while (true) {
            val previous = cursor.minusDays(1)
            if (parsedDates.contains(previous)) {
                streak++
                cursor = previous
            } else {
                break
            }
        }
        return streak
    }
}