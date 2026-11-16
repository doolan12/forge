package com.doolan.forge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doolan.forge.data.BodyweightEntity
import com.doolan.forge.data.CheckInEntity
import com.doolan.forge.data.FoodEntryEntity
import com.doolan.forge.data.MeasurementEntity
import com.doolan.forge.data.EquipmentEntity
import com.doolan.forge.data.ExerciseEntity
import com.doolan.forge.data.GenResult
import com.doolan.forge.data.ProfileData
import com.doolan.forge.data.ProgrammeEntity
import com.doolan.forge.data.SessionExerciseEntity
import com.doolan.forge.data.Repository
import com.doolan.forge.data.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(val repo: Repository) : ViewModel() {

    /** null while the profile is still loading from disk. */
    val profile: StateFlow<ProfileData?> =
        repo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val exercises: StateFlow<List<ExerciseEntity>> =
        repo.exercises.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val equipment: StateFlow<List<EquipmentEntity>> =
        repo.equipment.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableEquipment: StateFlow<Set<String>> =
        repo.equipment
            .map { list -> list.filter { it.available }.map { it.name }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val bodyweight: StateFlow<List<BodyweightEntity>> =
        repo.bodyweight.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val programmes: StateFlow<List<ProgrammeEntity>> =
        repo.programmes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val checkIns: StateFlow<List<CheckInEntity>> =
        repo.checkIns.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val foods: StateFlow<List<FoodEntryEntity>> =
        repo.foods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val measurements: StateFlow<List<MeasurementEntity>> =
        repo.measurements.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addFood(e: FoodEntryEntity) { viewModelScope.launch { repo.addFood(e) } }
    fun deleteFood(id: Long) { viewModelScope.launch { repo.deleteFood(id) } }
    fun addMeasurement(m: MeasurementEntity) { viewModelScope.launch { repo.addMeasurement(m) } }
    fun deleteMeasurement(id: Long) { viewModelScope.launch { repo.deleteMeasurement(id) } }

    fun saveCheckIn(c: CheckInEntity) {
        viewModelScope.launch { repo.saveCheckIn(c) }
    }

    fun easeSession(id: Long) {
        viewModelScope.launch { repo.easeSession(id) }
    }

    fun savePlan(
        name: String, goal: String, detail: String, weeks: Int,
        result: GenResult, replaceOld: Boolean, onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val first = result.sessions.minOfOrNull { it.dayEpoch } ?: 0L
            val programme = ProgrammeEntity(
                name = name, goal = goal, split = detail, method = "",
                startDay = first, weeks = weeks, createdAt = System.currentTimeMillis()
            )
            val plan = result.sessions.map { g ->
                val session = SessionEntity(
                    dateEpochDay = g.dayEpoch, title = g.title, discipline = g.discipline,
                    durationMin = g.minutes, reason = g.reason, workout = g.workout
                )
                val exs = g.exercises.map { e ->
                    SessionExerciseEntity(
                        sessionId = 0, exerciseId = e.exercise.id, orderIdx = 0, targetSets = e.sets,
                        repsMin = e.repsMin, repsMax = e.repsMax, targetRir = e.rir, restSec = e.restSec,
                        targetWeightKg = e.weightKg, setPlan = e.setPlan, note = e.note
                    )
                }
                session to exs
            }
            repo.savePlan(programme, plan, replaceOld)
            onDone()
        }
    }

    fun deleteProgramme(id: Long) {
        viewModelScope.launch { repo.deleteProgramme(id) }
    }

    fun saveProfile(p: ProfileData) {
        viewModelScope.launch { repo.saveProfile(p) }
    }

    fun sessionsBetween(from: Long, to: Long): Flow<List<SessionEntity>> = repo.sessionsBetween(from, to)
    fun setsBetween(from: Long, to: Long) = repo.setsBetween(from, to)

    fun addSession(s: SessionEntity, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addSession(s)
            onDone(id)
        }
    }

    fun moveSession(id: Long, newDay: Long) {
        viewModelScope.launch { repo.moveSession(id, newDay) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repo.deleteSession(id) }
    }

    fun skipSession(s: SessionEntity) {
        viewModelScope.launch { repo.updateSession(s.copy(status = "SKIPPED")) }
    }

    fun setFavourite(id: String, fav: Boolean) {
        viewModelScope.launch { repo.setFavourite(id, fav) }
    }

    fun addCustomExercise(e: ExerciseEntity) {
        viewModelScope.launch { repo.addCustomExercise(e) }
    }

    fun deleteCustomExercise(id: String) {
        viewModelScope.launch { repo.deleteCustomExercise(id) }
    }

    fun setEquipment(name: String, available: Boolean) {
        viewModelScope.launch { repo.setEquipment(name, available) }
    }

    fun addCustomEquipment(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addCustomEquipment(name) }
    }

    fun deleteCustomEquipment(name: String) {
        viewModelScope.launch { repo.deleteCustomEquipment(name) }
    }

    fun addBodyweight(kg: Double) {
        viewModelScope.launch { repo.addBodyweight(kg) }
    }

    fun deleteBodyweight(b: BodyweightEntity) {
        viewModelScope.launch { repo.deleteBodyweight(b) }
    }

    suspend fun exportJson(): String = repo.exportJson()
    suspend fun importJson(text: String) = repo.importJson(text)
}
