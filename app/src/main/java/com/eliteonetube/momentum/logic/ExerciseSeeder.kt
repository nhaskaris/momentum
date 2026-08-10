package com.eliteonetube.momentum.logic

import android.content.Context
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.ExerciseType
import com.eliteonetube.momentum.data.WorkoutDao
import org.json.JSONArray

object ExerciseSeeder {
    private val cardioExerciseNames = setOf(
        "Bicycling", "Bicycling, Stationary", "Elliptical Trainer", 
        "Jogging, Treadmill", "Rowing, Stationary", "Running, Treadmill", 
        "Stairmaster", "Step Mill", "Walking, Treadmill", "Rope Jumping",
        "Fast Skipping", "Wind Sprints", "Trail Running/Walking"
    )

    suspend fun seedIfNeeded(context: Context, workoutDao: WorkoutDao) {
        if (workoutDao.exerciseCount() > 0) return

        val jsonText = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonText)

        val exercises = (0 until jsonArray.length()).map { index ->
            val obj = jsonArray.getJSONObject(index)
            val name = obj.getString("name")
            val primaryMuscles = obj.getJSONArray("primaryMuscles")
            val muscleGroup = if (primaryMuscles.length() > 0) {
                primaryMuscles.getString(0).replaceFirstChar { it.uppercase() }
            } else {
                "Other"
            }
            
            val type = if (cardioExerciseNames.contains(name)) ExerciseType.CARDIO else ExerciseType.STRENGTH
            
            Exercise(name = name, muscleGroup = muscleGroup, exerciseType = type)
        }

        workoutDao.insertExercisesIfNotPresent(exercises)
    }
}
