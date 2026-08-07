package com.eliteonetube.momentum.data

import androidx.room3.Database
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

enum class Goal { CUT, BULK, MAINTAIN, REVERSE }

enum class UnitSystem { METRIC, IMPERIAL }

enum class AppTheme { LIGHT, DARK, SYSTEM }

class Converters {
    @TypeConverter
    fun fromGoal(goal: Goal): String = goal.name

    @TypeConverter
    fun toGoal(value: String): Goal = Goal.valueOf(value)

    @TypeConverter
    fun fromUnitSystem(system: UnitSystem): String = system.name

    @TypeConverter
    fun toUnitSystem(value: String): UnitSystem = UnitSystem.valueOf(value)

    @TypeConverter
    fun fromAppTheme(theme: AppTheme): String = theme.name

    @TypeConverter
    fun toAppTheme(value: String): AppTheme = AppTheme.valueOf(value)
}

@Entity(tableName = "weight_table")
data class WeightEntry(
    @PrimaryKey val date: String,
    val weight: Double,
    val calorieTargetAtEntry: Int? = null
)

@Entity(tableName = "user_profile_table")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val height: Double,
    val age: Int,
    val isMale: Boolean,
    val averageDailySteps: Int,
    val estimatedMaintenanceCalories: Int,
    val goal: Goal,
    val currentCalorieTarget: Int,
    val pendingCalorieTarget: Int? = null,
    val pendingAdjustmentReason: String? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val bodyFatPercentage: Double? = null,
    val useHealthConnect: Boolean = false,
    val lastCheckInDate: String? = null,
    val checkInDue: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM
)

@Entity(tableName = "check_in_table")
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weight: Double,
    val frontPhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val sidePhotoPath: String? = null,
    val calorieTargetBefore: Int,
    val calorieTargetAfter: Int,
    val adjustmentReason: String
)

@Entity(tableName = "exercise_table")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String
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
    val orderIndex: Int = 0
)

// --- LOGGING ENTITIES ---

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
    val notes: String? = null
)

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightEntry)

    @Query("SELECT * FROM weight_table ORDER BY date DESC LIMIT 14")
    fun getLastTwoWeeks(): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profile_table WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("DELETE FROM weight_table WHERE date = :date")
    suspend fun deleteWeight(date: String)

    @Query("SELECT date FROM weight_table ORDER BY date ASC")
    fun getAllWeightDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckIn)

    @Query("SELECT * FROM check_in_table ORDER BY date DESC")
    fun getAllCheckIns(): Flow<List<CheckIn>>
}

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

    @Query("DELETE FROM logged_set_table WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    @Query("SELECT COUNT(*) FROM exercise_table")
    suspend fun exerciseCount(): Int

    // --- TEMPLATE DAO QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercise(templateExercise: TemplateExercise): Long

    @Query("SELECT * FROM workout_template_table ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM template_exercise_table WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun getExercisesForTemplate(templateId: Long): Flow<List<TemplateExercise>>

    @Query("DELETE FROM workout_template_table WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Long)

    @Query("""
        UPDATE template_exercise_table 
        SET targetSets = :newSets, targetReps = :newReps, targetWeightKg = :newWeight
        WHERE templateId = :templateId AND exerciseId = :exerciseId
    """)
    suspend fun updateTemplateExerciseTargets(
        templateId: Long,
        exerciseId: Long,
        newSets: Int,
        newReps: Int,
        newWeight: Double
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

@Database(
    entities = [
        WeightEntry::class,
        UserProfile::class,
        Exercise::class,
        WorkoutTemplate::class,
        TemplateExercise::class,
        WorkoutSession::class,
        LoggedSet::class,
        CheckIn::class
    ],
    version = 19
)
@TypeConverters(Converters::class)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE logged_set_table ADD COLUMN notes TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE workout_session_table ADD COLUMN totalVolumeKg REAL NOT NULL DEFAULT 0.0")
                connection.execSQL("ALTER TABLE workout_session_table ADD COLUMN exerciseCount INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE workout_session_table ADD COLUMN setCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_session_table_date ON workout_session_table(date)"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN bodyFatPercentage REAL")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN useHealthConnect INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN lastCheckInDate TEXT")
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN checkInDue INTEGER NOT NULL DEFAULT 0")
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS check_in_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        weight REAL NOT NULL,
                        frontPhotoPath TEXT,
                        backPhotoPath TEXT,
                        sidePhotoPath TEXT,
                        calorieTargetBefore INTEGER NOT NULL,
                        calorieTargetAfter INTEGER NOT NULL,
                        adjustmentReason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN theme TEXT NOT NULL DEFAULT 'SYSTEM'")
            }
        }
    }
}
