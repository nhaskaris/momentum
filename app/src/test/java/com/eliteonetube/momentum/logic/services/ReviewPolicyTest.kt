package com.eliteonetube.momentum.logic.services

import com.eliteonetube.momentum.data.Goal
import com.eliteonetube.momentum.logic.algorithm.CoachAlgorithm
import com.eliteonetube.momentum.logic.algorithm.WeeklyReviewResult
import com.eliteonetube.momentum.data.WeightEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReviewPolicyTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun review(newTarget: Int, hadEnoughData: Boolean = true, hitCutFloor: Boolean = false) =
        WeeklyReviewResult(newTarget, "", 2400, hadEnoughData, Goal.CUT, hitCutFloor)

    @Test
    fun canAsk_firstTimeAndAfter120Days() {
        assertTrue(ReviewPolicy.canAsk(null, 0, today))
        assertTrue(ReviewPolicy.canAsk(today.minusDays(120), 1, today))
        assertFalse(ReviewPolicy.canAsk(today.minusDays(119), 1, today))
    }

    @Test
    fun canAsk_neverAfterThreeAsks() {
        assertTrue(ReviewPolicy.canAsk(today.minusDays(400), 2, today))
        assertFalse(ReviewPolicy.canAsk(today.minusDays(400), 3, today))
    }

    @Test
    fun goodCheckIn_whenCaloriesHoldOrRise() {
        assertTrue(ReviewPolicy.isGoodCheckIn(review(2000), previousTarget = 2000))
        assertTrue(ReviewPolicy.isGoodCheckIn(review(2150), previousTarget = 2000))
    }

    @Test
    fun notGoodCheckIn_whenStalledFloorOrNotEnoughData() {
        assertFalse(ReviewPolicy.isGoodCheckIn(review(1850), previousTarget = 2000))
        assertFalse(ReviewPolicy.isGoodCheckIn(review(1500, hitCutFloor = true), previousTarget = 1500))
        assertFalse(ReviewPolicy.isGoodCheckIn(review(1843, hitCutFloor = true), previousTarget = 1400))
        assertFalse(ReviewPolicy.isGoodCheckIn(review(2000, hadEnoughData = false), previousTarget = 2000))
    }

    @Test
    fun streakMilestone_onlyWhenReachingExactlySeven() {
        assertTrue(ReviewPolicy.reachedStreakMilestone(6, 7))
        assertFalse(ReviewPolicy.reachedStreakMilestone(7, 7)) // re-logging today
        assertFalse(ReviewPolicy.reachedStreakMilestone(13, 14))
        assertFalse(ReviewPolicy.reachedStreakMilestone(5, 6))
    }

    @Test
    fun cutVerdicts_flagFloorOnlyForFloorBranches() {
        val algorithm = CoachAlgorithm()
        val now = LocalDate.now()
        fun entries(lastWeek: Double, thisWeek: Double) =
            (0..3).map { WeightEntry(now.minusDays(it.toLong()).toString(), thisWeek) } +
                (7..10).map { WeightEntry(now.minusDays(it.toLong()).toString(), lastWeek) }
        fun cut(current: Int, lastWeek: Double, thisWeek: Double) =
            algorithm.calculateWeeklyAdjustment(entries(lastWeek, thisWeek), current, Goal.CUT, 2400, cutFloor = 1500)!!.hitCutFloor

        assertFalse(cut(2000, 80.5, 80.0)) // on track
        assertFalse(cut(2000, 80.0, 80.0)) // ordinary stall
        assertFalse(cut(1500, 80.5, 80.0)) // on track while at the floor
        assertTrue(cut(1500, 80.0, 80.0))  // stalled at the floor
        assertTrue(cut(1600, 80.0, 80.0))  // lowered to the floor
        assertTrue(cut(1300, 80.5, 80.0))  // raised to the floor
    }
}
