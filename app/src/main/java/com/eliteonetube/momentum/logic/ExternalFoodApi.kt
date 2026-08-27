package com.eliteonetube.momentum.logic

import com.eliteonetube.momentum.data.FoodItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExternalFoodApi {
    private val client = OkHttpClient()

    /**
     * Fetches food data from Open Food Facts API by barcode.
     * Returns a FoodItem if found, or null otherwise.
     */
    suspend fun fetchByBarcode(barcode: String): FoodItem? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Momentum - Android - https://github.com/nhaskaris/momentum/issues")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                
                if (json.getInt("status") != 1) return@withContext null
                
                val product = json.getJSONObject("product")
                val name = product.optString("product_name", "Unknown Product")
                val nutrients = product.optJSONObject("nutriments") ?: return@withContext null
                
                // Nutrients in OFF are typically per 100g
                val calories = nutrients.optDouble("energy-kcal_100g", nutrients.optDouble("energy-kcal_value", 0.0))
                val protein = nutrients.optDouble("proteins_100g", 0.0)
                val fat = nutrients.optDouble("fat_100g", 0.0)
                val carbs = nutrients.optDouble("carbohydrates_100g", 0.0)
                
                return@withContext FoodItem(
                    name = name,
                    calories = calories,
                    protein = protein,
                    fat = fat,
                    carbs = carbs,
                    servingSize = "100g",
                    servingAmount = 100.0,
                    servingUnit = "g",
                    barcode = barcode,
                    isCustom = true
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
