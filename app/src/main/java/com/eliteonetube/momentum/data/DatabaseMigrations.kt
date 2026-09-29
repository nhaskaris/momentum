package com.eliteonetube.momentum.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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

val MIGRATION_31_32 = object : Migration(31, 32) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN targetWeightKg REAL")
        connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN targetReps INTEGER")
        connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN targetDurationSeconds INTEGER")
        connection.execSQL("ALTER TABLE active_workout_set_table ADD COLUMN targetDistanceKm REAL")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE user_profile_table ADD COLUMN activeWorkoutStartTime INTEGER")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE check_in_table ADD COLUMN bodyFatPercentage REAL")
    }
}
