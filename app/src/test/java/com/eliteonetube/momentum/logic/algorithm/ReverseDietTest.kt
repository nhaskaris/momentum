package com.eliteonetube.momentum.logic.algorithm

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReverseDietTest {
    private val algorithm = CoachAlgorithm()

    /** Four weigh-ins a week: [lastWeek] for days 7–10 ago, [thisWeek] for days 0–3 ago. */
    private fun weighIns(lastWeek: Double, thisWeek: Double): List<WeightEntry> {
        val today = LocalDate.now()
        return (0..3).map { WeightEntry(today.minusDays(it.toLong()).toString(), thisWeek) } +
            (7..10).map { WeightEntry(today.minusDays(it.toLong()).toString(), lastWeek) }
    }

    private fun reverse(current: Int, maintenance: Int, lastWeek: Double = 70.0, thisWeek: Double = 70.0) =
        algorithm.calculateWeeklyAdjustment(weighIns(lastWeek, thisWeek), current, Goal.REVERSE, maintenance, cutFloor = 1500)!!

    @Test
    fun belowMaintenance_adds100AndStaysOnReverse() {
        val result = reverse(current = 2000, maintenance = 2500)
        assertEquals(2100, result.newTarget)
        assertEquals(Goal.REVERSE, algorithm.goalAfterReview(Goal.REVERSE, result.newTarget, 2500))
    }

    @Test
    fun stepThatReachesMaintenance_landsOnItAndSwitchesToMaintain() {
        val result = reverse(current = 2450, maintenance = 2500)
        assertEquals(2500, result.newTarget)
        assertTrue(result.reason.startsWith("Reverse complete"))
        assertEquals(Goal.MAINTAIN, algorithm.goalAfterReview(Goal.REVERSE, result.newTarget, 2500))
    }

    @Test
    fun aboveMaintenance_dropsToItAndSwitchesToMaintain() {
        // Maintenance is recalculated weekly and can fall below the current target
        val result = reverse(current = 2600, maintenance = 2500, lastWeek = 70.0, thisWeek = 71.0)
        assertEquals(2500, result.newTarget)
        assertEquals(Goal.MAINTAIN, algorithm.goalAfterReview(Goal.REVERSE, result.newTarget, 2500))
    }

    @Test
    fun gainingFastBelowMaintenance_holdsAndStaysOnReverse() {
        val result = reverse(current = 2000, maintenance = 2500, lastWeek = 70.0, thisWeek = 70.8)
        assertEquals(2000, result.newTarget)
        assertEquals(Goal.REVERSE, algorithm.goalAfterReview(Goal.REVERSE, result.newTarget, 2500))
    }

    @Test
    fun otherGoalsNeverSwitch() {
        for (goal in listOf(Goal.CUT, Goal.BULK, Goal.MAINTAIN)) {
            assertEquals(goal, algorithm.goalAfterReview(goal, 3000, 2500))
        }
    }
}
