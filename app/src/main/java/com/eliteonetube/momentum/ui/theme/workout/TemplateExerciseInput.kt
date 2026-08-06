package com.eliteonetube.momentum.ui.theme.workout

import com.eliteonetube.momentum.data.Exercise

data class TemplateExerciseInput(
    val exercise: Exercise,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0
)