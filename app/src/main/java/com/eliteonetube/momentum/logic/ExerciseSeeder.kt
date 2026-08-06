package com.eliteonetube.momentum.logic

import android.content.Context
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.WorkoutDao
import org.json.JSONArray

object ExerciseSeeder {
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
            Exercise(name = name, muscleGroup = muscleGroup)
        }

        workoutDao.insertExercisesIfNotPresent(exercises)
    }
}