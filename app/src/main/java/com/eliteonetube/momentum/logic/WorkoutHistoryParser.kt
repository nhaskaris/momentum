package com.eliteonetube.momentum.logic

import com.eliteonetube.momentum.data.Exercise
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

data class ScannedExerciseReview(
    val rawName: String,
    val exercise: Exercise?,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double
)

object WorkoutHistoryParser {

    private val setPattern = Regex("""(\d{1,2})\s*[xX*]\s*(\d{1,3}(?:[.,]\d)?)\s*(?:kg|lb|lbs)?""")
    private val reverseSetPattern = Regex("""(\d{1,3}(?:[.,]\d)?)\s*(?:kg|lb|lbs)?\s*[xX*]\s*(\d{1,2})""")
    private val numberPattern = Regex("""(\d{1,3}(?:[.,]\d)?)""")
    
    private val explicitSetsPattern = Regex("""Sets:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val explicitRepsPattern = Regex("""Reps:\s*(\d+)""", RegexOption.IGNORE_CASE)

    // Expanded noise patterns to filter out UI elements and descriptions
    private val noisePatterns = listOf(
        Regex("""^Set\s*\d+""", RegexOption.IGNORE_CASE),
        Regex("""^Reps:""", RegexOption.IGNORE_CASE),
        Regex("""^Weight""", RegexOption.IGNORE_CASE),
        Regex("""^Section""", RegexOption.IGNORE_CASE),
        Regex("""^Regular""", RegexOption.IGNORE_CASE),
        Regex("""^Add Set""", RegexOption.IGNORE_CASE),
        Regex("""^Delete Set""", RegexOption.IGNORE_CASE),
        Regex("""^Workout""", RegexOption.IGNORE_CASE),
        Regex("""^Notes""", RegexOption.IGNORE_CASE),
        Regex("""^Today""", RegexOption.IGNORE_CASE),
        Regex("""^History""", RegexOption.IGNORE_CASE),
        Regex("""^\d{1,2}:\d{2}"""), // Time formats
        Regex("""^\d{1,2}\s+[a-z]{3,9}\s+\d{4}""", RegexOption.IGNORE_CASE) // Date formats
    )

    fun parse(visionText: Text, allExercises: List<Exercise>): List<ScannedExerciseReview> {
        val detected = mutableListOf<ScannedExerciseReview>()
        
        // 1. Group all lines into spatial rows
        val allLines = visionText.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Line>>()
        
        for (line in allLines) {
            val box = line.boundingBox ?: continue
            val centerY = (box.top + box.bottom) / 2f
            
            val matchingRow = rows.firstOrNull { row ->
                val rBox = row.first().boundingBox ?: return@firstOrNull false
                val rCenterY = (rBox.top + rBox.bottom) / 2f
                val rHeight = (rBox.bottom - rBox.top).coerceAtLeast(1)
                abs(centerY - rCenterY) < rHeight * 0.7
            }
            
            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        var currentRawName: String? = null
        var currentMatchedExercise: Exercise? = null
        var currentSets = mutableListOf<Pair<Int, Double>>()
        var explicitSetCount: Int? = null
        var explicitRepTarget: Int? = null

        for (row in rows) {
            val sortedRow = row.sortedBy { it.boundingBox?.left ?: 0 }
            val rowText = sortedRow.joinToString(" ") { it.text }.trim()
            
            val matchedExercise = findMatchingExercise(rowText, allExercises)
            
            // Refined heading detection: must be relatively short and not purely numerical
            val isPotentialHeading = matchedExercise != null || (
                rowText.length in 4..40 && 
                !Regex("""^\d""").containsMatchIn(rowText) && 
                noisePatterns.none { it.containsMatchIn(rowText) } &&
                rowText.count { it == ' ' } < 6 // Exercises rarely have more than 6 words
            )

            if (matchedExercise != null || isPotentialHeading) {
                if (currentRawName != null) {
                    addIfValid(detected, currentRawName, currentMatchedExercise, currentSets, explicitSetCount, explicitRepTarget)
                }
                
                currentRawName = rowText
                currentMatchedExercise = matchedExercise
                currentSets = mutableListOf()
                explicitSetCount = null
                explicitRepTarget = null
                
                explicitSetsPattern.find(rowText)?.let { explicitSetCount = it.groupValues[1].toIntOrNull() }
                explicitRepsPattern.find(rowText)?.let { explicitRepTarget = it.groupValues[1].toIntOrNull() }
                continue
            }

            if (currentRawName != null) {
                val setsMatch = explicitSetsPattern.find(rowText)
                val repsMatch = explicitRepsPattern.find(rowText)
                
                if (setsMatch != null) explicitSetCount = setsMatch.groupValues[1].toIntOrNull()
                if (repsMatch != null) explicitRepTarget = repsMatch.groupValues[1].toIntOrNull()
                
                if (setsMatch != null || repsMatch != null) continue

                val xMatch = setPattern.find(rowText) ?: reverseSetPattern.find(rowText)
                if (xMatch != null) {
                    val v1 = xMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    val v2 = xMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                    val (reps, weight) = inferRepsAndWeight(v1, v2)
                    if (reps > 0) currentSets.add(reps to weight)
                } else {
                    val numbers = numberPattern.findAll(rowText).map { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }.toList()
                    if (numbers.size >= 2) {
                        val (reps, weight) = if (numbers.size >= 3) {
                            inferRepsAndWeight(numbers[1], numbers[2])
                        } else {
                            inferRepsAndWeight(numbers[0], numbers[1])
                        }
                        if (reps > 0) currentSets.add(reps to weight)
                    }
                }
            }
        }

        if (currentRawName != null) {
            addIfValid(detected, currentRawName, currentMatchedExercise, currentSets, explicitSetCount, explicitRepTarget)
        }

        // Final safety filter: remove items that have very unlikely names
        return detected.filter { item ->
            val name = item.exercise?.name ?: item.rawName
            name.length > 3 && !name.contains(Regex("""\d{2,}:""")) // Filter out timestamps
        }.distinctBy { it.exercise?.id ?: it.rawName }
    }

    private fun addIfValid(
        list: MutableList<ScannedExerciseReview>,
        rawName: String,
        exercise: Exercise?,
        sets: List<Pair<Int, Double>>,
        explicitSets: Int?,
        explicitReps: Int?
    ) {
        // Only add if we matched an exercise OR if we found actual numerical set data
        // This prevents random UI text from showing up in the list
        if (exercise != null || sets.isNotEmpty() || (explicitSets != null && explicitReps != null)) {
            list.add(createReviewItem(rawName, exercise, sets, explicitSets, explicitReps))
        }
    }

    private fun inferRepsAndWeight(v1: Double, v2: Double): Pair<Int, Double> {
        return if (v1 < v2 && v1 < 50) v1.toInt() to v2
        else if (v2 < v1 && v2 < 50) v2.toInt() to v1
        else v1.toInt() to v2
    }

    private fun createReviewItem(
        rawName: String, 
        exercise: Exercise?, 
        sets: List<Pair<Int, Double>>, 
        explicitSets: Int?, 
        explicitReps: Int?
    ): ScannedExerciseReview {
        val workingSet = sets.maxByOrNull { it.second } ?: (10 to 0.0)
        return ScannedExerciseReview(
            rawName = rawName,
            exercise = exercise,
            targetSets = explicitSets ?: sets.size.coerceAtLeast(3),
            targetReps = explicitReps ?: workingSet.first,
            targetWeightKg = workingSet.second
        )
    }

    private fun findMatchingExercise(text: String, allExercises: List<Exercise>): Exercise? {
        val normalized = text.lowercase()
            .replace("db", "dumbbell")
            .replace("bb", "barbell")
            .replace("inc ", "incline ")
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            
        if (normalized.length < 3) return null

        allExercises.find { it.name.lowercase() == normalized }?.let { return it }

        val inputTokens = normalized.split(" ").filter { it.length > 2 }.toSet()
        if (inputTokens.isEmpty()) return null
        
        var bestMatch: Exercise? = null
        var maxOverlap = 0
        
        for (exercise in allExercises) {
            val exTokens = exercise.name.lowercase().split(" ").filter { it.length > 2 }.toSet()
            val overlap = inputTokens.intersect(exTokens).size
            
            if (overlap > maxOverlap && (overlap >= (exTokens.size * 0.6) || overlap >= (inputTokens.size * 0.6))) {
                maxOverlap = overlap
                bestMatch = exercise
            }
        }
        
        return bestMatch
    }
}
