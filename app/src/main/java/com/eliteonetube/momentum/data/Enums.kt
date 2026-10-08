package com.eliteonetube.momentum.data

enum class Goal { CUT, BULK, MAINTAIN, REVERSE }

enum class UnitSystem { METRIC, IMPERIAL }

enum class AppTheme { LIGHT, DARK, SYSTEM }

enum class ExerciseType { STRENGTH, CARDIO }

/** Which part of the day a diary entry belongs to. Entries logged before sections existed are UNCATEGORIZED. */
enum class MealSlot(val label: String) {
    UNCATEGORIZED("Uncategorized"),
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks");

    companion object {
        /** The sections shown every day; Uncategorized only appears when it has entries. */
        val sections = listOf(BREAKFAST, LUNCH, DINNER, SNACKS)

        /** A sensible default when nothing chose a section, e.g. a barcode scan. */
        fun forTime(time: java.time.LocalTime): MealSlot = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACKS
        }
    }
}

/** Where a new diary entry goes: which day and which section. */
data class LogTarget(val date: String, val slot: MealSlot) {
    companion object {
        fun now() = LogTarget(java.time.LocalDate.now().toString(), MealSlot.forTime(java.time.LocalTime.now()))
    }
}
