package com.doolan.forge.data

import kotlin.math.roundToInt

class HyroxSegment(val label: String, val isRun: Boolean)

class HyroxPrediction(
    val loggedCount: Int,
    val totalSec: Int,
    val complete: Boolean,
    val refs: List<Int>,
    val fadePct: Int?,
    val biggest: List<String>
)

/** Station and run split logging, with a finish time estimate from your own logged times. */
object HyroxLog {
    val SEGMENTS: List<HyroxSegment> = run {
        val st = listOf("SkiErg", "Sled push", "Sled pull", "Burpee broad jumps", "Rowing", "Farmers carry", "Sandbag lunges", "Wall balls")
        val out = ArrayList<HyroxSegment>()
        for (i in 0 until 8) {
            out.add(HyroxSegment("Run ${i + 1}", true))
            out.add(HyroxSegment(st[i], false))
        }
        out
    }
    val KINDS = listOf("Race", "Simulation", "Partial")

    /** Accepts m:ss, h:mm:ss or plain seconds. Returns null if blank or unreadable. */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(":").map { it.trim().toDoubleOrNull() ?: return null }
        val sec = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return if (sec in 5.0..7200.0) sec.roundToInt() else null
    }

    fun fmt(sec: Int): String =
        if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
        else "%d:%02d".format(sec / 60, sec % 60)

    fun decode(splits: String): List<Int> {
        val l = splits.split(",").map { it.trim().toIntOrNull() ?: 0 }
        return List(16) { l.getOrElse(it) { 0 } }
    }

    fun encode(values: List<Int>): String = values.joinToString(",")

    fun total(r: HyroxResultEntity): Int? {
        val v = decode(r.splits)
        return if (v.all { it > 0 }) v.sum() else null
    }

    /**
     * For each segment, average your last 3 logged times (newest results first). Times from
     * Partial sessions are done fresh, so they are lengthened a little (runs 5%, stations 8%).
     * That fatigue adjustment is a rough rule of thumb, not a measured figure.
     */
    fun predict(results: List<HyroxResultEntity>, division: String): HyroxPrediction {
        val mine = results.filter { it.division == division }
            .sortedByDescending { it.dateEpochDay * 100000 + it.id }
        val refs = ArrayList<Int>()
        for (i in 0 until 16) {
            val vals = mine.mapNotNull { r ->
                val v = decode(r.splits)[i]
                if (v <= 0) null
                else if (r.kind == "Partial") (v * (if (SEGMENTS[i].isRun) 1.05 else 1.08)).roundToInt() else v
            }.take(3)
            refs.add(if (vals.isEmpty()) 0 else vals.average().roundToInt())
        }
        val have = refs.count { it > 0 }
        val full = mine.firstOrNull { r -> r.kind != "Partial" && decode(r.splits).all { it > 0 } }
        var fade: Int? = null
        if (full != null) {
            val v = decode(full.splits)
            val first = v[0]
            val last = v[14]
            if (first > 0 && last > 0) fade = ((last - first) * 100.0 / first).roundToInt()
        }
        val biggest = refs.withIndex().filter { it.value > 0 && !SEGMENTS[it.index].isRun }
            .sortedByDescending { it.value }.take(3)
            .map { "${SEGMENTS[it.index].label} ${fmt(it.value)}" }
        return HyroxPrediction(have, refs.sum(), have == 16, refs, fade, biggest)
    }
}
