package com.eliteonetube.momentum.logic


import com.eliteonetube.momentum.data.UnitSystem
import kotlin.math.roundToInt

object Units {
    fun kgToLb(kg: Double): Double = kg * 2.20462
    fun lbToKg(lb: Double): Double = lb / 2.20462

    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = (totalInches % 12).roundToInt()
        return feet to inches
    }
    fun feetInchesToCm(feet: Int, inches: Int): Double = ((feet * 12) + inches) * 2.54

    fun displayWeight(kg: Double, system: UnitSystem): String =
        if (system == UnitSystem.IMPERIAL) "%.1f lb".format(kgToLb(kg))
        else "%.1f kg".format(kg)

    fun displayHeight(cm: Double, system: UnitSystem): String =
        if (system == UnitSystem.IMPERIAL) {
            val (ft, inch) = cmToFeetInches(cm)
            "$ft' $inch\""
        } else "${cm.toInt()} cm"
}