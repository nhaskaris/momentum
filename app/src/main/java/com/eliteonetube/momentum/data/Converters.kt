package com.eliteonetube.momentum.data

import androidx.room.TypeConverter

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
