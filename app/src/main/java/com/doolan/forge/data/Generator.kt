package com.doolan.forge.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GenOptions(
    val goal: String,            // "Hypertrophy" or "Strength"
    val split: String,           // hypertrophy only
    val method: String,          // strength only
    val daysPerWeek: Int,
    val weeks: Int,
    val startNextWeek: Boolean,
    val avoid: Set<String>,      // exercise ids the user does not want
    val days: List<Int>? = null, // fixed weekday for each weekly session (1 = Monday), set by the hybrid planner
    val legCut: Boolean? = null  // null = decide from the profile's secondary goals
)

data class GenExercise(
    val exercise: ExerciseEntity,
    val role: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val rir: Int,
    val restSec: Int,
    val weightKg: Double,
    val setPlan: String,
    val note: String
)

data class GenSession(
    val dayEpoch: Long,
    val weekNo: Int,
    val title: String,
    val reason: String,
    val minutes: Int,
    val exercises: List<GenExercise>,
    val discipline: String = "GYM",
    val workout: String = ""
)

/** How hard a gym day is on the legs, used by the scheduler. 0 = none, 3 = very heavy. */
data class DayShape(val title: String, val legs: Int)

data class GenResult(val sessions: List<GenSession>, val notes: List<String>)

/**
 * Builds a programme from slots (for example "a main chest lift") instead of fixed workouts.
 * Each slot is filled from the exercise library using only equipment the user has switched on.
 */
object Generator {
    val HYP_SPLITS = listOf("Push/Pull/Legs", "Upper/Lower", "Full Body", "Bro Split")
    val STRENGTH_METHODS = listOf("Linear progression", "5/3/1", "DUP")

    private val LOWER = setOf("Quads", "Hamstrings", "Glutes", "Calves")
    private val BAD_CATS = setOf("HYROX", "Running drills", "Mobility")
    private val CANON = mapOf(
        "squat" to "back_squat", "bench" to "bb_bench", "deadlift" to "conventional_dl", "ohp" to "ohp"
    )

    fun daysRange(goal: String, split: String): IntRange =
        if (goal == "Strength") 2..5 else when (split) {
            "Push/Pull/Legs" -> 3..6
            "Upper/Lower" -> 2..6
            "Full Body" -> 2..4
            else -> 4..5
        }

    /** Exercises the user can choose to avoid. */
    val AVOIDABLE = listOf(
        "back_squat", "front_squat", "bb_bench", "conventional_dl", "ohp", "bb_row", "pullup",
        "rdl", "walking_lunge", "bulgarian_split", "hip_thrust", "skullcrusher"
    )

    // ---------- slot templates ----------
    private class Slot(val muscle: String, val role: String, val prefs: List<String>, val lift: String? = null)
    private class Day(val title: String, val slots: List<Slot>)
    private class Resolved(val slot: Slot, val ex: ExerciseEntity)
    private class Params(val sets: Int, val repsMin: Int, val repsMax: Int, val rest: Int)

    private fun s(muscle: String, role: String, vararg prefs: String) = Slot(muscle, role, prefs.toList())
    private fun lift(key: String, muscle: String, role: String, vararg prefs: String) =
        Slot(muscle, role, prefs.toList(), key)

    private val PUSH = Day("Push", listOf(
        s("Chest", "MAIN", "bb_bench", "db_bench", "machine_chest_press"),
        s("Chest", "SECONDARY", "inc_db_press", "inc_bb_press", "cable_fly"),
        s("Shoulders", "SECONDARY", "ohp", "db_shoulder_press", "machine_shoulder_press"),
        s("Shoulders", "ISO", "lateral_raise", "cable_lateral"),
        s("Triceps", "ISO", "pushdown", "oh_tri_ext", "skullcrusher"),
        s("Triceps", "ISO", "oh_tri_ext", "skullcrusher", "pushdown")
    ))
    private val PULL = Day("Pull", listOf(
        s("Back", "MAIN", "pullup", "lat_pulldown", "chinup"),
        s("Back", "SECONDARY", "bb_row", "seated_row", "db_row"),
        s("Back", "SECONDARY", "seated_row", "db_row", "straight_arm_pd"),
        s("Shoulders", "ISO", "rear_delt_fly", "db_rear_fly", "face_pull"),
        s("Biceps", "ISO", "db_curl", "bb_curl", "cable_curl"),
        s("Biceps", "ISO", "hammer_curl", "ez_curl")
    ))
    private val LEGS = Day("Legs", listOf(
        s("Quads", "MAIN", "back_squat", "hack_squat", "leg_press", "smith_squat", "goblet_squat"),
        s("Hamstrings", "MAIN", "rdl", "db_rdl", "lying_leg_curl"),
        s("Quads", "SECONDARY", "leg_press", "hack_squat", "bulgarian_split", "walking_lunge"),
        s("Hamstrings", "ISO", "lying_leg_curl", "seated_leg_curl"),
        s("Glutes", "SECONDARY", "hip_thrust", "glute_bridge"),
        s("Calves", "ISO", "standing_calf", "seated_calf", "leg_press_calf", "bw_calf_raise")
    ))
    private val UPPER = Day("Upper", listOf(
        s("Chest", "MAIN", "bb_bench", "db_bench", "machine_chest_press"),
        s("Back", "MAIN", "bb_row", "seated_row", "pullup", "lat_pulldown"),
        s("Shoulders", "SECONDARY", "db_shoulder_press", "ohp", "machine_shoulder_press"),
        s("Back", "SECONDARY", "lat_pulldown", "pullup", "seated_row"),
        s("Chest", "ISO", "cable_fly", "pec_deck"),
        s("Biceps", "ISO", "db_curl", "cable_curl"),
        s("Triceps", "ISO", "pushdown", "oh_tri_ext")
    ))
    private val LOWER_DAY = Day("Lower", listOf(
        s("Quads", "MAIN", "back_squat", "hack_squat", "leg_press", "smith_squat"),
        s("Hamstrings", "MAIN", "rdl", "db_rdl"),
        s("Quads", "SECONDARY", "leg_press", "bulgarian_split", "walking_lunge"),
        s("Hamstrings", "ISO", "lying_leg_curl", "seated_leg_curl"),
        s("Glutes", "ISO", "hip_thrust", "glute_bridge"),
        s("Calves", "ISO", "standing_calf", "seated_calf", "leg_press_calf"),
        s("Core", "ISO", "cable_crunch", "plank", "hanging_knee_raise")
    ))
    private val FB_A = Day("Full body A", listOf(
        s("Quads", "MAIN", "back_squat", "hack_squat", "leg_press", "goblet_squat"),
        s("Chest", "MAIN", "bb_bench", "db_bench", "machine_chest_press"),
        s("Back", "SECONDARY", "bb_row", "seated_row", "db_row"),
        s("Shoulders", "ISO", "lateral_raise", "cable_lateral"),
        s("Biceps", "ISO", "db_curl", "cable_curl"),
        s("Triceps", "ISO", "pushdown", "oh_tri_ext")
    ))
    private val FB_B = Day("Full body B", listOf(
        s("Hamstrings", "MAIN", "rdl", "db_rdl", "lying_leg_curl"),
        s("Shoulders", "MAIN", "ohp", "db_shoulder_press", "machine_shoulder_press"),
        s("Back", "MAIN", "pullup", "lat_pulldown", "chinup"),
        s("Quads", "SECONDARY", "leg_press", "walking_lunge", "bulgarian_split"),
        s("Chest", "ISO", "cable_fly", "pec_deck", "pushup"),
        s("Core", "ISO", "plank", "cable_crunch")
    ))
    private val FB_C = Day("Full body C", listOf(
        s("Quads", "MAIN", "hack_squat", "leg_press", "back_squat"),
        s("Chest", "SECONDARY", "inc_db_press", "db_bench", "machine_chest_press"),
        s("Back", "SECONDARY", "lat_pulldown", "seated_row", "db_row"),
        s("Glutes", "SECONDARY", "hip_thrust", "glute_bridge"),
        s("Calves", "ISO", "standing_calf", "seated_calf", "leg_press_calf"),
        s("Biceps", "ISO", "hammer_curl", "ez_curl", "db_curl")
    ))
    private val BRO_CHEST = Day("Chest & Triceps", listOf(
        s("Chest", "MAIN", "bb_bench", "db_bench", "machine_chest_press"),
        s("Chest", "SECONDARY", "inc_db_press", "inc_bb_press", "cable_fly"),
        s("Chest", "ISO", "cable_fly", "pec_deck", "pushup"),
        s("Triceps", "ISO", "pushdown", "skullcrusher"),
        s("Triceps", "ISO", "oh_tri_ext", "close_grip_bench", "bench_dip")
    ))
    private val BRO_BACK = Day("Back & Biceps", listOf(
        s("Back", "MAIN", "pullup", "lat_pulldown", "chinup"),
        s("Back", "SECONDARY", "bb_row", "seated_row", "db_row"),
        s("Back", "SECONDARY", "seated_row", "db_row", "straight_arm_pd"),
        s("Biceps", "ISO", "bb_curl", "db_curl", "cable_curl"),
        s("Biceps", "ISO", "hammer_curl", "ez_curl")
    ))
    private val BRO_SHOULDERS = Day("Shoulders & Core", listOf(
        s("Shoulders", "MAIN", "ohp", "db_shoulder_press", "machine_shoulder_press"),
        s("Shoulders", "ISO", "lateral_raise", "cable_lateral"),
        s("Shoulders", "ISO", "rear_delt_fly", "db_rear_fly", "face_pull"),
        s("Core", "ISO", "cable_crunch", "hanging_knee_raise", "plank"),
        s("Core", "ISO", "plank", "dead_bug", "pallof_press")
    ))
    private val BRO_ARMS = Day("Arms", listOf(
        s("Biceps", "SECONDARY", "bb_curl", "ez_curl", "db_curl"),
        s("Triceps", "SECONDARY", "skullcrusher", "close_grip_bench", "pushdown"),
        s("Biceps", "ISO", "hammer_curl", "cable_curl"),
        s("Triceps", "ISO", "oh_tri_ext", "pushdown"),
        s("Core", "ISO", "plank", "cable_crunch")
    ))

    private fun squat(role: String = "LIFT") = lift("squat", "Quads", role, "back_squat", "front_squat", "smith_squat", "hack_squat", "leg_press")
    private fun bench(role: String = "LIFT") = lift("bench", "Chest", role, "bb_bench", "db_bench", "machine_chest_press")
    private fun dead() = lift("deadlift", "Back", "LIFT", "conventional_dl", "rdl", "db_rdl")
    private fun ohp() = lift("ohp", "Shoulders", "LIFT", "ohp", "db_shoulder_press", "machine_shoulder_press")
    private val ROW = s("Back", "SECONDARY", "bb_row", "seated_row", "db_row")
    private val PULL_A = s("Back", "SECONDARY", "pullup", "chinup", "lat_pulldown")
    private val HAM = s("Hamstrings", "SECONDARY", "rdl", "db_rdl", "lying_leg_curl")
    private val QUADS_A = s("Quads", "SECONDARY", "leg_press", "hack_squat", "bulgarian_split", "walking_lunge")
    private val CORE = s("Core", "ISO", "plank", "cable_crunch", "hanging_knee_raise")
    private val BICEPS = s("Biceps", "ISO", "db_curl", "bb_curl", "cable_curl")
    private val TRICEPS = s("Triceps", "ISO", "pushdown", "oh_tri_ext", "skullcrusher")

    private fun strengthDays(n: Int): List<Day> = when (n) {
        1, 2 -> listOf(
            Day("", listOf(squat(), bench(), ROW)),
            Day("", listOf(dead(), ohp(), PULL_A))
        )
        3 -> listOf(
            Day("", listOf(squat(), bench(), ROW)),
            Day("", listOf(dead(), ohp(), PULL_A)),
            Day("", listOf(squat("LIGHT"), bench("LIGHT"), HAM, CORE))
        )
        4 -> listOf(
            Day("", listOf(squat(), HAM, CORE)),
            Day("", listOf(bench(), ROW, TRICEPS)),
            Day("", listOf(dead(), QUADS_A, CORE)),
            Day("", listOf(ohp(), PULL_A, BICEPS))
        )
        else -> listOf(
            Day("", listOf(squat(), HAM, CORE)),
            Day("", listOf(bench(), ROW, TRICEPS)),
            Day("", listOf(dead(), QUADS_A, CORE)),
            Day("", listOf(ohp(), PULL_A, BICEPS)),
            Day("", listOf(bench("LIGHT"), ROW, PULL_A, BICEPS, TRICEPS))
        )
    }


    private fun templatesFor(strength: Boolean, split: String, n: Int): List<Day> =
        if (strength) strengthDays(n).let { t -> (0 until n).map { t[it % t.size] } }
        else {
            val cycle = when (split) {
                "Push/Pull/Legs" -> listOf(PUSH, PULL, LEGS)
                "Upper/Lower" -> listOf(UPPER, LOWER_DAY)
                "Full Body" -> listOf(FB_A, FB_B, FB_C)
                else -> if (n >= 5) listOf(BRO_CHEST, BRO_BACK, LEGS, BRO_SHOULDERS, BRO_ARMS)
                else listOf(BRO_CHEST, BRO_BACK, LEGS, BRO_SHOULDERS)
            }
            (0 until n).map { cycle[it % cycle.size] }
        }

    private fun legLoad(day: Day): Int {
        val heavy = day.slots.count {
            (it.muscle in LOWER && (it.role == "MAIN" || it.role == "LIFT")) || it.lift == "deadlift"
        }
        val any = day.slots.count { it.muscle in LOWER }
        return when {
            heavy >= 2 -> 3
            heavy == 1 -> 2
            any > 0 -> 1
            else -> 0
        }
    }

    /** The weekly pattern of gym days and how hard each is on the legs, so the scheduler can place them. */
    fun dayShapes(goal: String, split: String, n: Int): List<DayShape> {
        val strength = goal == "Strength"
        val titles = templatesFor(strength, split, n)
        return titles.mapIndexed { i, d ->
            val t = if (d.title.isEmpty()) "Strength day ${i + 1}" else d.title
            DayShape(t, legLoad(d))
        }
    }

    private fun liftName(key: String) = when (key) {
        "squat" -> "Squat"; "bench" -> "Bench"; "deadlift" -> "Deadlift"; else -> "Overhead press"
    }

    private fun base(role: String) = when (role) {
        "LIFT", "LIGHT" -> Params(4, 3, 5, 180)
        "MAIN" -> Params(4, 6, 10, 150)
        "SECONDARY" -> Params(3, 8, 12, 120)
        else -> Params(3, 10, 15, 75)
    }

    private fun round25(x: Double): Double = (x / 2.5).roundToInt() * 2.5

    private fun kgText(d: Double): String = Calc.kg(d)

    // ---------- main entry ----------
    fun generate(
        o: GenOptions,
        p: ProfileData,
        all: List<ExerciseEntity>,
        avail: Set<String>,
        today: Long
    ): GenResult {
        val notes = ArrayList<String>()
        val days = p.trainingDays.sorted()
        if (days.isEmpty()) return GenResult(emptyList(), listOf("Pick your training days in Profile first."))

        val range = daysRange(o.goal, o.split)
        var n = o.daysPerWeek.coerceIn(range.first, range.last)
        if (n > days.size) {
            notes.add("You have ${days.size} training days set in Profile, so this uses ${days.size} sessions a week.")
            n = days.size
        }
        // spread the sessions across the days you can train
        val chosen: List<Int> =
            if (o.days != null && o.days.size == n) o.days
            else if (n == days.size) days
            else (0 until n).map { i -> days[if (n == 1) 0 else Math.round(i * (days.size - 1).toFloat() / (n - 1))] }

        val strength = o.goal == "Strength"
        val dayTemplates: List<Day> = templatesFor(strength, o.split, n)

        val hybrid = o.legCut ?: (p.secondaryGoals.any { it == "HYROX" || it.startsWith("Marathon") } &&
            (p.primaryGoal == "Strength" || p.primaryGoal == "Hypertrophy"))
        if (hybrid && o.legCut == true) {
            notes.add("Because you are also running or training for HYROX, gym leg volume is cut by one set per exercise to keep your legs fresh for it.")
        } else if (hybrid) {
            notes.add("Because you also train for ${p.secondaryGoals.filter { it == "HYROX" || it.startsWith("Marathon") }.joinToString(" and ")}, leg volume is cut by one set per exercise to keep your legs fresh for it.")
        }

        val limit = p.sessionMin.toDoubleOrNull()?.toInt() ?: 0
        val usedByTitle = HashMap<String, MutableSet<String>>()
        val resolved = ArrayList<List<Resolved>>()

        dayTemplates.forEachIndexed { idx, day ->
            val key = if (day.title.isEmpty()) "strength$idx" else day.title
            val earlier = usedByTitle.getOrPut(key) { mutableSetOf() }
            val inDay = mutableSetOf<String>()
            val list = ArrayList<Resolved>()
            for (slot in day.slots) {
                val ex = pick(slot, all, avail, o.avoid, inDay, earlier)
                if (ex == null) {
                    notes.add("Nothing in your equipment list fits a ${slot.muscle.lowercase()} exercise on one of the days, so that slot was skipped.")
                    continue
                }
                inDay.add(ex.id)
                list.add(Resolved(slot, ex))
            }
            // keep sessions inside your average session length
            if (limit >= 20) {
                var est = estimate(list)
                var trimmed = false
                while (est > limit) {
                    var i = list.indexOfLast { it.slot.role == "ISO" }
                    if (i < 0) i = list.indexOfLast { it.slot.role == "SECONDARY" }
                    if (i < 0) break
                    list.removeAt(i)
                    trimmed = true
                    est = estimate(list)
                }
                if (trimmed) notes.add("Some accessory work was dropped to keep sessions near your ${limit} minute limit.")
            }
            earlier.addAll(inDay)
            resolved.add(list)
        }

        // warn about back-to-back heavy leg days
        if (o.days == null) for (i in 1 until chosen.size) {
            if (chosen[i] == chosen[i - 1] + 1 && heavyLegs(resolved[i]) && heavyLegs(resolved[i - 1])) {
                notes.add("${Calc.weekdayShort(chosen[i - 1])} and ${Calc.weekdayShort(chosen[i])} both load your legs hard. Fewer sessions or different days would help recovery.")
            }
        }

        val weekStart = Calc.weekStart(today) + if (o.startNextWeek) 7 else 0
        val sessions = ArrayList<GenSession>()
        for (w in 1..o.weeks) {
            for (i in 0 until n) {
                val date = weekStart + 7L * (w - 1) + (chosen[i] - 1)
                if (date < today) continue
                val list = resolved[i]
                if (list.isEmpty()) continue
                val exercises = list.map { r ->
                    if (strength) strengthEx(r, o, p, w, i, hybrid) else hypEx(r, w, o.weeks, hybrid)
                }
                val title = sessionTitle(dayTemplates[i], list, strength)
                val minutes = 8 + exercises.sumOf { ((it.sets * (45 + it.restSec)) / 60.0) }.roundToInt()
                sessions.add(
                    GenSession(
                        date, w, title,
                        reason(o, title, list, w, chosen[i], strength, hybrid, dayTemplates[i].title, i),
                        minutes, exercises
                    )
                )
            }
        }
        return GenResult(sessions, notes.distinct())
    }

    private fun heavyLegs(list: List<Resolved>) =
        list.any { it.ex.primaryMuscle in LOWER && (it.slot.role == "MAIN" || it.slot.role == "LIFT") } ||
            list.any { it.slot.lift == "deadlift" }

    private fun sessionTitle(day: Day, list: List<Resolved>, strength: Boolean): String {
        if (!strength) return day.title
        val lifts = list.mapNotNull { it.slot.lift }.distinct().map { liftName(it) }
        val light = list.any { it.slot.role == "LIGHT" }
        val base = if (lifts.isEmpty()) "Accessories" else lifts.joinToString(" + ")
        return if (light) "$base (light)" else base
    }

    private fun estimate(list: List<Resolved>): Int {
        var sec = 0
        list.forEach { sec += base(it.slot.role).let { b -> b.sets * (45 + b.rest) } }
        return 8 + sec / 60
    }

    private fun pick(
        slot: Slot,
        all: List<ExerciseEntity>,
        avail: Set<String>,
        avoid: Set<String>,
        inDay: Set<String>,
        earlier: Set<String>
    ): ExerciseEntity? {
        fun ok(e: ExerciseEntity) =
            e.id !in avoid && e.id !in inDay && e.category !in BAD_CATS && e.usableWith(avail)
        val byId = all.associateBy { it.id }
        val prefs = slot.prefs.mapNotNull { byId[it] }.filter { ok(it) }.sortedByDescending { it.isFavourite }
        val fixed = slot.role == "MAIN" || slot.role == "LIFT" || slot.role == "LIGHT"
        val fresh = prefs.filter { it.id !in earlier }
        val chosen = if (fixed) prefs else if (fresh.isNotEmpty()) fresh else prefs
        chosen.firstOrNull()?.let { return it }
        return all.filter { ok(it) && it.primaryMuscle == slot.muscle && it.id !in earlier }
            .sortedByDescending { it.isFavourite }
            .firstOrNull()
            ?: all.filter { ok(it) && it.primaryMuscle == slot.muscle }.firstOrNull()
    }

    // ---------- hypertrophy ----------
    private fun hypEx(r: Resolved, w: Int, weeks: Int, hybrid: Boolean): GenExercise {
        val b = base(r.slot.role)
        val phase = ((w - 1) % 4) + 1
        val deload = (w % 4 == 0) || (w == weeks && weeks >= 4)
        val lower = r.ex.primaryMuscle in LOWER
        var sets = b.sets
        if (!deload && phase == 3 && r.slot.role != "ISO") sets += 1
        if (hybrid && lower) sets = max(2, sets - 1)
        var rir = when (phase) { 1 -> 3; 2 -> 2; else -> 1 }
        if (deload) {
            sets = max(2, (b.sets * 0.6).roundToInt())
            rir = 4
        }
        val note = when {
            deload -> "Deload week: lighter and fewer sets so you recover. Stop 4 reps before failure."
            r.slot.role == "MAIN" -> "Heavy main lift. Add weight once you hit ${b.repsMax} reps on every set."
            r.slot.role == "SECONDARY" -> "Add a rep each week, then add weight when you reach ${b.repsMax} reps."
            else -> "Controlled reps and feel the muscle work. Add reps before weight."
        }
        return GenExercise(r.ex, r.slot.role, sets, b.repsMin, b.repsMax, rir, b.rest, 0.0, "", note)
    }

    // ---------- strength ----------
    private fun rmOf(key: String, p: ProfileData): Double? {
        val v = when (key) {
            "squat" -> p.squat1rm
            "bench" -> p.bench1rm
            "deadlift" -> p.deadlift1rm
            else -> p.ohp1rm
        }.toDoubleOrNull()
        return if (v != null && v > 0) v else null
    }

    private fun plan(weights: List<Double>, reps: List<Int>): String =
        weights.indices.joinToString(";") { i -> "${kgText(weights[i])}x${reps[i]}" }

    private fun strengthEx(r: Resolved, o: GenOptions, p: ProfileData, w: Int, dayIdx: Int, hybrid: Boolean): GenExercise {
        val deload = (w % 4 == 0) || (w == o.weeks && o.weeks >= 4)
        val role = r.slot.role
        if (role == "SECONDARY" || role == "ISO") {
            val b = base(role)
            var sets = if (role == "SECONDARY") 3 else 3
            if (hybrid && r.ex.primaryMuscle in LOWER) sets = max(2, sets - 1)
            if (deload) sets = 2
            val rir = if (deload) 4 else 2
            return GenExercise(
                r.ex, role, sets, 8, 12, rir, b.rest, 0.0, "",
                if (deload) "Deload week: fewer sets." else "Accessory work. Add a rep each week, then add weight at 12 reps."
            )
        }

        val key = r.slot.lift ?: "squat"
        val oneRm = if (r.ex.id == CANON[key]) rmOf(key, p) else null
        val lower = key == "squat" || key == "deadlift"
        val inc = if (lower) 5.0 else 2.5
        val phase = ((w - 1) % 4) + 1
        val needRm = if (oneRm == null) " Put your best lifts in Profile to get exact weights." else ""
        val subNote = if (r.ex.id != CANON[key]) " This is a substitute for the barbell lift, so find a working weight." else ""

        fun finish(sets: Int, reps: List<Int>, weights: List<Double>?, rir: Int, note: String): GenExercise {
            val pl = if (weights != null) plan(weights, reps) else ""
            val top = weights?.lastOrNull() ?: 0.0
            val rMin = reps.minOrNull() ?: 5
            val rMax = reps.maxOrNull() ?: 5
            val sp = if (weights != null) pl else ""
            return GenExercise(r.ex, role, sets, rMin, rMax, rir, 180, if (weights != null) top else 0.0, sp, note + subNote)
        }

        if (role == "LIGHT") {
            val wt = oneRm?.let { round25(it * (if (deload) 0.55 else 0.65)) }
            val ws = wt?.let { List(3) { _ -> it } }
            return finish(3, List(3) { 5 }, ws, 3, "Light volume day. Keep it smooth and fast, about 3 reps in reserve.$needRm")
        }

        return when (o.method) {
            "5/3/1" -> {
                val cycle = (w - 1) / 4
                val tm = oneRm?.let { it * 0.9 + inc * cycle }
                val (pcts, reps) = when (phase) {
                    1 -> listOf(0.65, 0.75, 0.85) to listOf(5, 5, 5)
                    2 -> listOf(0.70, 0.80, 0.90) to listOf(3, 3, 3)
                    3 -> listOf(0.75, 0.85, 0.95) to listOf(5, 3, 1)
                    else -> listOf(0.40, 0.50, 0.60) to listOf(5, 5, 5)
                }
                val ws = tm?.let { t -> pcts.map { round25(t * it) } }
                val label = when (phase) { 1 -> "5s week"; 2 -> "3s week"; 3 -> "5/3/1 week"; else -> "deload week" }
                finish(
                    3, reps, ws, if (phase == 4) 4 else 1,
                    "5/3/1 $label. On the last set push for extra reps but keep 1-2 in reserve.$needRm"
                )
            }
            "DUP" -> {
                val kind = (dayIdx + w - 1) % 3
                val (sets, reps, pct) = when (kind) {
                    0 -> Triple(5, 3, 0.85)
                    1 -> Triple(4, 6, 0.75)
                    else -> Triple(3, 10, 0.65)
                }
                val name = when (kind) { 0 -> "Heavy"; 1 -> "Medium"; else -> "Light" }
                val f = if (deload) 0.9 else 1.0
                val ws = oneRm?.let { rm -> List(if (deload) 2 else sets) { _ -> round25(rm * pct * f) } }
                val n = if (deload) 2 else sets
                finish(n, List(n) { reps }, ws, 2, "$name day: $sets x $reps. Leave 1-3 reps in reserve.$needRm")
            }
            else -> { // linear progression
                val sets = if (key == "deadlift") 1 else 3
                val wk = if (deload) w - 1 else w
                val ws = oneRm?.let { rm ->
                    val raw = min(rm * 0.95, rm * 0.70 + inc * (wk - 1))
                    val wt = round25(if (deload) raw * 0.9 else raw)
                    List(sets) { _ -> wt }
                }
                finish(
                    sets, List(sets) { 5 }, ws, 2,
                    if (deload) "Deload week: 90% of last week's weight." else "Add weight each week while every rep stays clean. If you stall, the app suggests a lighter step.$needRm"
                )
            }
        }
    }

    // ---------- explanations ----------
    private fun reason(
        o: GenOptions, title: String, list: List<Resolved>, w: Int, dayNum: Int,
        strength: Boolean, hybrid: Boolean, templateTitle: String, dayIdx: Int
    ): String {
        val muscles = list.map { it.ex.primaryMuscle }.distinct().joinToString(", ").lowercase()
        val deload = (w % 4 == 0) || (w == o.weeks && o.weeks >= 4)
        val phase = ((w - 1) % 4) + 1
        val sb = StringBuilder()
        sb.append("$title trains your $muscles. ")
        if (strength) {
            sb.append(
                when (o.method) {
                    "5/3/1" -> "Using 5/3/1: heavy compound lifts at set percentages of your training max, rising across a 4 week cycle. "
                    "DUP" -> "Using daily undulating periodisation: the same lift is trained heavy, medium and light on different days so it keeps getting practice without wearing you out. "
                    else -> "Using linear progression: a few heavy sets of 5, adding weight week by week. "
                }
            )
        } else {
            sb.append("It sits in a ${o.split} split so each muscle is trained about ${muscleFrequency(o)} a week with recovery time between. ")
        }
        sb.append(
            if (deload) "Week $w is a deload: fewer sets and easier effort so you come back stronger. "
            else if (!strength) "Week $w of ${o.weeks}: work to about ${when (phase) { 1 -> 3; 2 -> 2; else -> 1 }} reps in reserve${if (phase == 3) " with an extra set" else ""}. "
            else "Week $w of ${o.weeks}. "
        )
        sb.append("It is on ${Calc.dayNameFull(weekdayToEpochAnchor(dayNum))} because that is one of the days you said you can train.")
        if (hybrid && list.any { it.ex.primaryMuscle in LOWER }) {
            sb.append(" Leg volume is trimmed because of your secondary goal.")
        }
        return sb.toString()
    }

    private fun muscleFrequency(o: GenOptions): String = when (o.split) {
        "Push/Pull/Legs" -> if (o.daysPerWeek >= 6) "twice" else "once"
        "Upper/Lower" -> if (o.daysPerWeek >= 4) "twice" else "once or twice"
        "Full Body" -> "two to three times"
        else -> "once"
    }

    /** Any date that falls on the given weekday (1 = Monday), used only to print the day name. */
    private fun weekdayToEpochAnchor(dayOfWeek: Int): Long {
        // 1970-01-05 was a Monday (epoch day 4)
        return 4L + (dayOfWeek - 1)
    }
}
