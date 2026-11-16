package com.doolan.forge.data

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

data class NutritionTargets(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val bmr: Int,
    val tdee: Int,
    val notes: List<String>
)

object Nutrition {
    val GOALS = listOf("Lose fat", "Maintain", "Build muscle")
    val MEASURES = listOf("Body fat %", "Waist", "Chest", "Arm", "Thigh", "Hip", "Neck")

    fun unit(type: String) = if (type == "Body fat %") "%" else "cm"

    /** Estimated targets from the profile. Returns null until age, sex, height and weight are filled in. */
    fun targets(p: ProfileData): NutritionTargets? {
        val age = p.age.toDoubleOrNull() ?: return null
        val h = p.heightCm.toDoubleOrNull() ?: return null
        val w = p.weightKg.toDoubleOrNull() ?: return null
        if (age <= 0 || h <= 0 || w <= 0) return null
        val sexTerm = when (p.sex) { "Male" -> 5.0; "Female" -> -161.0; else -> -78.0 }
        val bmr = 10 * w + 6.25 * h - 5 * age + sexTerm
        val n = p.trainingDays.size
        val factor = when {
            n <= 1 -> 1.2
            n <= 3 -> 1.375
            n <= 5 -> 1.55
            else -> 1.725
        }
        val tdee = bmr * factor
        val notes = ArrayList<String>()
        var kcal = when (p.nutritionGoal) {
            "Lose fat" -> tdee - minOf(500.0, tdee * 0.2)
            "Build muscle" -> tdee + 250.0
            else -> tdee
        }
        if (kcal < bmr) {
            kcal = bmr
            notes.add("Calories were held at your resting needs. Cutting lower than that is not recommended without professional guidance.")
        }
        val proteinPerKg = when (p.nutritionGoal) { "Lose fat" -> 2.2; "Build muscle" -> 2.0; else -> 1.8 }
        val protein = w * proteinPerKg
        val fat = max(0.8 * w, 0.25 * kcal / 9.0)
        val carbs = max(0.0, (kcal - protein * 4 - fat * 9) / 4.0)
        notes.add("These are estimates from standard formulas (Mifflin-St Jeor plus an activity level from your training days). Use them as a starting point and adjust by watching your weight trend over 2 to 3 weeks.")
        return NutritionTargets(
            kcal.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt(),
            bmr.roundToInt(), tdee.roundToInt(), notes
        )
    }

    /** US Navy body fat estimate from tape measurements in cm. An estimate only; it can be off by several percent. */
    fun navyBodyFat(p: ProfileData, waist: Double?, neck: Double?, hip: Double?): Double? {
        val h = p.heightCm.toDoubleOrNull() ?: return null
        if (waist == null || neck == null || h <= 0) return null
        val bf = if (p.sex == "Female") {
            if (hip == null || waist + hip - neck <= 0) return null
            163.205 * log10(waist + hip - neck) - 97.684 * log10(h) - 78.387
        } else {
            if (waist - neck <= 0) return null
            86.010 * log10(waist - neck) - 70.041 * log10(h) + 36.76
        }
        return if (bf in 3.0..60.0) bf else null
    }
}
