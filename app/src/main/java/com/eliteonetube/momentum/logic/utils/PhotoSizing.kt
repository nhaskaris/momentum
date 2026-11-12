package com.eliteonetube.momentum.logic.utils

import kotlin.math.max
import kotlin.math.roundToInt

/** How large saved progress photos may be. No Android types, so it can be unit-tested. */
object PhotoSizing {
    /** Sharp full-screen on a phone, at roughly a tenth of a camera original's file size. */
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 85

    fun needsShrinking(width: Int, height: Int): Boolean = max(width, height) > MAX_EDGE

    /** The size to save at: unchanged when already small enough, otherwise the long side becomes [MAX_EDGE]. */
    fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        if (!needsShrinking(width, height)) return width to height
        val scale = MAX_EDGE.toDouble() / max(width, height)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** Largest power-of-two subsampling that still decodes at least [MAX_EDGE] on the long side. */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }
}
