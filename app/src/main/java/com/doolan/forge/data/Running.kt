package com.doolan.forge.data

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Training paces in seconds per km. */
data class Paces(
    val vdot: Double,
    val easyFast: Int,
    val easySlow: Int,
    val marathon: Int,
    val threshold: Int,
    val interval: Int,
    val repetition: Int,
    val basis: String
)

data class RunOptions(
    val distance: String,        // one of Running.DISTANCES
    val runsPerWeek: Int,
    val currentWeeklyKm: Double
)

data class RunSession(
    val kind: String,            // EASY, LONG, QUALITY, TEMPO
    val title: String,
    val km: Double,
    val minutes: Int,
    val workout: String
)

/**
 * Running maths. Paces come from the Daniels/Gilbert VDOT formulas using the user's own best race time.
 * If there is no race time at all, sessions are described by effort instead of pace.
 */
object Running {
    val DISTANCES = listOf("5K", "10K", "Half marathon", "Marathon", "General fitness")

    fun distanceKm(name: String): Double = when (name) {
        "5K" -> 5.0
        "10K" -> 10.0
        "Half marathon" -> 21.0975
        "Marathon" -> 42.195
        else -> 0.0
    }

    /** "22:30", "1:45:10" or just minutes ("25"). Returns seconds or null. */
    fun parseTime(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(":").map { it.trim() }
        if (parts.any { it.toDoubleOrNull() == null }) return null
        val nums = parts.map { it.toDouble() }
        val sec = when (nums.size) {
            1 -> nums[0] * 60.0
            2 -> nums[0] * 60.0 + nums[1]
            3 -> nums[0] * 3600.0 + nums[1] * 60.0 + nums[2]
            else -> return null
        }
        return if (sec in 600.0..86400.0) sec.roundToInt() else null
    }

    fun vdotFromRace(distM: Double, tSec: Double): Double {
        val t = tSec / 60.0
        val v = distM / t
        val vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v
        val pct = 0.8 + 0.1894393 * exp(-0.012778 * t) + 0.2989558 * exp(-0.1932605 * t)
        return vo2 / pct
    }

    /** Seconds per km that uses the given fraction of VDOT. */
    fun paceAtFraction(vdot: Double, fraction: Double): Int {
        val vo2 = fraction * vdot
        val c = 4.60 + vo2
        val v = (-0.182258 + sqrt(0.182258 * 0.182258 + 4.0 * 0.000104 * c)) / (2.0 * 0.000104)
        return (60000.0 / v).roundToInt()
    }

    /** Predicted race time in seconds for a distance, given a VDOT. */
    fun predictSec(vdot: Double, distM: Double): Int {
        var lo = 120.0
        var hi = 30000.0
        repeat(60) {
            val mid = (lo + hi) / 2.0
            if (vdotFromRace(distM, mid) > vdot) lo = mid else hi = mid
        }
        return ((lo + hi) / 2.0).roundToInt()
    }

    fun pacesFor(vdot: Double, basis: String): Paces {
        val mp = (predictSec(vdot, 42195.0) / 42.195).roundToInt()
        return Paces(
            vdot = vdot,
            easyFast = paceAtFraction(vdot, 0.66),
            easySlow = paceAtFraction(vdot, 0.59),
            marathon = mp,
            threshold = paceAtFraction(vdot, 0.88),
            interval = paceAtFraction(vdot, 0.97),
            repetition = paceAtFraction(vdot, 1.05),
            basis = basis
        )
    }

    /** Uses the best of the user's logged race times. Null when the profile has none. */
    fun pacesFrom(p: ProfileData): Paces? {
        val races = listOf(
            Triple("5K", p.pb5k, 5000.0), Triple("10K", p.pb10k, 10000.0),
            Triple("half marathon", p.pbHalf, 21097.5), Triple("marathon", p.pbMarathon, 42195.0)
        )
        var best: Pair<Double, String>? = null
        for ((name, text, dist) in races) {
            val sec = parseTime(text) ?: continue
            val v = vdotFromRace(dist, sec.toDouble())
            if (v in 20.0..85.0 && (best == null || v > best.first)) best = v to "$name of ${Calc.durationLabel(sec)}"
        }
        return best?.let { pacesFor(it.first, it.second) }
    }

    fun fmtPace(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

    fun fmtRange(fast: Int, slow: Int): String = "${fmtPace(fast)}-${fmtPace(slow)}"

    /** Heart rate zones from 220 minus age. This is only an estimate. */
    fun hrZones(age: Int): List<Pair<String, String>> {
        val mx = 220 - age
        fun z(a: Double, b: Double) = "${(mx * a).roundToInt()}-${(mx * b).roundToInt()} bpm"
        return listOf(
            "Zone 1 (very easy)" to z(0.50, 0.60),
            "Zone 2 (easy)" to z(0.60, 0.70),
            "Zone 3 (steady)" to z(0.70, 0.80),
            "Zone 4 (threshold)" to z(0.80, 0.90),
            "Zone 5 (hard)" to z(0.90, 1.00)
        )
    }

    // ---------- planning ----------
    private fun level(p: ProfileData): Int = when (p.runningExp) {
        "None", "Beginner" -> 0
        "Regular runner" -> 1
        else -> 2
    }

    fun peakKm(o: RunOptions, p: ProfileData): Double {
        val l = level(p)
        return when (o.distance) {
            "5K" -> listOf(25.0, 35.0, 50.0)[l]
            "10K" -> listOf(30.0, 45.0, 60.0)[l]
            "Half marathon" -> listOf(40.0, 55.0, 75.0)[l]
            "Marathon" -> listOf(50.0, 65.0, 90.0)[l]
            else -> max(o.currentWeeklyKm * 1.3, 15.0)
        }
    }

    private fun longCap(distance: String) = when (distance) {
        "5K" -> 14.0
        "10K" -> 18.0
        "Half marathon" -> 24.0
        "Marathon" -> 32.0
        else -> 16.0
    }

    /** Weekly kilometres. Rises at most 8% a week, with an easier week every 4th week and a taper at the end. */
    fun weeklyKm(weeks: Int, taperWeeks: Int, start: Double, peak: Double): List<Double> {
        val out = ArrayList<Double>()
        var build = max(8.0, start)
        for (w in 1..weeks) {
            val fromEnd = weeks - w + 1
            val km = when {
                taperWeeks > 0 && fromEnd <= taperWeeks -> {
                    val f = if (taperWeeks == 1) 0.6 else if (fromEnd == 2) 0.75 else 0.5
                    min(build, peak) * f
                }
                w % 4 == 0 -> build * 0.8
                else -> {
                    if (w > 1) build = min(peak, build * 1.08)
                    build
                }
            }
            out.add(km)
        }
        return out
    }

    fun phase(w: Int, weeks: Int, taperWeeks: Int): String {
        val fromEnd = weeks - w + 1
        if (taperWeeks > 0 && fromEnd <= taperWeeks) return "Taper"
        val body = max(1, weeks - taperWeeks)
        val frac = w.toDouble() / body
        return when {
            frac <= 0.35 -> "Base"
            frac <= 0.75 -> "Build"
            else -> "Peak"
        }
    }

    fun kindsFor(runs: Int): List<String> = when (runs.coerceIn(1, 6)) {
        1 -> listOf("LONG")
        2 -> listOf("EASY", "LONG")
        3 -> listOf("EASY", "QUALITY", "LONG")
        4 -> listOf("EASY", "QUALITY", "EASY", "LONG")
        5 -> listOf("EASY", "QUALITY", "EASY", "TEMPO", "LONG")
        else -> listOf("EASY", "QUALITY", "EASY", "TEMPO", "EASY", "LONG")
    }

    private fun roundHalf(x: Double): Double = (x * 2.0).roundToInt() / 2.0

    private fun kmText(km: Double): String =
        if (km % 1.0 == 0.0) "${km.toInt()}" else String.format(java.util.Locale.US, "%.1f", km)

    private fun minutesFor(km: Double, paces: Paces?): Int {
        val perKm = if (paces != null) (paces.easyFast + paces.easySlow) / 2 else 360
        return (km * perKm / 60.0).roundToInt()
    }

    /** All the runs for one week. */
    fun week(
        o: RunOptions, w: Int, weeks: Int, taperWeeks: Int, weekKm: Double, paces: Paces?
    ): List<RunSession> {
        val kinds = kindsFor(o.runsPerWeek)
        val phase = Running.phase(w, weeks, taperWeeks)
        val cutback = w % 4 == 0 && phase != "Taper"
        val nEasy = kinds.count { it == "EASY" }
        val longShare = when (kinds.size) { 1 -> 1.0; 2 -> 0.55; 3 -> 0.42; else -> 0.32 }
        var longKm = min(weekKm * longShare, longCap(o.distance))
        val qKm = if (kinds.contains("QUALITY")) max(5.0, weekKm * 0.2) else 0.0
        val tKm = if (kinds.contains("TEMPO")) max(5.0, weekKm * 0.15) else 0.0
        val rest = weekKm - longKm - qKm - tKm
        val easyKm = if (nEasy > 0) max(3.0, rest / nEasy) else 0.0
        if (nEasy == 0 && kinds.size == 1) longKm = weekKm

        val out = ArrayList<RunSession>()
        var easyDone = 0
        for (k in kinds) {
            when (k) {
                "EASY" -> {
                    val km = roundHalf(easyKm)
                    easyDone++
                    val strides = easyDone == 1 && phase != "Taper" && !cutback
                    out.add(easyRun(km, paces, strides))
                }
                "LONG" -> out.add(longRun(roundHalf(longKm), o, paces, phase, cutback))
                "QUALITY" -> out.add(quality(roundHalf(qKm), o, paces, phase))
                else -> out.add(tempo(roundHalf(tKm), paces, phase))
            }
        }
        return out
    }

    private fun easyRun(km: Double, paces: Paces?, strides: Boolean): RunSession {
        val pace = if (paces != null) "Pace ${fmtRange(paces.easyFast, paces.easySlow)} /km, or heart rate zone 2." else "Effort 4 out of 10."
        val sb = StringBuilder("Easy run, ${kmText(km)} km. $pace You should be able to talk in full sentences. If you can't, slow down.")
        if (strides) sb.append(" Finish with 5 strides: 20 seconds fast but relaxed, then 60 seconds walking or jogging.")
        return RunSession("EASY", "Easy run ${kmText(km)} km", km, minutesFor(km, paces), sb.toString())
    }

    private fun longRun(km: Double, o: RunOptions, paces: Paces?, phase: String, cutback: Boolean): RunSession {
        val pace = if (paces != null) "Easy pace ${fmtRange(paces.easyFast, paces.easySlow)} /km." else "Effort 4 to 5 out of 10."
        val sb = StringBuilder("Long run, ${kmText(km)} km. $pace Keep it steady and start slow.")
        var title = "Long run ${kmText(km)} km"
        val mpBlock = (o.distance == "Marathon" || o.distance == "Half marathon") && phase == "Peak" && !cutback && km >= 14
        if (mpBlock) {
            val blk = roundHalf(min(12.0, km * 0.3))
            val mp = paces?.let { " (${fmtPace(it.marathon)} /km)" } ?: ""
            sb.append(" Run the last ${kmText(blk)} km at marathon pace$mp.")
            title += " with marathon pace"
        }
        if (km >= 14) sb.append(" Practise your race fuelling: water, and about one gel every 40 minutes after the first hour.")
        return RunSession("LONG", title, km, minutesFor(km, paces), sb.toString())
    }

    private fun effortOrPace(paces: Paces?, sec: Int?, effort: String): String =
        if (paces != null && sec != null) "${fmtPace(sec)} /km" else effort

    private fun quality(km: Double, o: RunOptions, paces: Paces?, phase: String): RunSession {
        val work = max(2.0, km - 3.0)
        val total = minutesFor(km, paces)
        return when (phase) {
            "Base" -> {
                val reps = (work * 2).roundToInt().coerceIn(5, 10)
                val fast = effortOrPace(paces, paces?.interval, "hard, about 8 out of 10")
                RunSession(
                    "QUALITY", "Fartlek $reps x 1 min", km, total,
                    "Warm up 10 minutes easy. Then $reps x 1 minute fast ($fast), each followed by 2 minutes easy jogging. Cool down 10 minutes easy. This builds speed without the stress of a hard track session."
                )
            }
            "Build" -> {
                val t = effortOrPace(paces, paces?.threshold, "comfortably hard, 7 out of 10")
                val workMin = work * (paces?.threshold ?: 300) / 60.0
                if (workMin >= 16) {
                    val reps = (workMin / 8.0).roundToInt().coerceIn(2, 5)
                    RunSession(
                        "QUALITY", "Threshold $reps x 8 min", km, total,
                        "Warm up 10 minutes easy. Then $reps x 8 minutes at threshold ($t), with 2 minutes easy jogging between. Cool down 10 minutes easy. Threshold feels comfortably hard: you can say a few words, not chat."
                    )
                } else {
                    val mins = workMin.roundToInt().coerceAtLeast(10)
                    RunSession(
                        "QUALITY", "Threshold $mins min", km, total,
                        "Warm up 10 minutes easy. Then $mins minutes continuous at threshold ($t). Cool down 10 minutes easy."
                    )
                }
            }
            "Peak" -> peakQuality(km, work, o, paces, total)
            else -> {
                val t = effortOrPace(paces, paces?.threshold, "comfortably hard, 7 out of 10")
                RunSession(
                    "QUALITY", "Taper sharpener", km, total,
                    "Warm up 10 minutes easy. Then 3 x 5 minutes at threshold ($t) with 2 minutes easy between. Cool down easy. Keep the pace, cut the volume, and finish feeling fresh."
                )
            }
        }
    }

    private fun peakQuality(km: Double, work: Double, o: RunOptions, paces: Paces?, total: Int): RunSession {
        return when (o.distance) {
            "Marathon" -> {
                val n = if (work < 9) 2 else 3
                val x = roundHalf(work / n)
                val mp = effortOrPace(paces, paces?.marathon, "marathon effort, about 6 out of 10")
                RunSession(
                    "QUALITY", "Marathon pace $n x ${kmText(x)} km", km, total,
                    "Warm up 2 km easy. Then $n x ${kmText(x)} km at marathon pace ($mp), 2 minutes easy jogging between. Cool down 1.5 km. Learn what race pace feels like."
                )
            }
            "Half marathon" -> {
                val reps = (work / 2.0).roundToInt().coerceIn(2, 5)
                val t = effortOrPace(paces, paces?.threshold, "comfortably hard, 7 out of 10")
                RunSession(
                    "QUALITY", "Threshold $reps x 2 km", km, total,
                    "Warm up 2 km easy. Then $reps x 2 km at threshold ($t), 2 minutes easy jogging between. Cool down 1.5 km."
                )
            }
            "10K" -> {
                val reps = work.roundToInt().coerceIn(3, 8)
                val i = effortOrPace(paces, paces?.interval, "hard, 8 out of 10")
                RunSession(
                    "QUALITY", "Intervals $reps x 1000 m", km, total,
                    "Warm up 2 km easy. Then $reps x 1000 m at interval pace ($i), 2:30 easy jogging between. Cool down 1.5 km."
                )
            }
            else -> {
                val reps = (work / 0.8).roundToInt().coerceIn(4, 10)
                val i = effortOrPace(paces, paces?.interval, "hard, 8 out of 10")
                RunSession(
                    "QUALITY", "Intervals $reps x 800 m", km, total,
                    "Warm up 2 km easy. Then $reps x 800 m at interval pace ($i), jogging 400 m between. Cool down 1.5 km."
                )
            }
        }
    }

    private fun tempo(km: Double, paces: Paces?, phase: String): RunSession {
        val work = max(2.0, km - 3.0)
        val total = minutesFor(km, paces)
        if (phase == "Base") {
            val mins = (work * 5.5).roundToInt().coerceIn(15, 30)
            val s = paces?.let { " (about ${fmtPace((it.marathon + it.threshold) / 2)} /km)" } ?: ""
            return RunSession(
                "TEMPO", "Steady run $mins min", km, total,
                "Warm up 10 minutes easy. Then $mins minutes steady$s: faster than easy but well under threshold, about 6 out of 10. Cool down 10 minutes easy."
            )
        }
        val mins = (work * (paces?.threshold ?: 300) / 60.0).roundToInt().coerceIn(12, 40)
        val t = effortOrPace(paces, paces?.threshold, "comfortably hard, 7 out of 10")
        return RunSession(
            "TEMPO", "Tempo $mins min", km, total,
            "Warm up 10 minutes easy. Then $mins minutes continuous at tempo ($t). Cool down 10 minutes easy."
        )
    }

    fun raceSession(distance: String, paces: Paces?): RunSession {
        val km = distanceKm(distance)
        val pred = paces?.let { " Your best race predicts about ${Calc.durationLabel(predictSec(it.vdot, km * 1000.0))} on a good day, but start a little slower than that." } ?: ""
        return RunSession(
            "RACE", "Race day: $distance", km, (km * 6).roundToInt(),
            "Race day. Warm up 10 minutes easy with 3 short strides. Start controlled for the first third, then build.$pred Eat the same breakfast you practised in training."
        )
    }

    /** Quick summary used in the plan notes. */
    fun predictionNote(paces: Paces, distance: String): String? {
        val km = distanceKm(distance)
        if (km <= 0.0) return null
        val t = predictSec(paces.vdot, km * 1000.0)
        return "Your ${paces.basis} gives a VDOT of ${"%.1f".format(paces.vdot)}, which predicts about ${Calc.durationLabel(t)} for the $distance. Paces in this plan are worked out from that."
    }

    @Suppress("unused")
    private fun riegel(t1: Double, d1: Double, d2: Double): Double = t1 * (d2 / d1).pow(1.06)
}
