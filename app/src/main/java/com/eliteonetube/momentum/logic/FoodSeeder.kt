package com.eliteonetube.momentum.logic

import android.content.Context
import com.eliteonetube.momentum.data.FoodDao
import com.eliteonetube.momentum.data.FoodItem
import org.json.JSONArray

object FoodSeeder {
    suspend fun seedIfNeeded(context: Context, foodDao: FoodDao) {
        if (foodDao.foodItemCount() > 0) return

        val jsonText = try {
            context.assets.open("foods.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return
        }
        val jsonArray = JSONArray(jsonText)

        val foodItems = (0 until jsonArray.length()).map { index ->
            val obj = jsonArray.getJSONObject(index)
            FoodItem(
                name = obj.getString("name"),
                calories = obj.getDouble("calories"),
                protein = obj.getDouble("protein"),
                fat = obj.getDouble("fat"),
                carbs = obj.getDouble("carbs"),
                servingSize = obj.optString("servingSize", "100g"),
                servingAmount = obj.optDouble("servingAmount", 100.0),
                servingUnit = obj.optString("servingUnit", "g"),
                isCustom = false,
                barcode = if (obj.has("barcode")) obj.getString("barcode") else null
            )
        }

        foodItems.forEach { foodDao.insertFoodItem(it) }
    }
}
