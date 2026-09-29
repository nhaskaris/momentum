package com.eliteonetube.momentum.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
