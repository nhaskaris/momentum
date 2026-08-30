package com.eliteonetube.momentum.logic

import android.content.Context
import com.eliteonetube.momentum.data.FoodDao
import com.eliteonetube.momentum.data.FoodItem
import org.json.JSONObject
import androidx.core.content.edit

object FoodSeeder {
    private const val PREFS_NAME = "food_seeder_prefs"
    private const val KEY_SEEDED_VERSION = "seeded_version"

    suspend fun seedIfNeeded(context: Context, foodDao: FoodDao) {
        val jsonText = try {
            context.assets.open("foods.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return
        }
        
        val jsonRoot = JSONObject(jsonText)
        val newVersion = jsonRoot.optInt("version", 1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_SEEDED_VERSION, 0)

        // Only run if version has increased
        if (newVersion <= currentVersion) return

        val jsonArray = jsonRoot.getJSONArray("foods")
        val existingNames = foodDao.getOfficialFoodNames().toSet()

        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val name = obj.getString("name")
            
            if (!existingNames.contains(name)) {
                val foodItem = FoodItem(
                    name = name,
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
                foodDao.insertFoodItem(foodItem)
            }
        }
        
        // Update the seeded version in prefs
        prefs.edit { putInt(KEY_SEEDED_VERSION, newVersion) }
    }
}
