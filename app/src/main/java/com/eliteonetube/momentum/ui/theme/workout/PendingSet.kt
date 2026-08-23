package com.eliteonetube.momentum.ui.workout

/**
 * Temporary in-memory representation of a set logged during an active session
 * before it is saved into the database.
 */
data class PendingSet(
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val orderIndex: Int = 0
)
