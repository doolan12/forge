package com.doolan.forge.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Readiness(
    val score: Int,
    val label: String,
    val advice: String,
    val factors: List<String>,
    val low: Boolean
)

data class LoadInfo(val thisWeek: Int, val usualWeek: Int, val ratio: Double, val label: String)

/**
 * Readiness comes only from what the user logs in the daily check-in (sleep, soreness, stress, energy,
 * and resting heart rate if they enter it). It is a guide, not a medical measurement.
 */
object ReadinessCalc {

    fun score(c: CheckInEntity, targetSleep: Double, earlier: List<CheckInEntity>): Readiness {
        val target = if (targetSleep in 4.0..12.0) targetSleep else 8.0
        val factors = ArrayList<String>()

        val sleepPart = 30.0 * min(1.0, c.sleepHours / target)
        val qualityPart = 15.0 * (c.sleepQuality - 1) / 4.0
        val sorenessPart = 25.0 * (5 - c.soreness) / 4.0
        val stressPart = 15.0 * (5 - c.stress) / 4.0
        val energyPart = 15.0 * (c.energy - 1) / 4.0
        var total = sleepPart + qualityPart + sorenessPart + stressPart + energyPart

        if (c.sleepHours < target * 0.85) {
            factors.add("You slept ${Calc.kg(c.sleepHours)} h, below your ${Calc.kg(target)} h target.")
        }
        if (c.sleepQuality <= 2) factors.add("Sleep quality was poor.")
        if (c.soreness >= 4) factors.add("Muscle soreness is high.")
        if (c.stress >= 4) factors.add("Stress is high.")
        if (c.energy <= 2) factors.add("Energy is low.")

        if (c.restingHr > 0) {
            val past = earlier.filter { it.restingHr > 0 && it.dateEpochDay < c.dateEpochDay }.takeLast(14)
            if (past.size >= 5) {
                val avg = past.map { it.restingHr }.average()
                val diff = c.restingHr - avg
                if (diff >= 5) {
                    total -= 10
                    factors.add("Resting heart rate is ${diff.roundToInt()} bpm above your recent average.")
                } else if (diff >= 3) {
                    total -= 5
                    factors.add("Resting heart rate is a little above your recent average.")
                }
            }
        }

        val score = max(0, min(100, total.roundToInt()))
        val label: String
        var advice: String
        when {
            score >= 75 -> { label = "Good to go"; advice = "You look recovered. Train as planned." }
            score >= 55 -> { label = "Moderate"; advice = "Train as planned, but warm up well and stop sets a rep earlier if it feels heavy." }
            else -> { label = "Low"; advice = "Your body is asking for an easier day. You can ease today's session (fewer sets, more reps in reserve) or rest. Missed work is never squeezed into your other days." }
        }
        if (c.soreness >= 4) {
            advice += " Normal soreness is dull and eases as you move. Sharp or joint pain is different, so stop and get it checked."
        }
        return Readiness(score, label, advice, factors, score < 55)
    }

    /** Load = how hard you rated each completed session times its length. It needs about 3 weeks of logged sessions to mean much. */
    fun load(sessions: List<SessionEntity>, today: Long): LoadInfo? {
        fun load(s: SessionEntity) = (if (s.difficulty > 0) s.difficulty else 5) * s.durationMin
        val done = sessions.filter { it.status == "DONE" && it.discipline != "REST" }
        val week = done.filter { it.dateEpochDay in (today - 6)..today }.sumOf { load(it) }
        val earlier = done.filter { it.dateEpochDay in (today - 27)..(today - 7) }
        if (earlier.isEmpty()) return null
        val usual = earlier.sumOf { load(it) } / 3.0
        if (usual <= 0.0) return null
        val ratio = week / usual
        val label = when {
            ratio < 0.8 -> "lighter than usual"
            ratio <= 1.3 -> "in line with your usual"
            ratio <= 1.5 -> "higher than usual, watch your recovery"
            else -> "a big jump from your usual, take care"
        }
        return LoadInfo(week, usual.roundToInt(), ratio, label)
    }
}
