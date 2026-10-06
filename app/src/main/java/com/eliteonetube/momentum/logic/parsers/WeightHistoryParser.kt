package com.eliteonetube.momentum.logic.parsers

import android.util.Log
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.utils.Units
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

object WeightHistoryParser {

    private const val TAG = "WeightHistoryParser"
    
    // Prioritize numbers followed by units (kg, lb) or numbers with decimals
    private val weightWithUnitRegex = Regex("""(\d{2,3}(?:[.,]\d)?)\s*(kg|lbs?)""", RegexOption.IGNORE_CASE)
    private val weightDecimalRegex = Regex("""(\d{2,3}[.,]\d)""")
    private val weightSimpleRegex = Regex("""(\d{2,3})""")
    
    private val dateRegexes = listOf(
        Regex("""(\d{1,2}\s+[a-z]{3,9}\s+\d{4})""", RegexOption.IGNORE_CASE), // 30 Jul 2026
        Regex("""([a-z]{3,9}\s+\d{1,2},?\s+\d{4})""", RegexOption.IGNORE_CASE), // Jul 30, 2026
        Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})"""), // 30/07/2026
        Regex("""(\d{1,2}\s+[a-z]{3,9})""", RegexOption.IGNORE_CASE), // 30 Jul
        Regex("""([a-z]{3,9}\s+\d{1,2})""", RegexOption.IGNORE_CASE)  // Jul 30
    )

    // Formats without a year default to the current year (screenshots often show "30 Jul").
    // A plain ofPattern("d MMM") can't produce a full date, so those rows used to be dropped.
    // Built per import (not cached) so "this year" can't go stale if the app stays open over New Year.
    private fun dateFormats(currentYear: Long): List<DateTimeFormatter> {
        fun format(pattern: String, locale: Locale? = Locale.US) = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .parseDefaulting(ChronoField.YEAR_OF_ERA, currentYear)
            .let { if (locale != null) it.toFormatter(locale) else it.toFormatter() }
        return listOf(
            format("d MMM yyyy"),
            format("MMM d yyyy"),
            format("MMM d, yyyy"),
            format("dd/MM/yyyy", locale = null),
            format("d MMM"),
            format("MMM d")
        )
    }

    /**
     * Weights are always returned in kg. A number labelled "lb"/"lbs" is converted; a number with no
     * unit is taken to be in [defaultUnit] (the user's own setting).
     */
    fun parse(visionText: Text, defaultUnit: UnitSystem): List<WeightEntry> {
        val results = mutableListOf<WeightEntry>()
        val formats = dateFormats(LocalDate.now().year.toLong())
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

        for (row in rows) {
            // Sort lines by left-to-right to keep reading order
            val sortedLines = row.sortedBy { it.boundingBox?.left ?: 0 }
            val rowText = sortedLines.joinToString(" ") { it.text }
            Log.d(TAG, "Processing row: $rowText")
            
            // 1. Find the date first so we can ignore its components when looking for weights
            val dateMatch = findDateMatch(rowText)
            val date = dateMatch?.let { parseDate(it, formats) } ?: continue
            
            // 2. Find the weight, excluding the part of the string that matched the date
            val textToSearchForWeight = if (dateMatch != null) {
                rowText.replace(dateMatch, "").trim()
            } else {
                rowText
            }
            
            val weight = extractWeightKg(textToSearchForWeight, defaultUnit)
                ?: extractWeightKg(rowText, defaultUnit) // Fallback to full text if cleaning failed
            
            if (weight != null) {
                Log.d(TAG, "Found valid entry: $date -> $weight")
                results.add(WeightEntry(date = date.toString(), weight = weight))
            }
        }

        return results.distinctBy { it.date }.sortedByDescending { it.date }
    }

    private fun extractWeightKg(text: String, defaultUnit: UnitSystem): Double? {
        // High priority: Number + Unit — the label wins over the user's setting
        weightWithUnitRegex.find(text)?.let {
            val value = it.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
            val isPounds = it.groupValues[2].startsWith("lb", ignoreCase = true)
            return if (isPounds) Units.lbToKg(value) else value
        }

        val unitless = weightDecimalRegex.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() // Number with decimal
            ?: weightSimpleRegex.findAll(text).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull() // Last number (often weight is on the right)
            ?: return null
        return if (defaultUnit == UnitSystem.IMPERIAL) Units.lbToKg(unitless) else unitless
    }

    private fun findDateMatch(text: String): String? {
        for (regex in dateRegexes) {
            val match = regex.find(text)
            if (match != null) return match.value
        }
        return null
    }

    private fun parseDate(dateStr: String, formats: List<DateTimeFormatter>): LocalDate? {
        for (formatter in formats) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter)
            } catch (e: Exception) {}
        }
        return null
    }
}
