package com.doolan.forge.data

data class Suggestion(val weight: Double?, val text: String)

/** Suggests what to do next time, using only what the user actually logged. */
object Progression {
    private val LOWER = setOf("Quads", "Hamstrings", "Glutes", "Calves")

    fun suggest(lastSets: List<SetLogEntity>, se: SessionExerciseEntity, exercise: ExerciseEntity): Suggestion {
        if (lastSets.isEmpty()) return Suggestion(null, "")
        val top = lastSets.maxOf { it.weightKg }
        if (top <= 0.0) return Suggestion(null, "Last time was bodyweight. Add a rep or two per set.")
        val atTop = lastSets.filter { it.weightKg == top }
        val inc = if (exercise.primaryMuscle in LOWER || exercise.id == "conventional_dl") 5.0 else 2.5
        val hitAll = atTop.size >= minOf(se.targetSets, 2) && atTop.all { it.reps >= se.repsMax }
        val missed = atTop.count { it.reps < se.repsMin }
        return when {
            hitAll -> Suggestion(
                top + inc,
                "You hit ${se.repsMax} reps on every set at ${Calc.kg(top)} kg. Try ${Calc.kg(top + inc)} kg."
            )
            missed >= 2 -> Suggestion(
                top,
                "You fell short of ${se.repsMin} reps on $missed sets at ${Calc.kg(top)} kg. Stay there and beat it."
            )
            else -> Suggestion(
                top,
                "Stay at ${Calc.kg(top)} kg and add a rep or two per set before going heavier."
            )
        }
    }
}
