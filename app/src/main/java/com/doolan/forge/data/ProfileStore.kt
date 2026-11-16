package com.doolan.forge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "forge_profile")

/** Everything the user tells us about themselves. Stored as text so it is easy to back up. */
data class ProfileData(
    val onboarded: Boolean = false,
    val name: String = "",
    val age: String = "",
    val sex: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val bodyFat: String = "",
    val experience: String = "Intermediate",
    val fitnessLevel: String = "Average",
    val runningExp: String = "None",
    val pb5k: String = "",
    val pb10k: String = "",
    val pbHalf: String = "",
    val pbMarathon: String = "",
    val squat1rm: String = "",
    val bench1rm: String = "",
    val deadlift1rm: String = "",
    val ohp1rm: String = "",
    val trainingDays: Set<Int> = emptySet(),   // 1 = Monday ... 7 = Sunday
    val preferredTime: String = "Morning",
    val sessionMin: String = "60",
    val sleepHours: String = "",
    val primaryGoal: String = "Hypertrophy",
    val secondaryGoals: Set<String> = emptySet(),
    val injuries: String = "",
    val nutritionGoal: String = "Maintain"
)

const val DEFAULT_MODEL = "claude-sonnet-5-5"

class ProfileStore(private val context: Context) {

    private object K {
        fun s(n: String) = stringPreferencesKey(n)
        val onboarded = s("onboarded"); val name = s("name"); val age = s("age"); val sex = s("sex")
        val height = s("height"); val weight = s("weight"); val bodyFat = s("bodyFat")
        val experience = s("experience"); val fitness = s("fitness"); val runningExp = s("runningExp")
        val pb5k = s("pb5k"); val pb10k = s("pb10k"); val pbHalf = s("pbHalf"); val pbMarathon = s("pbMarathon")
        val squat = s("squat"); val bench = s("bench"); val dead = s("dead"); val ohp = s("ohp")
        val days = s("days"); val time = s("time"); val sessionMin = s("sessionMin"); val sleep = s("sleep")
        val primary = s("primary"); val secondary = s("secondary"); val injuries = s("injuries")
        val nutrition = s("nutritionGoal")
    }

    private fun Preferences.toProfile(): ProfileData {
        fun g(k: Preferences.Key<String>, d: String = "") = this[k] ?: d
        return ProfileData(
            onboarded = g(K.onboarded) == "1",
            name = g(K.name), age = g(K.age), sex = g(K.sex),
            heightCm = g(K.height), weightKg = g(K.weight), bodyFat = g(K.bodyFat),
            experience = g(K.experience, "Intermediate"), fitnessLevel = g(K.fitness, "Average"),
            runningExp = g(K.runningExp, "None"),
            pb5k = g(K.pb5k), pb10k = g(K.pb10k), pbHalf = g(K.pbHalf), pbMarathon = g(K.pbMarathon),
            squat1rm = g(K.squat), bench1rm = g(K.bench), deadlift1rm = g(K.dead), ohp1rm = g(K.ohp),
            trainingDays = g(K.days).split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
            preferredTime = g(K.time, "Morning"), sessionMin = g(K.sessionMin, "60"), sleepHours = g(K.sleep),
            primaryGoal = g(K.primary, "Hypertrophy"),
            secondaryGoals = g(K.secondary).split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            injuries = g(K.injuries),
            nutritionGoal = g(K.nutrition, "Maintain")
        )
    }

    val profile: Flow<ProfileData> = context.dataStore.data.map { it.toProfile() }

    private val coachKey = stringPreferencesKey("coach_api_key")
    private val coachModel = stringPreferencesKey("coach_model")

    /** The user's own AI key. Kept only on this phone and never included in backups. */
    val apiKey: Flow<String> = context.dataStore.data.map { it[coachKey] ?: "" }
    val modelName: Flow<String> = context.dataStore.data.map { it[coachModel] ?: DEFAULT_MODEL }

    suspend fun saveCoach(key: String, model: String) {
        context.dataStore.edit { e ->
            e[coachKey] = key.trim()
            e[coachModel] = model.trim().ifEmpty { DEFAULT_MODEL }
        }
    }

    suspend fun current(): ProfileData = profile.first()

    suspend fun save(p: ProfileData) {
        context.dataStore.edit { e ->
            e[K.onboarded] = if (p.onboarded) "1" else "0"
            e[K.name] = p.name; e[K.age] = p.age; e[K.sex] = p.sex
            e[K.height] = p.heightCm; e[K.weight] = p.weightKg; e[K.bodyFat] = p.bodyFat
            e[K.experience] = p.experience; e[K.fitness] = p.fitnessLevel; e[K.runningExp] = p.runningExp
            e[K.pb5k] = p.pb5k; e[K.pb10k] = p.pb10k; e[K.pbHalf] = p.pbHalf; e[K.pbMarathon] = p.pbMarathon
            e[K.squat] = p.squat1rm; e[K.bench] = p.bench1rm; e[K.dead] = p.deadlift1rm; e[K.ohp] = p.ohp1rm
            e[K.days] = p.trainingDays.sorted().joinToString(",")
            e[K.time] = p.preferredTime; e[K.sessionMin] = p.sessionMin; e[K.sleep] = p.sleepHours
            e[K.primary] = p.primaryGoal; e[K.secondary] = p.secondaryGoals.joinToString(",")
            e[K.injuries] = p.injuries
            e[K.nutrition] = p.nutritionGoal
        }
    }
}
