package com.eliteonetube.momentum.logic

import android.util.Log
import com.google.mlkit.vision.text.Text

data class ScannedNutrition(
    val name: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null
)

/**
 * High-precision spatial nutrition parser.
 */
object NutritionScanner {

    private val numberRegex = Regex("""(\d+(?:[.,]\d+)?)""")
    private val kcalTagRegex = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(?:kcal|kal|cal|keal|kca)\b""")

    private const val KJ_TO_KCAL_RATIO = 4.184
    private const val RATIO_TOLERANCE = 0.3 // Increased tolerance for OCR errors

    private val caloriesKeyword = Regex("""(?i)(?:Total\s*)?Calories|Energy|Energija|Ενέργεια|Θερμίδες|Evépyeia""")
    private val proteinKeyword = Regex("""(?i)(?:Total\s*)?Protein|Prot\.|Belančevine|Πρωτεΐνη|Πρωτεΐνες|lporeivEÇ|lpureivEc""")
    private val fatKeyword = Regex("""(?i)(?:Total\s*)?Fat|Masti|Lipides|Λιπαρά|Λίπος|Amapá""")
    private val carbsKeyword = Regex("""(?i)(?:Total\s*)?Carb(?:ohydrate)?s?|Glucides|Ugljeni\s*hidrati|Υδατάνθρακες|Yõardv@pakE|Yöarbvepaxe""")

    /**
     * Attempts to extract a food name from a package front. 
     * Usually looks for the largest text or the first few lines, ignoring generic 
     * weight/volume markers.
     */
    fun parseName(text: Text): String? {
        val allLines = text.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return null

        // Sort by font size (approximated by bounding box height)
        val sortedBySize = allLines.sortedByDescending { it.boundingBox?.height() ?: 0 }
        
        // Filter out generic units like "500g", "1L", "NET WT"
        val filterRegex = Regex("""(?i)^\d+\s*(?:g|kg|ml|l|oz|lb|pcs)|net\s*wt|front|back|photo""")
        
        // Take the largest non-generic line
        val candidate = sortedBySize.firstOrNull { line ->
            !filterRegex.containsMatchIn(line.text) && line.text.length > 2
        }

        return candidate?.text?.trim()?.replace("\n", " ")
    }

    fun parseText(text: Text): ScannedNutrition {
        Log.d("NutritionScanner", "--- PARSING NEW FRAME ---")
        val allLines = text.textBlocks.flatMap { it.lines }
        
        val rows = groupIntoRows(allLines)
        val rowTexts = rows.map { row ->
            val txt = row.joinToString(" ") { it.text }
            Log.d("NutritionScanner", "RECONSTRUCTED ROW: $txt")
            txt
        }

        // 1. First, search for "kcal" anywhere in the text as it is the most reliable anchor
        val anchorKcal = rowTexts.mapNotNull { kcalTagRegex.find(it)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() }.firstOrNull()

        // 2. Extract values using anchor and spatial context
        val calories = anchorKcal ?: findValueInRows(rowTexts, caloriesKeyword, isEnergyRow = true)
        
        return ScannedNutrition(
            calories = calories,
            protein  = findValueInRows(rowTexts, proteinKeyword, excludeValue = calories),
            fat      = findValueInRows(rowTexts, fatKeyword, excludeValue = calories),
            carbs    = findValueInRows(rowTexts, carbsKeyword, excludeValue = calories)
        )
    }

    private fun groupIntoRows(lines: List<Text.Line>): List<List<Text.Line>> {
        val sorted = lines.filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.top }

        val rows = mutableListOf<MutableList<Text.Line>>()

        for (line in sorted) {
            val box = line.boundingBox!!
            val centerY = (box.top + box.bottom) / 2f

            val matchingRow = rows.firstOrNull { row ->
                val rBox = row.first().boundingBox!!
                val rHeight = (rBox.bottom - rBox.top).coerceAtLeast(1)
                val rCenterY = (rBox.top + rBox.bottom) / 2f
                // High tolerance (90%) for slanted/dense labels
                kotlin.math.abs(centerY - rCenterY) < rHeight * 0.9
            }

            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        return rows.map { row -> row.sortedBy { it.boundingBox!!.left } }
    }

    private fun findValueInRows(rowTexts: List<String>, keywordRegex: Regex, isEnergyRow: Boolean = false, excludeValue: Double? = null): Double? {
        for ((i, row) in rowTexts.withIndex()) {
            if (!keywordRegex.containsMatchIn(row)) continue

            // Strategy: Check current row, then next row (OCR often splits label and value)
            val candidates = mutableListOf<String>()
            candidates.add(row)
            if (i + 1 < rowTexts.size) candidates.add(rowTexts[i + 1])

            for (text in candidates) {
                val value = extractValue(text, isEnergyRow)
                
                // Calibration: ignore values that are likely the calorie count if looking for macros
                if (value != null && excludeValue != null && kotlin.math.abs(value - excludeValue) < 1.0) {
                    continue 
                }

                if (value != null) return value
            }
        }
        return null
    }

    private fun extractValue(text: String, isEnergyRow: Boolean): Double? {
        // Explicit kcal tag check
        kcalTagRegex.find(text)?.let {
            return it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        val numbers = numberRegex.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", ".")?.toDoubleOrNull() }
            .toList()

        if (numbers.isEmpty()) return null

        if (isEnergyRow && numbers.size >= 2) {
            for (a in numbers) {
                for (b in numbers) {
                    if (a <= b) continue
                    val ratio = a / b
                    if (kotlin.math.abs(ratio - KJ_TO_KCAL_RATIO) / KJ_TO_KCAL_RATIO < RATIO_TOLERANCE) {
                        return b 
                    }
                }
            }
            // Energy rows: kcal is usually the smaller one if under 1000, 
            // but many European labels put kJ first.
            return numbers.minOrNull()
        }

        if (!isEnergyRow) {
            // Find numbers followed by 'g' or misread variants like '9' (very common)
            val gMatch = Regex("""(\d+(?:[.,]\d+)?)\s*[g9s]""").find(text)
            if (gMatch != null) {
                return gMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
            }
        }

        return numbers.firstOrNull()
    }
}
