package com.eliteonetube.momentum.data

import android.content.Context
import androidx.room.TypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

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
    version = 33
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
                val instance = Room.databaseBuilder(
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
                    MIGRATION_30_31, MIGRATION_31_32,
                    MIGRATION_32_33
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
