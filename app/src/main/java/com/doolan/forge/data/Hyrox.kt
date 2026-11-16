package com.doolan.forge.data

import kotlin.math.max
import kotlin.math.roundToInt

/** Race weights for one HYROX division. Sled weights include the weight of the sled. */
data class HyroxDivision(
    val name: String,
    val pushKg: Int,
    val pullKg: Int,
    val farmersKgEach: Int,
    val sandbagKg: Int,
    val wallBallKg: Int
)

data class HyroxOptions(val division: String, val sessionsPerWeek: Int)

data class HyroxSession(
    val kind: String,            // STATIONS_A, STATIONS_B, COMPROMISED, ENGINE, HALF_SIM, FULL_SIM
    val title: String,
    val minutes: Int,
    val workout: String,
    val stations: List<StationBlock>
)

/** A station that is logged as sets in the workout screen. */
data class StationBlock(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val note: String
)

object Hyrox {
    val DIVISIONS = listOf(
        HyroxDivision("Women Open", 102, 78, 16, 10, 4),
        HyroxDivision("Men Open", 152, 103, 24, 20, 6),
        HyroxDivision("Women Pro", 152, 103, 24, 20, 6),
        HyroxDivision("Men Pro", 202, 153, 32, 30, 9)
    )

    fun division(name: String): HyroxDivision = DIVISIONS.firstOrNull { it.name == name } ?: DIVISIONS[1]

    private fun round(x: Double, step: Double) = ((x / step).roundToInt() * step)

    /** Fraction of race weight to train with. Starts light and reaches race weight in the peak phase. */
    private fun loadFactor(phase: String, cutback: Boolean): Double {
        val f = when (phase) {
            "Base" -> 0.7
            "Build" -> 0.85
            "Peak" -> 1.0
            else -> 0.8
        }
        return if (cutback) f * 0.9 else f
    }

    private val RACE_NOTE =
        "Race weights follow the standard HYROX divisions (sled weights include the sled). Check the current official rulebook before race day, because the rules can change."

    fun raceNote(): String = RACE_NOTE

    /** Which station sessions to use in a week, in order. */
    fun kindsFor(sessions: Int, phase: String, w: Int, weeks: Int): List<String> {
        val sim = phase == "Peak" && w >= weeks - 3 && (w % 2 == 0)
        return when (sessions.coerceIn(1, 3)) {
            1 -> listOf(if (sim) "HALF_SIM" else "COMPROMISED")
            2 -> listOf(if (w % 2 == 1) "STATIONS_A" else "STATIONS_B", if (sim) "HALF_SIM" else "COMPROMISED")
            else -> listOf(if (w % 2 == 1) "STATIONS_A" else "STATIONS_B", "ENGINE", if (sim) "HALF_SIM" else "COMPROMISED")
        }
    }

    fun week(o: HyroxOptions, w: Int, weeks: Int, taperWeeks: Int, paces: Paces?): List<HyroxSession> {
        val phase = Running.phase(w, weeks, taperWeeks)
        val cutback = w % 4 == 0 && phase != "Taper"
        val d = division(o.division)
        val f = loadFactor(phase, cutback)
        val taper = phase == "Taper"
        return kindsFor(o.sessionsPerWeek, phase, w, weeks).map { kind ->
            when (kind) {
                "STATIONS_A" -> stationsA(d, f, taper, cutback)
                "STATIONS_B" -> stationsB(d, f, taper, cutback)
                "ENGINE" -> engine(taper, cutback, paces)
                "HALF_SIM" -> sim(d, paces)
                else -> compromised(d, f, taper, cutback, paces)
            }
        }
    }

    private fun setsFor(base: Int, taper: Boolean, cutback: Boolean) =
        if (taper) max(2, base - 2) else if (cutback) max(2, base - 1) else base

    private fun stationsA(d: HyroxDivision, f: Double, taper: Boolean, cutback: Boolean): HyroxSession {
        val sets = setsFor(4, taper, cutback)
        val push = round(d.pushKg * f, 5.0)
        val pull = round(d.pullKg * f, 5.0)
        val farm = round(d.farmersKgEach * f, 2.0)
        val blocks = listOf(
            StationBlock("hx_sled_push", sets, 2, push, "Reps here are 25 m lengths (2 lengths = the race 50 m). Race weight is ${d.pushKg} kg including the sled. Low, driving steps."),
            StationBlock("hx_sled_pull", sets, 2, pull, "Reps are 25 m lengths. Race weight is ${d.pullKg} kg including the sled. Hand over hand, sit back."),
            StationBlock("hx_farmers", sets, 4, farm, "Weight is per hand. Reps are 25 m lengths (8 lengths = the race 200 m). Race weight is ${d.farmersKgEach} kg each hand.")
        )
        return HyroxSession(
            "STATIONS_A", "HYROX stations: sleds and carry", 50 + sets * 4,
            "Heavy station work. Rest 2 minutes between sets so every set is done with good form. Loads are ${(f * 100).roundToInt()}% of race weight${if (f < 1.0) ", building towards full race weight" else ""}.",
            blocks
        )
    }

    private fun stationsB(d: HyroxDivision, f: Double, taper: Boolean, cutback: Boolean): HyroxSession {
        val sets = setsFor(4, taper, cutback)
        val sand = round(d.sandbagKg * f, 2.5)
        val wb = d.wallBallKg.toDouble()
        val blocks = listOf(
            StationBlock("hx_sandbag_lunge", sets, 20, sand, "Reps are steps. Race weight is ${d.sandbagKg} kg for 100 m. Stay tall, knee lightly touches the floor."),
            StationBlock("hx_wall_ball", sets, 20, wb, "Race weight is ${d.wallBallKg} kg. Race reps are 75 to 100 depending on the season and division, so check the rulebook. Break the set before you have to."),
            StationBlock("hx_burpee_bj", sets, 10, 0.0, "Steady rhythm. Race distance is 80 m of burpee broad jumps.")
        )
        return HyroxSession(
            "STATIONS_B", "HYROX stations: lunges, wall balls, burpees", 45 + sets * 4,
            "Leg and engine station work. Rest 90 seconds between sets. Sandbag at ${(f * 100).roundToInt()}% of race weight.",
            blocks
        )
    }

    private fun engine(taper: Boolean, cutback: Boolean, paces: Paces?): HyroxSession {
        val reps = if (taper) 3 else if (cutback) 4 else 5
        val ergs = "SkiErg and rowing machine"
        return HyroxSession(
            "ENGINE", "HYROX engine: SkiErg and rower", 40 + reps * 6,
            "Warm up 8 minutes easy. Then alternate $reps x 4 minutes hard, starting with the SkiErg and switching to the rower each round, with 2 minutes easy between. Hold a pace you could repeat every round. Pull with your legs and hips, not just your arms ($ergs). Cool down 5 minutes.",
            emptyList()
        )
    }

    private fun racePace(paces: Paces?): String =
        if (paces != null) "about ${Running.fmtPace(paces.marathon)}-${Running.fmtPace(paces.threshold + 10)} /km, the pace you could hold for all 8 runs"
        else "a steady effort, 7 out of 10, the pace you could hold for all 8 runs"

    private fun compromised(d: HyroxDivision, f: Double, taper: Boolean, cutback: Boolean, paces: Paces?): HyroxSession {
        val rounds = if (taper) 3 else if (cutback) 4 else 5
        val push = round(d.pushKg * f, 5.0)
        val pull = round(d.pullKg * f, 5.0)
        val sand = round(d.sandbagKg * f, 2.5)
        val stations = listOf(
            "SkiErg 500 m", "sled push 50 m at ${Calc.kg(push)} kg", "sled pull 50 m at ${Calc.kg(pull)} kg",
            "burpee broad jumps 40 m", "row 500 m", "sandbag lunges 50 m at ${Calc.kg(sand)} kg"
        )
        val lines = (1..rounds).joinToString(" ") { i -> "Round $i: run 1 km, then ${stations[(i - 1) % stations.size]}." }
        return HyroxSession(
            "COMPROMISED", "HYROX run and station rounds", 25 + rounds * 9,
            "This trains running on tired legs, which is what HYROX actually is. Warm up 10 minutes. $lines Run pace: ${racePace(paces)}. Rest 90 seconds between rounds. Cool down 5 minutes.",
            emptyList()
        )
    }

    private fun sim(d: HyroxDivision, paces: Paces?): HyroxSession {
        return HyroxSession(
            "HALF_SIM", "HYROX half simulation", 60,
            "Half race simulation at race loads. Do 4 rounds of: run 1 km, then SkiErg 1000 m, sled push 50 m (${d.pushKg} kg), sled pull 50 m (${d.pullKg} kg). Run pace: ${racePace(paces)}. Do not stop between runs and stations except to change equipment. Note your total time and compare it next time. A full simulation is best done only once and at least two weeks before the event.",
            emptyList()
        )
    }

    fun raceDay(divisionName: String): HyroxSession {
        val d = division(divisionName)
        return HyroxSession(
            "RACE", "HYROX race day: ${d.name}", 90,
            "Race day. 8 x 1 km runs, each followed by a station: SkiErg 1000 m, sled push 50 m (${d.pushKg} kg), sled pull 50 m (${d.pullKg} kg), burpee broad jumps 80 m, row 1000 m, farmers carry 200 m (${d.farmersKgEach} kg each hand), sandbag lunges 100 m (${d.sandbagKg} kg), wall balls (${d.wallBallKg} kg). Start the first run slower than feels right.",
            emptyList()
        )
    }
}
