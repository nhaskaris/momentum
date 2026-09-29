package com.eliteonetube.momentum.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercisesIfNotPresent(exercises: List<Exercise>)

    @Query("SELECT * FROM exercise_table ORDER BY name ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise_table WHERE muscleGroup = :muscleGroup ORDER BY name ASC")
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<Exercise>>

    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("SELECT * FROM workout_session_table ORDER BY date DESC LIMIT 30")
    fun getRecentSessions(): Flow<List<WorkoutSession>>

    @Query("DELETE FROM workout_session_table WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM logged_set_table WHERE sessionId = :sessionId")
    suspend fun deleteSetsBySessionId(sessionId: Long)

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Insert
    suspend fun insertSet(set: LoggedSet)

    @Query("SELECT * FROM logged_set_table WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<LoggedSet>>

    @Query("SELECT * FROM logged_set_table WHERE exerciseId = :exerciseId ORDER BY id DESC LIMIT 20")
    suspend fun getSetsForExercise(exerciseId: Long): List<LoggedSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveSet(activeSet: ActiveWorkoutSet)

    @Query("SELECT * FROM active_workout_set_table ORDER BY orderIndex ASC, id ASC")
    fun getActiveSets(): Flow<List<ActiveWorkoutSet>>

    @Query("DELETE FROM active_workout_set_table")
    suspend fun clearActiveSets()

    @Query("DELETE FROM active_workout_set_table WHERE exerciseId = :exerciseId AND setNumber = :setNumber")
    suspend fun deleteActiveSet(exerciseId: Long, setNumber: Int)

    @Update
    suspend fun updateActiveSet(activeSet: ActiveWorkoutSet)

    @Query("DELETE FROM active_workout_set_table WHERE exerciseId = :exerciseId")
    suspend fun deleteActiveSetsForExercise(exerciseId: Long)

    @Query("DELETE FROM active_workout_set_table WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    @Transaction
    suspend fun replaceActiveSets(newSets: List<ActiveWorkoutSet>) {
        clearActiveSets()
        newSets.forEach { insertActiveSet(it) }
    }

    @Query("SELECT COUNT(*) FROM exercise_table")
    suspend fun exerciseCount(): Int

    @Query("SELECT name FROM exercise_table")
    suspend fun getExerciseNames(): List<String>

    // --- TEMPLATE DAO QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercise(templateExercise: TemplateExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateSet(templateSet: TemplateSet): Long

    @Query("SELECT * FROM template_set_table WHERE templateExerciseId = :templateExerciseId ORDER BY setNumber ASC")
    suspend fun getSetsForTemplateExercise(templateExerciseId: Long): List<TemplateSet>

    @Query("DELETE FROM template_set_table WHERE templateExerciseId IN (SELECT id FROM template_exercise_table WHERE templateId = :templateId)")
    suspend fun deleteTemplateSetsByTemplateId(templateId: Long)

    /** Replaces a routine's exercises and sets atomically, so a crash can't leave it half-empty. */
    @Transaction
    suspend fun replaceTemplateContents(templateId: Long, exercises: List<Pair<TemplateExercise, List<TemplateSet>>>) {
        deleteTemplateSetsByTemplateId(templateId)
        deleteTemplateExercises(templateId)
        exercises.forEach { (exercise, sets) ->
            val teid = insertTemplateExercise(exercise)
            sets.forEach { insertTemplateSet(it.copy(templateExerciseId = teid)) }
        }
    }

    @Query("SELECT * FROM workout_template_table ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM template_exercise_table WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun getExercisesForTemplate(templateId: Long): Flow<List<TemplateExercise>>

    @Query("DELETE FROM workout_template_table WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Long)

    @Query("DELETE FROM template_exercise_table WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: Long)

    @Query("""
        UPDATE template_exercise_table 
        SET targetSets = :newSets, targetReps = :newReps, targetWeightKg = :newWeight,
            targetDurationSeconds = :newDuration, targetDistanceKm = :newDistance
        WHERE templateId = :templateId AND exerciseId = :exerciseId
    """)
    suspend fun updateTemplateExerciseTargets(
        templateId: Long,
        exerciseId: Long,
        newSets: Int,
        newReps: Int,
        newWeight: Double,
        newDuration: Int?,
        newDistance: Double?
    )

    // --- PROGRESSION & PAST HISTORY QUERIES ---

    /**
     * Gets previous sets logged for a specific exercise across all past sessions,
     * ordered by session date and set ID descending to quickly pull latest performance metrics.
     */
    @Query("""
        SELECT logged_set_table.* FROM logged_set_table
        INNER JOIN workout_session_table ON logged_set_table.sessionId = workout_session_table.id
        WHERE logged_set_table.exerciseId = :exerciseId
        ORDER BY workout_session_table.date DESC, logged_set_table.id ASC
    """)
    fun getHistoryForExercise(exerciseId: Long): Flow<List<LoggedSet>>

    /**
     * Finds the maximum weight lifted for an exercise to help calculate personal records (PRs).
     */
    @Query("SELECT MAX(weightKg) FROM logged_set_table WHERE exerciseId = :exerciseId")
    fun getMaxWeightForExercise(exerciseId: Long): Flow<Double?>

    @Query("""
        SELECT DISTINCT exercise_table.* FROM exercise_table
        INNER JOIN logged_set_table ON exercise_table.id = logged_set_table.exerciseId
        WHERE logged_set_table.sessionId = :sessionId
    """)
    suspend fun getExercisesForSessionOnce(sessionId: Long): List<Exercise>

    @Query("SELECT * FROM logged_set_table WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getSetsForSessionOnce(sessionId: Long): List<LoggedSet>
}
