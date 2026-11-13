package com.eliteonetube.momentum.logic.algorithm

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.services.ReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntakeCheckTest {
    private val algorithm = CoachAlgorithm()
    private val today = LocalDate.now()

    /** Four weigh-ins a week: [lastWeek] for days 7–10 ago, [thisWeek] for days 0–3 ago. */
    private fun weighIns(lastWeek: Double, thisWeek: Double, targetAtEntry: Int? = null): List<WeightEntry> =
        (0..3).map { WeightEntry(today.minusDays(it.toLong()).toString(), thisWeek, targetAtEntry) } +
            (7..10).map { WeightEntry(today.minusDays(it.toLong()).toString(), lastWeek, targetAtEntry) }

    /** The same calories on each of the given days-ago. */
    private fun eaten(calories: Double, daysAgo: IntRange = 1..7): Map<String, Double> =
        daysAgo.associate { today.minusDays(it.toLong()).toString() to calories }

    private fun adjust(goal: Goal, current: Int, lastWeek: Double, thisWeek: Double, averageEaten: Int?, floor: Int = 1500) =
        algorithm.calculateWeeklyAdjustment(
            weighIns(lastWeek, thisWeek), current, goal, estimatedMaintenance = 2600, cutFloor = floor,
            intake = averageEaten?.let { WeekIntake(it, 7) }
        )!!

    // --- Reading the diary ---

    @Test
    fun weekIntake_averagesTheSevenDaysBeforeToday() {
        val diary = eaten(2000.0) + (today.toString() to 5000.0) + (today.minusDays(8).toString() to 5000.0)
        assertEquals(WeekIntake(2000, 7), IntakeCheck.weekIntake(diary, calorieTarget = 2000, today = today))
    }

    @Test
    fun weekIntake_treatsHalfLoggedDaysAsNotLogged() {
        // Five proper days plus two with only a breakfast logged
        val diary = eaten(2000.0, 1..5) + eaten(400.0, 6..7)
        assertEquals(WeekIntake(2000, 5), IntakeCheck.weekIntake(diary, calorieTarget = 2000, today = today))
        // Exactly half the target still counts
        assertEquals(7, IntakeCheck.weekIntake(eaten(1000.0), calorieTarget = 2000, today = today)!!.daysLogged)
    }

    @Test
    fun weekIntake_needsFiveLoggedDays() {
        assertNull(IntakeCheck.weekIntake(eaten(2000.0, 1..4), calorieTarget = 2000, today = today))
        assertNull(IntakeCheck.weekIntake(emptyMap(), calorieTarget = 2000, today = today))
    }

    @Test
    fun offPlan_isMoreThanTenPercentEitherWay() {
        assertFalse(IntakeCheck.ateOverTarget(WeekIntake(2200, 7), 2000))
        assertTrue(IntakeCheck.ateOverTarget(WeekIntake(2201, 7), 2000))
        assertFalse(IntakeCheck.ateUnderTarget(WeekIntake(1800, 7), 2000))
        assertTrue(IntakeCheck.ateUnderTarget(WeekIntake(1799, 7), 2000))
    }

    // --- Holding the target ---

    @Test
    fun cutStalledWhileEatingOver_holdsInsteadOfCutting() {
        val result = adjust(Goal.CUT, current = 1900, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 2400)
        assertEquals(1900, result.newTarget)
        assertTrue(result.heldForIntake)
        assertTrue(result.reason.startsWith("You averaged 2400 kcal against a 1900 kcal target"))
    }

    @Test
    fun cutStalledWhileOnPlan_cutsAsBefore() {
        val result = adjust(Goal.CUT, current = 1900, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 1950)
        assertEquals(1750, result.newTarget)
        assertFalse(result.heldForIntake)
    }

    @Test
    fun cutLosingTooFastWhileEatingUnder_holdsInsteadOfAdding() {
        val result = adjust(Goal.CUT, current = 1900, lastWeek = 82.0, thisWeek = 80.0, averageEaten = 1500)
        assertEquals(1900, result.newTarget)
        assertTrue(result.heldForIntake)
    }

    @Test
    fun eatingOverButStillOnTrack_keepsTheNormalVerdict() {
        val result = adjust(Goal.CUT, current = 1900, lastWeek = 80.5, thisWeek = 80.0, averageEaten = 2400)
        assertFalse(result.heldForIntake)
        assertTrue(result.reason.startsWith("Right on track"))
    }

    @Test
    fun maintainAndBulk_holdWhenEatingExplainsTheTrend() {
        // Maintain trending up while eating over; trending down while eating under
        assertTrue(adjust(Goal.MAINTAIN, 2400, lastWeek = 80.0, thisWeek = 80.6, averageEaten = 2800).heldForIntake)
        assertTrue(adjust(Goal.MAINTAIN, 2400, lastWeek = 80.6, thisWeek = 80.0, averageEaten = 2000).heldForIntake)
        // Bulk gaining too fast while eating over; barely gaining while eating under
        assertTrue(adjust(Goal.BULK, 2800, lastWeek = 80.0, thisWeek = 80.8, averageEaten = 3300).heldForIntake)
        assertTrue(adjust(Goal.BULK, 2800, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 2300).heldForIntake)
        // Eating over doesn't block a raise
        assertFalse(adjust(Goal.BULK, 2800, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 3300).heldForIntake)
    }

    @Test
    fun reverse_holdsOnlyWhenEatingUnder() {
        val under = adjust(Goal.REVERSE, current = 2000, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 1700)
        assertEquals(2000, under.newTarget)
        assertTrue(under.heldForIntake)
        assertTrue(under.reason.contains("get up to your current target first"))

        val over = adjust(Goal.REVERSE, current = 2000, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 2400)
        assertEquals(2100, over.newTarget)
        assertFalse(over.heldForIntake)
    }

    @Test
    fun cutFloorVerdicts_winOverTheFoodLog() {
        // Below the floor and eating under target: still raised to the floor
        val raised = adjust(Goal.CUT, current = 1300, lastWeek = 82.0, thisWeek = 80.0, averageEaten = 1000)
        assertEquals(1500, raised.newTarget)
        assertFalse(raised.heldForIntake)
        // Stalled at the floor while eating over: the floor message stands
        val atFloor = adjust(Goal.CUT, current = 1500, lastWeek = 80.0, thisWeek = 80.0, averageEaten = 2000)
        assertTrue(atFloor.hitCutFloor)
        assertFalse(atFloor.heldForIntake)
    }

    @Test
    fun noFoodData_behavesExactlyAsBefore() {
        val result = adjust(Goal.CUT, current = 1900, lastWeek = 80.0, thisWeek = 80.0, averageEaten = null)
        assertEquals(1750, result.newTarget)
        assertFalse(result.heldForIntake)
    }

    // --- The whole review ---

    private val cutter = UserProfile(
        height = 180.0, age = 30, isMale = true, averageDailySteps = 8000,
        estimatedMaintenanceCalories = 2300, goal = Goal.CUT, currentCalorieTarget = 2000
    )

    @Test
    fun weeklyReview_holdsAndReportsIntake() {
        val review = WeeklyReview.run(cutter, weighIns(80.0, 80.0, targetAtEntry = 2000), dailyCalories = eaten(2500.0), today = today)
        assertEquals(2000, review.newTarget)
        assertTrue(review.heldForIntake)
        assertEquals(WeekIntake(2500, 7), review.intake)
        // An off-plan hold is not a moment to ask for a review
        assertFalse(ReviewPolicy.isGoodCheckIn(review, previousTarget = 2000))
    }

    @Test
    fun weeklyReview_ignoresFoodWhenTheTargetChangedMidWeek() {
        // Weigh-ins this week were recorded against a different target
        val review = WeeklyReview.run(cutter, weighIns(80.0, 80.0, targetAtEntry = 2300), dailyCalories = eaten(2500.0), today = today)
        assertNull(review.intake)
        assertFalse(review.heldForIntake)
        assertEquals(1850, review.newTarget)
    }

    @Test
    fun weeklyReview_ignoresFoodWithTooFewLoggedDays() {
        val review = WeeklyReview.run(cutter, weighIns(80.0, 80.0), dailyCalories = eaten(2500.0, 1..4), today = today)
        assertNull(review.intake)
        assertEquals(1850, review.newTarget)
    }

    // --- The 7-day windows ---

    @Test
    fun lastWeek_isDaysSevenToThirteenOnly() {
        // Three weigh-ins this week, but last week only has two plus one from 14 days ago
        val entries = (0..2).map { WeightEntry(today.minusDays(it.toLong()).toString(), 80.0) } +
            listOf(7L, 8L, 14L).map { WeightEntry(today.minusDays(it).toString(), 80.0) }
        assertNull(algorithm.calculateWeeklyAdjustment(entries, 2000, Goal.CUT, 2600, cutFloor = 1500))

        val withDayThirteen = entries + WeightEntry(today.minusDays(13).toString(), 80.0)
        assertEquals(1850, algorithm.calculateWeeklyAdjustment(withDayThirteen, 2000, Goal.CUT, 2600, cutFloor = 1500)!!.newTarget)
    }
}
