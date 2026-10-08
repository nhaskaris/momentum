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

    @TypeConverter
    fun fromMealSlot(slot: MealSlot): String = slot.name

    // Unknown values (e.g. from a newer app version) fall back instead of crashing
    @TypeConverter
    fun toMealSlot(value: String): MealSlot = MealSlot.entries.firstOrNull { it.name == value } ?: MealSlot.UNCATEGORIZED
}
