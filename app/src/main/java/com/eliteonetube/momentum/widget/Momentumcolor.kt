package com.eliteonetube.momentum.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

/**
 * Widget palette, matching the app's light and dark themes (ui/theme). Each entry follows the
 * phone's light/dark setting on its own, the way the app does.
 */
object MomentumColors {
    val background: ColorProvider = ColorProvider(day = Color.White, night = Color(0xFF18181B))
    val textPrimary: ColorProvider = ColorProvider(day = Color(0xFF111827), night = Color(0xFFFAFAFA))
    val textSecondary: ColorProvider = ColorProvider(day = Color(0xFF6B7280), night = Color(0xFFA1A1AA))
    val accent: ColorProvider = solidColor(Color(0xFF0EA5E9))
    val accentSoft: ColorProvider = ColorProvider(day = Color(0x260EA5E9), night = Color(0x330EA5E9))
    val over: ColorProvider = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
    val track: ColorProvider = ColorProvider(day = Color(0x1A111827), night = Color(0x26FAFAFA))
    val onAccent: ColorProvider = solidColor(Color.White)

    // Same macro colours as the Eat tab and Dashboard
    val protein: ColorProvider = solidColor(Color(0xFF0EA5E9))
    val carbs: ColorProvider = solidColor(Color(0xFF10B981))
    val fat: ColorProvider = solidColor(Color(0xFFF59E0B))
}

/** A colour that's the same in light and dark mode. */
fun solidColor(color: Color): ColorProvider = ColorProvider(day = color, night = color)
