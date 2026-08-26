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
import androidx.room3.Transaction
import android.content.Context
import kotlinx.coroutines.flow.Flow

enum class Goal { CUT, BULK, MAINTAIN, REVERSE }

enum class UnitSystem { METRIC, IMPERIAL }

enum class AppTheme { LIGHT, DARK, SYSTEM }

enum class ExerciseType { STRENGTH, CARDIO }

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

    @TypeConverter
    fun fromExerciseType(type: ExerciseType): String = type.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)
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
    val theme: AppTheme = AppTheme.SYSTEM,
    val useExternalApi: Boolean = false,
    val activeWorkoutTemplateId: Long? = null,
    val hasActiveWorkout: Boolean = false,
    val remindersEnabled: Boolean = true,
    val morningReminderTime: String = "08:30",
    val eveningReminderTime: String = "20:00"
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

@Entity(tableName = "food_item_table")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingSize: String? = "100g",
    val servingAmount: Double = 100.0,
    val servingUnit: String = "g",
    val isCustom: Boolean = false,
    val barcode: String? = null
)

@Entity(
    tableName = "daily_food_log_table",
    foreignKeys = [
        ForeignKey(
            entity = FoodItem::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("foodItemId"), Index("date")]
)
data class DailyFoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val foodItemId: Long,
    val quantity: Double
)

@Entity(tableName = "daily_meal_log_table")
data class DailyMealLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealId: Long,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

data class FoodLogWithItem(
    val id: Long,
    val date: String = "", 
    val foodItemId: Long,
    val quantity: Double,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingAmount: Double = 100.0,
    val servingUnit: String = "g",
    val isMeal: Boolean = false
)

data class DailyNutrition(
    val date: String,
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double
)

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
    val orderIndex: Int = 0
)

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightEntry)

    @Query("SELECT * FROM weight_table ORDER BY date DESC LIMIT 14")
    fun getLastTwoWeeks(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_table ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeightEntry>>

    @Query("SELECT EXISTS(SELECT 1 FROM weight_table WHERE date = :date)")
    suspend fun hasWeightForDate(date: String): Boolean

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

@Entity(tableName = "meal_table")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String? = null
)

@Entity(
    tableName = "meal_food_item_table",
    foreignKeys = [
        ForeignKey(
            entity = Meal::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FoodItem::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mealId"), Index("foodItemId")]
)
data class MealFoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodItemId: Long,
    val quantity: Double
)

data class MealWithItems(
    val meal: Meal,
    val items: List<FoodLogWithItem> // Reuse FoodLogWithItem or similar structure
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

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(foodItem: FoodItem): Long

    @Query("SELECT * FROM food_item_table ORDER BY name ASC")
    fun getAllFoodItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_item_table WHERE name LIKE '%' || :query || '%'")
    fun searchFoodItems(query: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_item_table WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodItemByBarcode(barcode: String): FoodItem?

    @Insert
    suspend fun insertFoodLog(log: DailyFoodLog)

    @Update
    suspend fun updateFoodLog(log: DailyFoodLog)

    @Query("""
        SELECT daily_food_log_table.*, food_item_table.name, food_item_table.calories, 
               food_item_table.protein, food_item_table.fat, food_item_table.carbs,
               food_item_table.servingAmount, food_item_table.servingUnit
        FROM daily_food_log_table
        INNER JOIN food_item_table ON daily_food_log_table.foodItemId = food_item_table.id
        WHERE daily_food_log_table.date = :date
    """)
    fun getFoodLogsForDate(date: String): Flow<List<FoodLogWithItem>>

    @Query("DELETE FROM daily_food_log_table WHERE id = :logId")
    suspend fun deleteFoodLog(logId: Long)

    @Query("SELECT COUNT(*) FROM food_item_table")
    suspend fun foodItemCount(): Int

    // --- MEAL DAO QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealFoodItem(mealFoodItem: MealFoodItem)

    @Query("SELECT * FROM meal_table ORDER BY name ASC")
    fun getAllMeals(): Flow<List<Meal>>

    @Query("""
        SELECT meal_food_item_table.*, food_item_table.name, food_item_table.calories, 
               food_item_table.protein, food_item_table.fat, food_item_table.carbs,
               food_item_table.servingAmount, food_item_table.servingUnit
        FROM meal_food_item_table
        INNER JOIN food_item_table ON meal_food_item_table.foodItemId = food_item_table.id
        WHERE meal_food_item_table.mealId = :mealId
    """)
    fun getItemsForMeal(mealId: Long): Flow<List<FoodLogWithItem>>

    @Query("DELETE FROM meal_table WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    // --- DAILY MEAL LOG QUERIES ---

    @Insert
    suspend fun insertDailyMealLog(log: DailyMealLog)

    @Query("SELECT * FROM daily_meal_log_table WHERE date = :date")
    fun getDailyMealLogsForDate(date: String): Flow<List<DailyMealLog>>

    @Query("""
        SELECT daily_food_log_table.*, food_item_table.name, food_item_table.calories, 
               food_item_table.protein, food_item_table.fat, food_item_table.carbs,
               food_item_table.servingAmount, food_item_table.servingUnit
        FROM daily_food_log_table
        INNER JOIN food_item_table ON daily_food_log_table.foodItemId = food_item_table.id
        WHERE daily_food_log_table.date >= :startDate
    """)
    fun getRecentFoodLogs(startDate: String): Flow<List<FoodLogWithItem>>

    @Query("SELECT * FROM daily_meal_log_table WHERE date >= :startDate")
    fun getRecentMealLogs(startDate: String): Flow<List<DailyMealLog>>

    @Query("DELETE FROM daily_meal_log_table WHERE id = :logId")
    suspend fun deleteDailyMealLog(logId: Long)
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

@Database(
    entities = [
        WeightEntry::class,
        UserProfile::class,
        Exercise::class,
        WorkoutTemplate::class,
        TemplateExercise::class,
        WorkoutSession::class,
        LoggedSet::class,
        CheckIn::class,
        FoodItem::class,
        DailyFoodLog::class,
        ActiveWorkoutSet::class,
        Meal::class,
        MealFoodItem::class,
        DailyMealLog::class,
        TemplateSet::class
    ],
    version = 31
)
@TypeConverters(Converters::class)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile
        private var INSTANCE: WeightDatabase? = null

        fun getInstance(context: Context): WeightDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room3.Room.databaseBuilder(
                    context.applicationContext,
                    WeightDatabase::class.java,
                    "weight_tracker_db"
                ).addMigrations(
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                    MIGRATION_24_25, MIGRATION_25_26,
                    MIGRATION_26_27, MIGRATION_27_28,
                    MIGRATION_28_29, MIGRATION_29_30,
                    MIGRATION_30_31
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }

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

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_item_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        calories REAL NOT NULL,
                        protein REAL NOT NULL,
                        fat REAL NOT NULL,
                        carbs REAL NOT NULL,
                        servingSize TEXT,
                        isCustom INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_food_log_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        foodItemId INTEGER NOT NULL,
                        quantity REAL NOT NULL,
                        FOREIGN KEY(foodItemId) REFERENCES food_item_table(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_daily_food_log_table_foodItemId ON daily_food_log_table (foodItemId)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_daily_food_log_table_date ON daily_food_log_table (date)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE food_item_table ADD COLUMN barcode TEXT")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN useExternalApi INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN activeWorkoutTemplateId INTEGER")
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS active_workout_set_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        setNumber INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        reps INTEGER NOT NULL,
                        notes TEXT,
                        isCompleted INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN hasActiveWorkout INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE exercise_table ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'STRENGTH'")
                connection.execSQL("ALTER TABLE logged_set_table ADD COLUMN durationSeconds INTEGER")
                connection.execSQL("ALTER TABLE logged_set_table ADD COLUMN distanceKm REAL")
                connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN durationSeconds INTEGER")
                connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN distanceKm REAL")
                connection.execSQL("ALTER TABLE template_exercise_table ADD COLUMN targetDurationSeconds INTEGER")
                connection.execSQL("ALTER TABLE template_exercise_table ADD COLUMN targetDistanceKm REAL")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN remindersEnabled INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN morningReminderTime TEXT NOT NULL DEFAULT '08:30'")
                connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN eveningReminderTime TEXT NOT NULL DEFAULT '20:00'")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        notes TEXT
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_food_item_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mealId INTEGER NOT NULL,
                        foodItemId INTEGER NOT NULL,
                        quantity REAL NOT NULL,
                        FOREIGN KEY(mealId) REFERENCES meal_table(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(foodItemId) REFERENCES food_item_table(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_meal_food_item_table_mealId ON meal_food_item_table (mealId)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_meal_food_item_table_foodItemId ON meal_food_item_table (foodItemId)")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_meal_log_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        mealId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        calories REAL NOT NULL,
                        protein REAL NOT NULL,
                        fat REAL NOT NULL,
                        carbs REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS template_set_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateExerciseId INTEGER NOT NULL,
                        setNumber INTEGER NOT NULL,
                        targetReps INTEGER NOT NULL,
                        targetWeightKg REAL NOT NULL,
                        targetDurationSeconds INTEGER,
                        targetDistanceKm REAL,
                        FOREIGN KEY(templateExerciseId) REFERENCES template_exercise_table(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_template_set_table_templateExerciseId ON template_set_table (templateExerciseId)")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE food_item_table ADD COLUMN servingAmount REAL NOT NULL DEFAULT 100.0")
                connection.execSQL("ALTER TABLE food_item_table ADD COLUMN servingUnit TEXT NOT NULL DEFAULT 'g'")
            }
        }
    }
}
