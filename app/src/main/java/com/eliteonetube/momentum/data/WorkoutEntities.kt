package com.eliteonetube.momentum.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "exercise_table")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val exerciseType: ExerciseType = ExerciseType.STRENGTH
) {
    // Convenience getter so UI calling exercise.targetMuscleGroup or exercise.category works directly
    val targetMuscleGroup: String get() = muscleGroup
}

@Entity(tableName = "workout_template_table")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // e.g., "Push Day A", "Upper Body"
    val notes: String? = null
)

@Entity(
    tableName = "template_exercise_table",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0,
    val orderIndex: Int = 0,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Double? = null
)

@Entity(
    tableName = "workout_session_table",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("templateId"), Index("date")]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // ISO yyyy-MM-dd, same format as WeightEntry.date
    val notes: String? = null,
    val templateId: Long? = null, // Optional reference to the template used
    val totalVolumeKg: Double = 0.0,
    val exerciseCount: Int = 0,
    val setCount: Int = 0
)

@Entity(
    tableName = "logged_set_table",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class LoggedSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val notes: String? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null
)

@Entity(tableName = "active_workout_set_table")
data class ActiveWorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Double? = null,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "template_set_table",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExercise::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateExerciseId")]
)
data class TemplateSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateExerciseId: Long,
    val setNumber: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Double? = null
)
