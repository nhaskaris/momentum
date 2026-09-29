package com.eliteonetube.momentum.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(foodItem: FoodItem): Long

    @Query("SELECT name FROM food_item_table WHERE isCustom = 0")
    suspend fun getOfficialFoodNames(): List<String>

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
