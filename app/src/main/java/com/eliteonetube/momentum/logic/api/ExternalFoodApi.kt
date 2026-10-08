package com.eliteonetube.momentum.logic.api

import com.eliteonetube.momentum.data.FoodItem
import okhttp3.HttpUrl
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
                
                val body = response.body.string()
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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches Open Food Facts by name, e.g. "greek yogurt". Returns foods per 100 g, or null if
     * the service couldn't be reached (so the UI can tell "offline" apart from "no matches").
     *
     * Uses search.openfoodfacts.org: the older /cgi/search.pl endpoint is unreliable and the
     * /api/v2/search endpoint doesn't support text search.
     */
    suspend fun search(query: String, limit: Int = 20): List<FoodItem>? = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("search.openfoodfacts.org")
            .addPathSegment("search")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page_size", limit.toString())
            .addQueryParameter("fields", "code,product_name,brands,nutriments")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Momentum - Android - https://github.com/nhaskaris/momentum/issues")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val hits = JSONObject(response.body.string()).optJSONArray("hits") ?: return@withContext emptyList()
                (0 until hits.length()).mapNotNull { i ->
                    val product = hits.optJSONObject(i) ?: return@mapNotNull null
                    val name = product.optString("product_name").trim()
                    val nutrients = product.optJSONObject("nutriments") ?: return@mapNotNull null
                    val calories = nutrients.optDouble("energy-kcal_100g", Double.NaN)
                    // Skip entries without a name or calories — they can't be logged meaningfully
                    if (name.isEmpty() || calories.isNaN() || calories < 0) return@mapNotNull null
                    val brand = product.optJSONArray("brands")?.optString(0)?.trim().orEmpty()
                    FoodItem(
                        name = if (brand.isNotEmpty() && !name.contains(brand, ignoreCase = true)) "$name ($brand)" else name,
                        calories = calories,
                        protein = nutrients.optDouble("proteins_100g", 0.0).coerceAtLeast(0.0),
                        fat = nutrients.optDouble("fat_100g", 0.0).coerceAtLeast(0.0),
                        carbs = nutrients.optDouble("carbohydrates_100g", 0.0).coerceAtLeast(0.0),
                        servingSize = "100g",
                        servingAmount = 100.0,
                        servingUnit = "g",
                        barcode = product.optString("code").takeIf { it.isNotBlank() },
                        isCustom = true
                    )
                }.distinctBy { it.barcode ?: it.name }
            }
        } catch (_: Exception) {
            null
        }
    }
}
