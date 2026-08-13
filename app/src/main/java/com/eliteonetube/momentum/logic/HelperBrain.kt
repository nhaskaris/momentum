package com.eliteonetube.momentum.logic

import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.AppTab

object HelperBrain {

    fun getMessage(
        tab: AppTab,
        profile: UserProfile?,
        recentWeights: List<WeightEntry>,
        todayLogs: List<FoodLogWithItem>,
        streak: Int,
        hasActiveWorkout: Boolean
    ): String {
        return when (tab) {
            AppTab.DASHBOARD -> getDashboardMessage(streak, profile)
            AppTab.STATISTICS -> getStatsMessage(recentWeights)
            AppTab.NUTRITION -> getNutritionMessage(todayLogs)
            AppTab.WORKOUTS -> getWorkoutMessage(hasActiveWorkout)
            AppTab.PROFILE -> getProfileMessage()
        }
    }

    private fun getDashboardMessage(streak: Int, profile: UserProfile?): String {
        if (profile?.checkInDue == true) return "Time for your weekly check-in! I've prepared everything for you."
        
        val pool = mutableListOf(
            "Every weigh-in is a data point for my math. Keep it up!",
            "Small daily actions lead to massive results. You're doing great!",
            "Consistency is our secret weapon. Let's keep that momentum!"
        )
        
        if (streak > 0) {
            pool.add("That $streak-day streak looks amazing on you!")
            pool.add("Don't let the flame go out! $streak days and counting.")
        } else {
            pool.add("Ready to start a new streak today?")
        }
        
        return pool.random()
    }

    private fun getStatsMessage(weights: List<WeightEntry>): String {
        if (weights.size < 2) return "I need at least 2 weight entries to start seeing patterns!"
        
        val change = weights[0].weight - weights.last().weight
        val pool = mutableListOf(
            "I'm crunching the numbers to keep your target perfect.",
            "Don't sweat the daily fluctuations—I'm looking at the big picture.",
            "Metabolism is a moving target, but I've got my eyes on it."
        )
        
        if (change < 0) pool.add("Trend is moving down! Our strategy is working.")
        if (change > 0) pool.add("Up slightly? Likely just water or inflammation. Stay the course.")
        
        return pool.random()
    }

    private fun getNutritionMessage(logs: List<FoodLogWithItem>): String {
        val protein = logs.sumOf { it.protein * it.quantity }
        val calories = logs.sumOf { it.calories * it.quantity }
        
        val pool = mutableListOf(
            "Focus on whole foods today—they keep you full longer!",
            "Remember: we're fueling performance, not just counting numbers.",
            "Logging every bite ensures I can give you the best advice."
        )
        
        if (protein < 40 && logs.isNotEmpty()) {
            pool.add("Protein looks a bit low. Maybe some greek yogurt or chicken?")
        } else if (protein > 100) {
            pool.add("Solid protein intake! Your muscles are well-fed.")
        }
        
        return pool.random()
    }

    private fun getWorkoutMessage(active: Boolean): String {
        val pool = mutableListOf<String>()
        
        if (active) {
            pool.add("Focus on the mind-muscle connection. Every rep counts!")
            pool.add("Need to change things up? You can swap any exercise by tapping the menu on its card.")
            pool.add("If you swap an exercise during a routine, I'll save the change for next time automatically!")
        } else {
            pool.add("Movement is medicine. Ready for a session?")
            pool.add("Pick a routine and let's get to work!")
            pool.add("Rest is just as important as the work. How are you feeling?")
            pool.add("Strength is built in the recovery. Make sure to sleep well!")
            pool.add("Did you know? You can turn any past workout into a Saved Routine by tapping it in your history!")
            pool.add("If you have long workouts in another app, you can select multiple screenshots at once to import the whole routine!")
        }
        
        return pool.random()
    }

    private fun getProfileMessage(): String {
        return listOf(
            "Your data is safe with me. I never send it to the cloud.",
            "I use your height and age to set your metabolic floor.",
            "Need to change your goal? I'll handle the calorie math automatically.",
            "I'm your little privacy-first helper. No cloud, no tracking."
        ).random()
    }
}
