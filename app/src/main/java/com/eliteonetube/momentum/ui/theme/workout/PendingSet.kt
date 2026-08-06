package com.eliteonetube.momentum.ui.workout

/**
 * Temporary in-memory representation of a set logged during an active session
 * before it is saved into the database.
 */
data class PendingSet(
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val notes: String? = null,
    val isCompleted: Boolean = true
)