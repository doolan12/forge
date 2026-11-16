package com.doolan.forge.data

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class HybridOptions(
    val gym: GenOptions?,
    val run: RunOptions?,
    val hyrox: HyroxOptions?,
    val primary: String,          // "Gym", "Running" or "HYROX"
    val weeks: Int,
    val startNextWeek: Boolean,
    val eventDay: Long?           // race date for the primary goal, if there is one
)

/** One session in the repeating week, before it has a day. */
class SchedItem(
    val comp: String,             // Gym, Running, HYROX
    val label: String,
    val legs: Int,                // 0 to 3, how hard it is on the legs
    val hard: Boolean,
    val long: Boolean,
    val key: Boolean              // a key session of the primary goal
)

class SchedResult(
    val weekday: IntArray,        // chosen weekday (1 = Monday) for each item
    val placement: List<String>,  // plain English reason for each item
    val conflicts: List<String>
)

/**
 * Places the week's sessions on the user's training days.
 * Rules: heavy leg days are kept apart, hard days avoid hard days, the long run avoids heavy leg days,
 * and the primary goal's key sessions are protected most. Every possible arrangement is scored and the best one is used.
 */
object Scheduler {

    private fun pairCost(a: SchedItem, b: SchedItem): Double {
        var c = 1.0
        val ls = a.legs + b.legs
        if (ls >= 6) c += 14.0 else if (ls == 5) c += 9.0 else if (ls == 4) c += 5.0
        if (a.hard && b.hard) c += 5.0
        if ((a.long && b.legs >= 2) || (b.long && a.legs >= 2)) c += 6.0
        if (a.key || b.key) c *= 1.5
        return c
    }

    fun place(items: List<SchedItem>, days: List<Int>): SchedResult {
        val n = items.size
        val d = days.size
        val best = IntArray(n)
        var bestCost = Double.MAX_VALUE
        val cur = IntArray(n) { -1 }          // index into days
        val used = BooleanArray(d)

        fun cost(): Double {
            var total = 0.0
            for (i in 0 until n) {
                val wi = days[cur[i]]
                if (items[i].long && wi >= 6) total -= 1.5
                for (j in 0 until n) {
                    if (i == j) continue
                    val wj = days[cur[j]]
                    if (wj == wi % 7 + 1) total += pairCost(items[i], items[j])
                }
            }
            return total
        }

        fun dfs(i: Int) {
            if (i == n) {
                val c = cost()
                if (c < bestCost - 1e-9) {
                    bestCost = c
                    for (k in 0 until n) best[k] = days[cur[k]]
                }
                return
            }
            for (k in 0 until d) {
                if (used[k]) continue
                used[k] = true
                cur[i] = k
                dfs(i + 1)
                used[k] = false
            }
        }
        dfs(0)

        val weekday = best.copyOf()
        fun itemAt(day: Int): SchedItem? {
            val idx = weekday.indexOf(day)
            return if (idx >= 0) items[idx] else null
        }

        val conflicts = ArrayList<String>()
        val placement = ArrayList<String>()
        for (i in 0 until n) {
            val wd = weekday[i]
            val prevDay = if (wd == 1) 7 else wd - 1
            val nextDay = wd % 7 + 1
            val before = itemAt(prevDay)
            val after = itemAt(nextDay)
            val sb = StringBuilder("The day before is ${before?.label ?: "a rest day"} and the day after is ${after?.label ?: "a rest day"}.")
            val neighbours = listOfNotNull(before, after)
            if (items[i].legs >= 2 && neighbours.none { it.legs >= 2 || it.long }) {
                sb.append(" Heavy leg work is kept away from other leg-heavy days and your long run, and this one has none beside it.")
            }
            if (items[i].long && neighbours.none { it.legs >= 2 }) {
                sb.append(" Your long run has no heavy leg day directly beside it.")
            }
            if (items[i].key) sb.append(" It is a key session for your main goal, so it gets a fresher slot.")
            placement.add(sb.toString())

            if (after != null) {
                val extra = pairCost(items[i], after) - 1.0
                if (extra >= 5.0) {
                    conflicts.add(
                        "${items[i].label} on ${Calc.weekdayShort(wd)} is directly followed by ${after.label} on ${Calc.weekdayShort(nextDay)}. " +
                            "With the days you have, these could not be separated. Adding a training day or dropping a session would help recovery."
                    )
                }
            }
        }
        return SchedResult(weekday, placement, conflicts)
    }
}

private class RunShape(val label: String, val legs: Int, val hard: Boolean, val long: Boolean)

/** Combines gym, running and HYROX into one weekly plan. */
object HybridPlanner {

    private val FALLBACKS = mapOf(
        "hx_sled_push" to listOf("leg_press", "walking_lunge", "goblet_squat"),
        "hx_sled_pull" to listOf("seated_row", "db_row"),
        "hx_farmers" to listOf("db_row", "plank"),
        "hx_sandbag_lunge" to listOf("walking_lunge", "bulgarian_split"),
        "hx_wall_ball" to listOf("goblet_squat")
    )

    fun build(
        o: HybridOptions,
        p: ProfileData,
        all: List<ExerciseEntity>,
        avail: Set<String>,
        today: Long
    ): GenResult {
        val notes = ArrayList<String>()
        val days = p.trainingDays.sorted()
        if (days.isEmpty()) return GenResult(emptyList(), listOf("Pick your training days in Profile first."))
        if (o.gym == null && o.run == null && o.hyrox == null) {
            return GenResult(emptyList(), listOf("Pick at least one thing to train."))
        }

        // ----- how many sessions of each -----
        val comps = ArrayList<String>()
        if (o.gym != null) comps.add("Gym")
        if (o.run != null) comps.add("Running")
        if (o.hyrox != null) comps.add("HYROX")
        val order = listOf(o.primary) + listOf("Gym", "Running", "HYROX").filter { it != o.primary }
        val counts = HashMap<String, Int>()
        o.gym?.let { counts["Gym"] = it.daysPerWeek.coerceIn(2, 6) }
        o.run?.let { counts["Running"] = it.runsPerWeek.coerceIn(1, 6) }
        o.hyrox?.let { counts["HYROX"] = it.sessionsPerWeek.coerceIn(1, 3) }
        val floor = mapOf("Gym" to 2, "Running" to 1, "HYROX" to 1)
        val dropped = HashSet<String>()
        val trimmed = HashSet<String>()
        while (counts.values.sum() > days.size) {
            val target = order.reversed().firstOrNull { it in counts && counts[it]!! > floor[it]!! }
            if (target != null) {
                counts[target] = counts[target]!! - 1
                trimmed.add(target)
            } else {
                val drop = order.reversed().firstOrNull { it in counts && counts.size > 1 } ?: break
                counts.remove(drop)
                dropped.add(drop)
            }
        }
        if (counts.isEmpty()) return GenResult(emptyList(), listOf("Not enough training days to build a plan."))
        if (trimmed.isNotEmpty()) {
            notes.add("You have ${days.size} training days, so ${trimmed.joinToString(" and ")} sessions per week were reduced to fit. Your main goal (${o.primary}) is cut last.")
        }
        if (dropped.isNotEmpty()) {
            notes.add("There was not room for ${dropped.joinToString(" and ")} on ${days.size} training days, so it was left out. Add training days in Profile to include it.")
        }
        val useGym = o.gym != null && "Gym" in counts
        val useRun = o.run != null && "Running" in counts
        val useHyrox = o.hyrox != null && "HYROX" in counts
        val primary = if (o.primary in counts) o.primary else counts.keys.first()

        // ----- weeks, event and taper -----
        val startWeek = Calc.weekStart(today) + if (o.startNextWeek) 7 else 0
        var weeks = o.weeks
        var event = o.eventDay
        if (primary == "Gym" || (primary == "Running" && o.run?.distance == "General fitness")) event = null
        var taper = 0
        if (event != null) {
            if (event < startWeek + 14) {
                notes.add("That event date is too close to build a plan for. Pick a date at least 3 weeks after the start.")
                return GenResult(emptyList(), notes)
            }
            weeks = ceil((event - startWeek + 1) / 7.0).toInt().coerceIn(3, 24)
            if (event - startWeek + 1 > 24 * 7) {
                notes.add("The event is more than 24 weeks away, so this plan covers the first 24 weeks only. Build a new plan later for the final stretch.")
                event = null
            } else {
                taper = if (primary == "Running" && o.run?.distance == "Marathon" && weeks >= 8) 2 else 1
            }
        }

        // ----- paces -----
        val paces = if (useRun || useHyrox) Running.pacesFrom(p) else null
        if ((useRun || useHyrox) && paces == null) {
            notes.add("No race time in Profile, so running is described by effort instead of pace. Add a recent 5K or 10K time to get exact paces.")
        }
        if (paces != null && useRun) Running.predictionNote(paces, o.run!!.distance)?.let { notes.add(it) }

        // ----- weekly structure -----
        val items = ArrayList<SchedItem>()
        val gymN = counts["Gym"] ?: 0
        var gymOpts: GenOptions? = null
        var gymCount = 0
        if (useGym) {
            var g = o.gym!!
            var n = gymN
            if (g.goal == "Hypertrophy" && n < Generator.daysRange(g.goal, g.split).first) {
                notes.add("${g.split} needs more days than you have free, so the gym part uses a Full Body split instead.")
                g = g.copy(split = "Full Body")
            }
            n = n.coerceIn(Generator.daysRange(g.goal, g.split).first, Generator.daysRange(g.goal, g.split).last)
            val shapes = Generator.dayShapes(g.goal, g.split, n)
            shapes.forEach {
                items.add(SchedItem("Gym", it.title, it.legs, it.legs >= 2, false, primary == "Gym" && it.legs >= 2))
            }
            gymOpts = g.copy(daysPerWeek = n)
            gymCount = shapes.size
        }
        val runKinds = if (useRun) Running.kindsFor(counts["Running"]!!) else emptyList()
        runKinds.forEach { k ->
            val shape = when (k) {
                "LONG" -> RunShape("Long run", 2, true, true)
                "QUALITY" -> RunShape("Quality run", 1, true, false)
                "TEMPO" -> RunShape("Tempo run", 1, true, false)
                else -> RunShape("Easy run", 1, false, false)
            }
            items.add(
                SchedItem(
                    "Running", shape.label, shape.legs, shape.hard, shape.long,
                    primary == "Running" && (shape.hard || shape.long)
                )
            )
        }
        val hxKinds: List<Triple<String, Int, Boolean>> = if (useHyrox) when (counts["HYROX"]!!) {
            1 -> listOf(Triple("HYROX run and stations", 2, true))
            2 -> listOf(Triple("HYROX stations", 3, true), Triple("HYROX run and stations", 2, true))
            else -> listOf(Triple("HYROX stations", 3, true), Triple("HYROX engine session", 1, true), Triple("HYROX run and stations", 2, true))
        } else emptyList()
        hxKinds.forEach { (label, legs, hard) ->
            items.add(SchedItem("HYROX", label, legs, hard, false, primary == "HYROX"))
        }

        if (items.size > days.size) {
            notes.add("There are more sessions than training days. Add training days in Profile or choose fewer sessions.")
            return GenResult(emptyList(), notes)
        }
        val sched = Scheduler.place(items, days)
        sched.conflicts.distinct().take(3).forEach { notes.add(it) }
        notes.add("Hard sessions are placed so heavy leg days stay apart and your long run is not next to a heavy leg day. Your main goal (${primary}) gets the freshest days.")

        val sessions = ArrayList<GenSession>()
        val weekStartFor = { w: Int -> startWeek + 7L * (w - 1) }

        // ----- gym -----
        if (useGym && gymOpts != null) {
            val gymDays = (0 until gymCount).map { sched.weekday[it] }
            val go = gymOpts.copy(
                weeks = weeks, startNextWeek = o.startNextWeek, days = gymDays,
                legCut = useRun || useHyrox
            )
            val r = Generator.generate(go, p, all, avail, today)
            notes.addAll(r.notes)
            r.sessions.forEach { g ->
                val idx = gymDays.indexOf(java.time.LocalDate.ofEpochDay(g.dayEpoch).dayOfWeek.value)
                val extra = if (idx >= 0) " " + sched.placement[idx] else ""
                sessions.add(g.copy(reason = g.reason + extra))
            }
        }

        // ----- running -----
        if (useRun) {
            val ro = o.run!!.copy(runsPerWeek = counts["Running"]!!)
            val peak = max(Running.peakKm(ro, p) * (if (primary == "Running") 1.0 else 0.75), ro.currentWeeklyKm)
            val km = Running.weeklyKm(weeks, taper, ro.currentWeeklyKm, peak)
            val reachedBody = km.filterIndexed { i, _ -> Running.phase(i + 1, weeks, taper) != "Taper" }.maxOrNull() ?: 0.0
            if (ro.distance != "General fitness" && reachedBody < peak * 0.8) {
                notes.add("In $weeks weeks the running builds to about ${reachedBody.toInt()} km a week, below the usual ${peak.toInt()} km for a ${ro.distance}. Weekly distance only rises about 8% a week to protect you from injury, so a longer plan would be safer.")
            }
            val first = items.indexOfFirst { it.comp == "Running" }
            for (w in 1..weeks) {
                val runs = Running.week(ro, w, weeks, taper, km[w - 1], paces)
                runs.forEachIndexed { i, rs ->
                    val date = weekStartFor(w) + (sched.weekday[first + i] - 1)
                    if (date < today) return@forEachIndexed
                    val ph = Running.phase(w, weeks, taper)
                    sessions.add(
                        GenSession(
                            date, w, rs.title, runReason(rs, ph, w, weeks, ro.distance) + " " + sched.placement[first + i],
                            rs.minutes, emptyList(), "RUN", rs.workout
                        )
                    )
                }
            }
        }

        // ----- HYROX -----
        if (useHyrox) {
            val ho = o.hyrox!!.copy(sessionsPerWeek = counts["HYROX"]!!)
            val first = items.indexOfFirst { it.comp == "HYROX" }
            val byId = all.associateBy { it.id }
            val missing = HashSet<String>()
            for (w in 1..weeks) {
                val list = Hyrox.week(ho, w, weeks, taper, paces)
                list.forEachIndexed { i, hs ->
                    val date = weekStartFor(w) + (sched.weekday[first + i] - 1)
                    if (date < today) return@forEachIndexed
                    val ph = Running.phase(w, weeks, taper)
                    val exs = ArrayList<GenExercise>()
                    hs.stations.forEach { b ->
                        var ex = byId[b.exerciseId]
                        var note = b.note
                        if (ex == null || !ex.usableWith(avail)) {
                            val sub = FALLBACKS[b.exerciseId].orEmpty().mapNotNull { byId[it] }.firstOrNull { it.usableWith(avail) }
                            if (sub != null) {
                                missing.add(byId[b.exerciseId]?.name ?: b.exerciseId)
                                note = "You do not have the equipment for ${byId[b.exerciseId]?.name ?: "this station"}, so this is a substitute. Pick a challenging weight."
                                ex = sub
                            } else ex = null
                        }
                        if (ex != null) {
                            val substituted = ex.id != b.exerciseId
                            exs.add(
                                GenExercise(
                                    ex, "STATION", b.sets, if (substituted) 8 else b.reps, if (substituted) 12 else b.reps, 2,
                                    120, if (substituted) 0.0 else b.weightKg, "", note
                                )
                            )
                        }
                    }
                    sessions.add(
                        GenSession(
                            date, w, hs.title, hyroxReason(hs, ph, w, weeks) + " " + sched.placement[first + i],
                            hs.minutes, exs, "HYROX", hs.workout
                        )
                    )
                }
            }
            if (missing.isNotEmpty()) {
                notes.add("No equipment for ${missing.joinToString(", ")}, so substitutes are used. Turn the equipment on in Profile if you have it.")
            }
            notes.add(Hyrox.raceNote())
        }

        // ----- event day and the days before it -----
        val ev = event
        var result: List<GenSession> = sessions
        if (ev != null) {
            val kept = ArrayList<GenSession>()
            for (s in sessions) {
                val d = s.dayEpoch
                when {
                    d >= ev -> {}
                    d == ev - 1L -> if (s.discipline == "RUN" && s.title.startsWith("Easy run")) kept.add(s)
                    d >= ev - 3L -> if (s.discipline == "RUN") kept.add(s)
                    else -> kept.add(s)
                }
            }
            val race: GenSession? = if (primary == "Running") {
                val rs = Running.raceSession(o.run!!.distance, paces)
                GenSession(ev, weeks, rs.title, "Your event. Everything before it is built to have you fresh and fit on this day.", rs.minutes, emptyList(), "RUN", rs.workout)
            } else if (primary == "HYROX") {
                val hs = Hyrox.raceDay(o.hyrox!!.division)
                GenSession(ev, weeks, hs.title, "Your event. Everything before it is built to have you fresh and fit on this day.", hs.minutes, emptyList(), "HYROX", hs.workout)
            } else null
            if (race != null) kept.add(race)
            notes.add("The last 3 days before the event only have light running so you arrive fresh.")
            result = kept
        }

        val sorted = result.sortedWith(compareBy({ it.dayEpoch }, { it.discipline }))
        return GenResult(sorted, notes.distinct())
    }

    private fun runReason(rs: RunSession, phase: String, w: Int, weeks: Int, distance: String): String {
        val why = when (rs.kind) {
            "EASY" -> "Easy running builds your aerobic base and recovers you from harder days without adding stress."
            "LONG" -> "The long run builds endurance and the durability to cope with ${if (distance == "General fitness") "longer distances" else "your $distance"}."
            "QUALITY" -> "This is your main fast session of the week, which raises how quickly you can run."
            "TEMPO" -> "Steady and tempo running raises the pace you can hold for a long time."
            else -> "Event day."
        }
        val ph = when (phase) {
            "Base" -> "Base phase: easy volume and gentle speed work."
            "Build" -> "Build phase: raising threshold fitness."
            "Peak" -> "Peak phase: practising race specific effort."
            else -> "Taper: less volume, same sharpness, so you are fresh."
        }
        val cut = if (w % 4 == 0 && phase != "Taper") " This is an easier week with about 20% less distance so your body absorbs the training." else ""
        return "$why Week $w of $weeks. $ph$cut"
    }

    private fun hyroxReason(hs: HyroxSession, phase: String, w: Int, weeks: Int): String {
        val why = when (hs.kind) {
            "STATIONS_A", "STATIONS_B" -> "Station work builds the strength endurance for the sleds, carries and lunges at close to race weight."
            "ENGINE" -> "The SkiErg and rower sessions build the aerobic engine for the erg stations without pounding your legs."
            "HALF_SIM" -> "A half simulation tests your pacing at race loads so there are no surprises on the day."
            else -> "Alternating runs and stations trains you to run well on tired legs, which is the heart of HYROX."
        }
        val ph = when (phase) {
            "Base" -> "Base phase: lighter loads, good technique."
            "Build" -> "Build phase: loads rise towards race weight."
            "Peak" -> "Peak phase: race loads and race pacing."
            else -> "Taper: fewer sets, same intensity."
        }
        val cut = if (w % 4 == 0 && phase != "Taper") " This is an easier week so you recover." else ""
        return "$why Week $w of $weeks. $ph$cut"
    }
}
