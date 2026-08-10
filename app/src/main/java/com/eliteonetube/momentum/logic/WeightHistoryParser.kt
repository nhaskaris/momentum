package com.eliteonetube.momentum.logic

import android.util.Log
import com.eliteonetube.momentum.data.WeightEntry
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object WeightHistoryParser {

    private const val TAG = "WeightHistoryParser"
    
    // Prioritize numbers followed by units (kg, lb) or numbers with decimals
    private val weightWithUnitRegex = Regex("""(\d{2,3}(?:[.,]\d)?)\s*(?:kg|lb|lbs)""", RegexOption.IGNORE_CASE)
    private val weightDecimalRegex = Regex("""(\d{2,3}[.,]\d)""")
    private val weightSimpleRegex = Regex("""(\d{2,3})""")
    
    private val dateRegexes = listOf(
        Regex("""(\d{1,2}\s+[a-z]{3,9}\s+\d{4})""", RegexOption.IGNORE_CASE), // 30 Jul 2026
        Regex("""([a-z]{3,9}\s+\d{1,2},?\s+\d{4})""", RegexOption.IGNORE_CASE), // Jul 30, 2026
        Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})"""), // 30/07/2026
        Regex("""(\d{1,2}\s+[a-z]{3,9})""", RegexOption.IGNORE_CASE), // 30 Jul
        Regex("""([a-z]{3,9}\s+\d{1,2})""", RegexOption.IGNORE_CASE)  // Jul 30
    )

    private val dateFormats = mapOf(
        "d MMM yyyy" to DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US),
        "MMM d yyyy" to DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
        "MMM d, yyyy" to DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
        "dd/MM/yyyy" to DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        "d MMM" to DateTimeFormatter.ofPattern("d MMM", Locale.US),
        "MMM d" to DateTimeFormatter.ofPattern("MMM d", Locale.US)
    )

    fun parse(visionText: Text): List<WeightEntry> {
        val results = mutableListOf<WeightEntry>()
        val blocks = visionText.textBlocks
        
        val lines = blocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
        
        for (line in lines) {
            val box = line.boundingBox ?: continue
            val centerY = (box.top + box.bottom) / 2f
            
            val matchingRow = rows.firstOrNull { row ->
                val rBox = row.first().boundingBox ?: return@firstOrNull false
                val rCenterY = (rBox.top + rBox.bottom) / 2f
                val rHeight = (rBox.bottom - rBox.top).coerceAtLeast(1)
                kotlin.math.abs(centerY - rCenterY) < rHeight * 0.6
            }
            
            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        val currentYear = LocalDate.now().year

        for (row in rows) {
            // Sort lines by left-to-right to keep reading order
            val sortedLines = row.sortedBy { it.boundingBox?.left ?: 0 }
            val rowText = sortedLines.joinToString(" ") { it.text }
            Log.d(TAG, "Processing row: $rowText")
            
            // 1. Find the date first so we can ignore its components when looking for weights
            val dateMatch = findDateMatch(rowText)
            val date = dateMatch?.let { parseDate(it, currentYear) } ?: continue
            
            // 2. Find the weight, excluding the part of the string that matched the date
            val textToSearchForWeight = if (dateMatch != null) {
                rowText.replace(dateMatch, "").trim()
            } else {
                rowText
            }
            
            val weight = extractWeight(textToSearchForWeight) ?: extractWeight(rowText) // Fallback to full text if cleaning failed
            
            if (weight != null) {
                Log.d(TAG, "Found valid entry: $date -> $weight")
                results.add(WeightEntry(date = date.toString(), weight = weight))
            }
        }

        return results.distinctBy { it.date }.sortedByDescending { it.date }
    }

    private fun extractWeight(text: String): Double? {
        // High priority: Number + Unit
        weightWithUnitRegex.find(text)?.let {
            return it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }
        
        // Medium priority: Number with decimal
        weightDecimalRegex.find(text)?.let {
            return it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }
        
        // Low priority: Last simple number found (often weight is on the right)
        val matches = weightSimpleRegex.findAll(text).toList()
        if (matches.isNotEmpty()) {
            return matches.last().groupValues[1].toDoubleOrNull()
        }
        
        return null
    }

    private fun findDateMatch(text: String): String? {
        for (regex in dateRegexes) {
            val match = regex.find(text)
            if (match != null) return match.value
        }
        return null
    }

    private fun parseDate(dateStr: String, currentYear: Int): LocalDate? {
        for (formatter in dateFormats.values) {
            try {
                var parsed = LocalDate.parse(dateStr, formatter)
                if (!dateStr.contains(Regex("""\d{4}"""))) {
                    parsed = parsed.withYear(currentYear)
                }
                return parsed
            } catch (e: Exception) {}
        }
        return null
    }
}
