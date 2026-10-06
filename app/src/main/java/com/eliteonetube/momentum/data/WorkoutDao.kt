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

    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("SELECT * FROM workout_session_table ORDER BY date DESC LIMIT 30")
    fun getRecentSessions(): Flow<List<WorkoutSession>>

    /** Every session, for the full history screen. */
    @Query("SELECT * FROM workout_session_table ORDER BY date DESC, id DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("""
        SELECT t.id AS templateId,
               (SELECT COUNT(*) FROM template_exercise_table te WHERE te.templateId = t.id) AS exerciseCount,
               (SELECT MAX(s.date) FROM workout_session_table s WHERE s.templateId = t.id) AS lastDone
        FROM workout_template_table t
    """)
    fun getTemplateSummaries(): Flow<List<TemplateSummary>>

    // @Update, not REPLACE: a REPLACE deletes the row first, which would cascade-delete the routine's
    // exercises and null out templateId on every session logged from it.
    @Update
    suspend fun updateTemplate(template: WorkoutTemplate)

    @Query("""
        SELECT COUNT(*) AS sessions,
               COALESCE(SUM(setCount), 0) AS sets,
               COALESCE(SUM(totalVolumeKg), 0.0) AS volumeKg,
               COALESCE(SUM(CASE WHEN date > :since THEN 1 ELSE 0 END), 0) AS sessionsLast30Days
        FROM workout_session_table
    """)
    fun getWorkoutTotals(since: String): Flow<WorkoutTotals>

    @Query("SELECT * FROM workout_session_table ORDER BY totalVolumeKg DESC LIMIT 1")
    fun getHeaviestSession(): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_session_table ORDER BY exerciseCount DESC, date DESC LIMIT 1")
    fun getMostVariedSession(): Flow<WorkoutSession?>

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

    @Query("SELECT * FROM template_exercise_table WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getExercisesForTemplateOnce(templateId: Long): List<TemplateExercise>

    /** All sets of a routine in one query, instead of one query per exercise. */
    @Query("SELECT * FROM template_set_table WHERE templateExerciseId IN (SELECT id FROM template_exercise_table WHERE templateId = :templateId) ORDER BY templateExerciseId ASC, setNumber ASC")
    suspend fun getSetsForTemplate(templateId: Long): List<TemplateSet>

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
}
