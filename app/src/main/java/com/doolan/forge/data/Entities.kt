package com.doolan.forge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val equipment: String,          // "A|B" - all are required
    val difficulty: String,
    val instructions: String,       // "step|step|step"
    val mistakes: String,
    val tips: String,
    val isCustom: Boolean = false,
    val isFavourite: Boolean = false
) {
    val equipmentList: List<String> get() = equipment.split("|").filter { it.isNotBlank() }
    val stepList: List<String> get() = instructions.split("|").filter { it.isNotBlank() }
    val mistakeList: List<String> get() = mistakes.split("|").filter { it.isNotBlank() }
    val tipList: List<String> get() = tips.split("|").filter { it.isNotBlank() }
    val secondaryList: List<String> get() = secondaryMuscles.split("|").filter { it.isNotBlank() }

    fun usableWith(available: Set<String>): Boolean =
        equipmentList.all { it == "Bodyweight" || it in available }
}

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val name: String,
    val available: Boolean = false,
    val isCustom: Boolean = false
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val title: String,
    val discipline: String,         // GYM, RUN, HYROX, REST, OTHER
    val durationMin: Int = 60,
    val status: String = "PLANNED", // PLANNED, DONE, SKIPPED
    val notes: String = "",
    val difficulty: Int = 0,        // perceived difficulty 1-10 after the session
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val distanceKm: Double = 0.0,   // runs
    val durationSec: Int = 0,       // runs: actual time
    val reason: String = "",        // why the generator scheduled this session
    val programmeId: Long = 0,      // 0 = added by hand
    val workout: String = ""        // written session plan (runs, HYROX), blank for gym sessions
)

@Entity(tableName = "session_exercises")
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val orderIdx: Int,
    val targetSets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val targetRir: Int = 2,
    val restSec: Int = 90,
    val targetWeightKg: Double = 0.0,
    val setPlan: String = "",       // "62.5x5;72.5x5;82.5x5" (kg x reps per set), blank = use the target fields
    val note: String = ""
)

@Entity(tableName = "set_logs")
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sessionExerciseId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rir: Int,
    val completedAt: Long,
    val dateEpochDay: Long
)

@Entity(tableName = "bodyweight")
data class BodyweightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val kg: Double
)

@Entity(tableName = "programmes")
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: String,
    val split: String,
    val method: String,
    val startDay: Long,
    val weeks: Int,
    val createdAt: Long
)

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val sleepHours: Double,
    val sleepQuality: Int,      // 1 poor - 5 great
    val soreness: Int,          // 1 none - 5 very sore
    val stress: Int,            // 1 low - 5 high
    val energy: Int,            // 1 flat - 5 great
    val restingHr: Int = 0,     // 0 = not logged
    val note: String = ""
)

@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val type: String,           // Body fat %, Waist, Chest, Arm, Thigh, Hip, Neck
    val value: Double
)
