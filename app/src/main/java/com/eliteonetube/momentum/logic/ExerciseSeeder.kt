package com.eliteonetube.momentum.logic

import android.content.Context
import com.eliteonetube.momentum.data.Exercise
import com.eliteonetube.momentum.data.ExerciseType
import com.eliteonetube.momentum.data.WorkoutDao

import org.json.JSONObject
import androidx.core.content.edit

object ExerciseSeeder {
    private const val PREFS_NAME = "exercise_seeder_prefs"
    private const val KEY_SEEDED_VERSION = "seeded_version"

    private val cardioExerciseNames = setOf(
        "Bicycling", "Bicycling, Stationary", "Elliptical Trainer", 
        "Jogging, Treadmill", "Rowing, Stationary", "Running, Treadmill", 
        "Stairmaster", "Step Mill", "Walking, Treadmill", "Rope Jumping",
        "Fast Skipping", "Wind Sprints", "Trail Running/Walking"
    )

    suspend fun seedIfNeeded(context: Context, workoutDao: WorkoutDao) {
        val jsonText = try {
            context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return
        }
        
        val jsonRoot = JSONObject(jsonText)
        val newVersion = jsonRoot.optInt("version", 1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_SEEDED_VERSION, 0)

        // Only run if version has increased
        if (newVersion <= currentVersion) return

        val jsonArray = jsonRoot.getJSONArray("exercises")
        val existingNames = workoutDao.getExerciseNames().toSet()

        val exercisesToInsert = mutableListOf<Exercise>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val name = obj.getString("name")
            
            if (!existingNames.contains(name)) {
                val primaryMuscles = obj.getJSONArray("primaryMuscles")
                val muscleGroup = if (primaryMuscles.length() > 0) {
                    primaryMuscles.getString(0).replaceFirstChar { it.uppercase() }
                } else {
                    "Other"
                }
                
                val type = if (cardioExerciseNames.contains(name)) ExerciseType.CARDIO else ExerciseType.STRENGTH
                exercisesToInsert.add(Exercise(name = name, muscleGroup = muscleGroup, exerciseType = type))
            }
        }

        if (exercisesToInsert.isNotEmpty()) {
            workoutDao.insertExercisesIfNotPresent(exercisesToInsert)
        }
        
        // Update the seeded version in prefs
        prefs.edit { putInt(KEY_SEEDED_VERSION, newVersion) }
    }
}
