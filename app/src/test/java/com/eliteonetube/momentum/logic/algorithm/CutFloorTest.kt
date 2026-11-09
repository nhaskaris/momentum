package com.eliteonetube.momentum.logic.algorithm

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CutFloorTest {
    private val algorithm = CoachAlgorithm()
    private val today = LocalDate.now()

    /** Four weigh-ins a week: [lastWeek] for days 7–10 ago, [thisWeek] for days 0–3 ago. */
    private fun weighIns(lastWeek: Double, thisWeek: Double): List<WeightEntry> =
        (0..3).map { WeightEntry(today.minusDays(it.toLong()).toString(), thisWeek) } +
            (7..10).map { WeightEntry(today.minusDays(it.toLong()).toString(), lastWeek) }

    /** Flat weight = stalled cut. */
    private fun cut(current: Int, floor: Int = 1500, maintenance: Int = 2400, lastWeek: Double = 80.0, thisWeek: Double = 80.0) =
        algorithm.calculateWeeklyAdjustment(weighIns(lastWeek, thisWeek), current, Goal.CUT, maintenance, floor)!!

    @Test
    fun floor_isSexMinimumOrBmrWhicheverIsHigher() {
        assertEquals(1200, algorithm.calculateCutFloor(bmr = 1100.0, isMale = false))
        assertEquals(1500, algorithm.calculateCutFloor(bmr = 1400.0, isMale = true))
        assertEquals(1820, algorithm.calculateCutFloor(bmr = 1820.4, isMale = true))
    }

    @Test
    fun stalledAboveFloor_cuts150AsBefore() {
        assertEquals(1850, cut(current = 2000).newTarget)
    }

    @Test
    fun stalledStepWouldCrossFloor_landsOnFloor() {
        val result = cut(current = 1600)
        assertEquals(1500, result.newTarget)
        assertTrue(result.reason.contains("won't go below"))
    }

    @Test
    fun stalledAtFloor_holdsAndSuggestsABreak() {
        val result = cut(current = 1500)
        assertEquals(1500, result.newTarget)
        assertTrue(result.reason.contains("Consider a break from cutting"))
    }

    @Test
    fun belowFloorFromBefore_isRaisedEvenWhenOnTrack() {
        val result = cut(current = 1300, lastWeek = 80.5, thisWeek = 80.0)
        assertEquals(1500, result.newTarget)
        assertTrue(result.reason.startsWith("Raising you"))
    }

    @Test
    fun losingFastAtFloor_stillAddsCalories() {
        assertEquals(1650, cut(current = 1500, lastWeek = 82.0, thisWeek = 80.0).newTarget)
    }

    @Test
    fun floorAtOrAboveMaintenance_holdsFloorAndSuggestsMaintain() {
        val result = cut(current = 1100, floor = 1200, maintenance = 1150)
        assertEquals(1200, result.newTarget)
        assertTrue(result.reason.contains("cutting isn't recommended"))
    }

    @Test
    fun switchingToCut_respectsFloor() {
        assertEquals(1950, algorithm.calculateGoalTransition(Goal.MAINTAIN, Goal.CUT, 2200, 2400, cutFloor = 1500).newTarget)
        val clamped = algorithm.calculateGoalTransition(Goal.MAINTAIN, Goal.CUT, 1650, 2000, cutFloor = 1500)
        assertEquals(1500, clamped.newTarget)
        assertTrue(clamped.explanation.contains("minimum safe intake"))
        val notRecommended = algorithm.calculateGoalTransition(Goal.MAINTAIN, Goal.CUT, 1150, 1150, cutFloor = 1200)
        assertEquals(1200, notRecommended.newTarget)
        assertTrue(notRecommended.explanation.contains("cutting isn't recommended"))
    }

    @Test
    fun currentWeight_usesWeekAverageWithThreeWeighIns() {
        val entries = listOf(80.0, 81.0, 82.0).mapIndexed { i, w -> WeightEntry(today.minusDays(i.toLong()).toString(), w) }
        assertEquals(81.0, WeeklyReview.currentWeightKg(entries, today)!!, 0.001)
    }

    @Test
    fun currentWeight_fallsBackToLatestWithFewWeighIns() {
        val entries = listOf(
            WeightEntry(today.minusDays(1).toString(), 79.0),
            WeightEntry(today.minusDays(9).toString(), 83.0),
            WeightEntry(today.plusDays(1).toString(), 50.0) // a future-dated entry is ignored
        )
        assertEquals(79.0, WeeklyReview.currentWeightKg(entries, today)!!, 0.001)
        assertNull(WeeklyReview.currentWeightKg(emptyList(), today))
    }

    @Test
    fun weeklyReview_raisesExistingCutterToFloor() {
        val profile = UserProfile(
            height = 180.0, age = 30, isMale = true, averageDailySteps = 8000,
            estimatedMaintenanceCalories = 2300, goal = Goal.CUT, currentCalorieTarget = 1200
        )
        val result = WeeklyReview.run(profile, weighIns(lastWeek = 80.3, thisWeek = 80.0), today = today)
        // Mifflin at 80 kg / 180 cm / 30 y male = 1780, above the 1500 minimum
        assertEquals(1780, result.newTarget)
        assertEquals(Goal.CUT, result.newGoal)
    }
}
