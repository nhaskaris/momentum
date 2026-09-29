package com.eliteonetube.momentum.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
    val activeWorkoutStartTime: Long? = null,
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
