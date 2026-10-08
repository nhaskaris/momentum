package com.eliteonetube.momentum.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    // Plain insert, not REPLACE: a REPLACE deletes the old row first, and the CASCADE foreign keys
    // would then delete every log and meal ingredient using that food. Edits go through updateFoodItem.
    @Insert
    suspend fun insertFoodItem(foodItem: FoodItem): Long

    @Update
    suspend fun updateFoodItem(foodItem: FoodItem)

    /** Most recently logged foods first, for the Add Food list. */
    @Query("""
        SELECT foodItemId FROM daily_food_log_table
        GROUP BY foodItemId
        ORDER BY MAX(date) DESC, MAX(id) DESC
        LIMIT :limit
    """)
    fun getRecentFoodIds(limit: Int = 15): Flow<List<Long>>

    @Query("SELECT name FROM food_item_table WHERE isCustom = 0")
    suspend fun getOfficialFoodNames(): List<String>

    @Query("SELECT * FROM food_item_table ORDER BY name ASC")
    fun getAllFoodItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_item_table WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodItemByBarcode(barcode: String): FoodItem?

    @Insert
    suspend fun insertFoodLog(log: DailyFoodLog)


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

    // Targeted updates: rebuilding the whole row used to reset its date to today
    @Query("UPDATE daily_food_log_table SET quantity = :quantity WHERE id = :logId")
    suspend fun updateFoodLogQuantity(logId: Long, quantity: Double)

    @Query("UPDATE daily_food_log_table SET mealSlot = :slot WHERE id = :logId")
    suspend fun setFoodLogSlot(logId: Long, slot: MealSlot)

    @Query("UPDATE daily_meal_log_table SET mealSlot = :slot WHERE id = :logId")
    suspend fun setMealLogSlot(logId: Long, slot: MealSlot)

    @Query("SELECT * FROM daily_food_log_table WHERE date = :date AND mealSlot = :slot")
    suspend fun getFoodLogRows(date: String, slot: MealSlot): List<DailyFoodLog>

    @Query("SELECT * FROM daily_meal_log_table WHERE date = :date AND mealSlot = :slot")
    suspend fun getMealLogRows(date: String, slot: MealSlot): List<DailyMealLog>

    /** Copies one section of a day into another day's same section. Returns how many entries were copied. */
    @Transaction
    suspend fun copySection(fromDate: String, toDate: String, slot: MealSlot): Int {
        val foods = getFoodLogRows(fromDate, slot)
        val meals = getMealLogRows(fromDate, slot)
        foods.forEach { insertFoodLog(it.copy(id = 0, date = toDate)) }
        meals.forEach { insertDailyMealLog(it.copy(id = 0, date = toDate)) }
        return foods.size + meals.size
    }

    // --- MEAL DAO QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealFoodItem(mealFoodItem: MealFoodItem)

    @Query("SELECT * FROM meal_table ORDER BY name ASC")
    fun getAllMeals(): Flow<List<Meal>>

    @Query("""
        SELECT meal_food_item_table.id, meal_food_item_table.foodItemId, meal_food_item_table.quantity,
               food_item_table.name, food_item_table.calories, 
               food_item_table.protein, food_item_table.fat, food_item_table.carbs,
               food_item_table.servingAmount, food_item_table.servingUnit
        FROM meal_food_item_table
        INNER JOIN food_item_table ON meal_food_item_table.foodItemId = food_item_table.id
        WHERE meal_food_item_table.mealId = :mealId
    """)
    fun getItemsForMeal(mealId: Long): Flow<List<FoodLogWithItem>>

    @Query("DELETE FROM meal_table WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    // @Update, not REPLACE, for the same cascade reason as foods
    @Update
    suspend fun updateMeal(meal: Meal)

    @Query("DELETE FROM meal_food_item_table WHERE mealId = :mealId")
    suspend fun deleteMealFoodItems(mealId: Long)

    /** Renames a meal and swaps its ingredients in one transaction. Past meal logs are snapshots and stay as they were. */
    @Transaction
    suspend fun updateMealContents(meal: Meal, items: List<MealFoodItem>) {
        updateMeal(meal)
        deleteMealFoodItems(meal.id)
        items.forEach { insertMealFoodItem(it.copy(id = 0, mealId = meal.id)) }
    }

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
