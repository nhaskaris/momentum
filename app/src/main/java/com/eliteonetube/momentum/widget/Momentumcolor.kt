package com.eliteonetube.momentum.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

object MomentumColors {
    val blue = Color(0xFF0EA5E9)
    val dark = Color(0xFF09090B)
    val surface = Color(0xFF18181B)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.5f)
}

/**
 * Ensures consistent color application across Glance widgets.
 */
fun solidColor(color: Color): ColorProvider = ColorProvider(day = color, night = color)
