package com.doolan.forge.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doolan.forge.data.Calc
import com.doolan.forge.data.Progression
import com.doolan.forge.data.ExerciseEntity
import com.doolan.forge.data.Repository
import com.doolan.forge.data.SessionEntity
import com.doolan.forge.data.SessionExerciseEntity
import com.doolan.forge.data.SetLogEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SetRow(weight: String, reps: String, rir: String, done: Boolean, var logId: Long = 0) {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
    var rir by mutableStateOf(rir)
    var done by mutableStateOf(done)
}

class ExBlock(val se: SessionExerciseEntity, val exercise: ExerciseEntity, previous: String) {
    val rows = mutableStateListOf<SetRow>()
    var previous by mutableStateOf(previous)
    var suggestion by mutableStateOf("")
}

class WorkoutViewModel(private val repo: Repository, val sessionId: Long) : ViewModel() {

    var session by mutableStateOf<SessionEntity?>(null)
        private set
    val blocks = mutableStateListOf<ExBlock>()
    var loaded by mutableStateOf(false)
        private set
    var restRemaining by mutableIntStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
    var prMessage by mutableStateOf<String?>(null)

    private var restJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = repo.session(sessionId)
        if (s == null) {
            loaded = true
            return
        }
        session = s
        val ses = repo.sessionExercises(sessionId)
        val logs = repo.setsForSession(sessionId)
        for (se in ses) {
            val ex = repo.exercise(se.exerciseId) ?: continue
            blocks.add(buildBlock(se, ex, logs.filter { it.sessionExerciseId == se.id }))
        }
        loaded = true
    }

    private suspend fun buildBlock(
        se: SessionExerciseEntity,
        ex: ExerciseEntity,
        existing: List<SetLogEntity>
    ): ExBlock {
        val history = repo.history(ex.id, sessionId)
        val lastSessionId = history.firstOrNull()?.sessionId
        val lastSets = history.filter { it.sessionId == lastSessionId }.sortedBy { it.setIndex }
        val prevText = lastSets.joinToString(", ") { "${Calc.kg(it.weightKg)}x${it.reps}" }
        val block = ExBlock(se, ex, prevText)

        existing.sortedBy { it.setIndex }.forEach { l ->
            block.rows.add(
                SetRow(
                    Calc.kg(l.weightKg), l.reps.toString(),
                    if (l.rir >= 0) l.rir.toString() else "", true, l.id
                )
            )
        }
        // programmed sets (for example 5/3/1 percentages) fill the rows with weights and reps
        val planned = se.setPlan.split(";").mapNotNull { part ->
            val bits = part.split("x")
            val kg = bits.getOrNull(0)?.toDoubleOrNull()
            val reps = bits.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()
            if (kg != null && reps != null) kg to reps else null
        }
        val sug = Progression.suggest(lastSets, se, ex)
        block.suggestion = when {
            planned.isNotEmpty() && planned.any { it.first > 0 } -> "Programmed weights are filled in. Change them if they feel wrong."
            se.targetWeightKg > 0 -> "Programmed starting weight: ${Calc.kg(se.targetWeightKg)} kg."
            else -> sug.text
        }
        var i = block.rows.size
        while (block.rows.size < se.targetSets) {
            val p = lastSets.getOrNull(i)
            val pl = planned.getOrNull(i)
            val weight = when {
                pl != null && pl.first > 0 -> pl.first
                se.targetWeightKg > 0 -> se.targetWeightKg
                (sug.weight ?: 0.0) > 0 -> sug.weight ?: 0.0
                p != null && p.weightKg > 0 -> p.weightKg
                else -> 0.0
            }
            block.rows.add(
                SetRow(
                    if (weight > 0) Calc.kg(weight) else "",
                    if (pl != null) pl.second.toString() else "",
                    "", false
                )
            )
            i++
        }
        return block
    }

    fun addExercise(ex: ExerciseEntity) {
        viewModelScope.launch {
            val se = SessionExerciseEntity(
                sessionId = sessionId, exerciseId = ex.id, orderIdx = blocks.size,
                targetSets = 3, repsMin = 8, repsMax = 12, targetRir = 2, restSec = 90
            )
            val id = repo.addSessionExercise(se)
            blocks.add(buildBlock(se.copy(id = id), ex, emptyList()))
        }
    }

    fun removeBlock(block: ExBlock) {
        viewModelScope.launch {
            repo.removeSessionExercise(block.se.id)
            blocks.remove(block)
        }
    }

    fun swapExercise(block: ExBlock, newEx: ExerciseEntity) {
        if (block.rows.any { it.done }) {
            message = "Finished sets are already logged for this exercise. Remove it and add the new one instead."
            return
        }
        viewModelScope.launch {
            val updated = block.se.copy(exerciseId = newEx.id, setPlan = "", targetWeightKg = 0.0)
            repo.updateSessionExercise(updated)
            val idx = blocks.indexOf(block)
            if (idx >= 0) blocks[idx] = buildBlock(updated, newEx, emptyList())
        }
    }

    fun addSet(block: ExBlock) {
        val last = block.rows.lastOrNull()
        block.rows.add(SetRow(last?.weight ?: "", "", "", false))
    }

    fun removeSet(block: ExBlock, row: SetRow) {
        if (block.rows.size <= 1) return
        if (row.done) {
            viewModelScope.launch {
                repo.deleteSet(row.logId)
                block.rows.remove(row)
            }
        } else {
            block.rows.remove(row)
        }
    }

    fun toggleSet(block: ExBlock, row: SetRow) {
        if (row.done) {
            viewModelScope.launch {
                repo.deleteSet(row.logId)
                row.done = false
                row.logId = 0
            }
            return
        }
        val w = row.weight.toDoubleOrNull() ?: 0.0
        val r = row.reps.toIntOrNull() ?: 0
        if (r <= 0) {
            message = "Enter the reps for this set first."
            return
        }
        val rir = row.rir.toIntOrNull() ?: -1
        val idx = block.rows.indexOf(row)
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val history = repo.history(block.exercise.id, sessionId)
            val prevBest = history.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: 0.0
            val id = repo.logSet(
                SetLogEntity(
                    sessionId = sessionId, sessionExerciseId = block.se.id, exerciseId = block.exercise.id,
                    setIndex = idx, weightKg = w, reps = r, rir = rir,
                    completedAt = now, dateEpochDay = Calc.today()
                )
            )
            row.logId = id
            row.done = true
            val e = Calc.e1rm(w, r)
            if (w > 0 && prevBest > 0 && e > prevBest + 0.01) {
                prMessage = "New PR on ${block.exercise.name}: est. 1RM ${Calc.kg(Math.round(e * 10) / 10.0)} kg"
            }
            val s = session
            if (s != null && s.startedAt == 0L) {
                val started = s.copy(startedAt = now)
                session = started
                repo.updateSession(started)
            }
            startRest(block.se.restSec)
        }
    }

    fun startRest(sec: Int) {
        restJob?.cancel()
        restRemaining = sec
        restJob = viewModelScope.launch {
            while (restRemaining > 0) {
                delay(1000)
                restRemaining -= 1
            }
        }
    }

    fun addRest(sec: Int) = startRest(restRemaining + sec)

    fun skipRest() {
        restJob?.cancel()
        restRemaining = 0
    }

    fun finish(difficulty: Int, notes: String, distanceKm: Double, durationSec: Int, onDone: () -> Unit) {
        val s = session ?: return
        viewModelScope.launch {
            val done = s.copy(
                status = "DONE", difficulty = difficulty, notes = notes,
                distanceKm = distanceKm, durationSec = durationSec,
                finishedAt = System.currentTimeMillis()
            )
            repo.updateSession(done)
            session = done
            onDone()
        }
    }

    fun markPlanned(onDone: () -> Unit) {
        val s = session ?: return
        viewModelScope.launch {
            val p = s.copy(status = "PLANNED")
            repo.updateSession(p)
            session = p
            onDone()
        }
    }
}
