package com.doolan.forge.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val context: Context, val db: ForgeDatabase, val store: ProfileStore) {

    val profile = store.profile
    val exercises: Flow<List<ExerciseEntity>> = db.exercises().all()
    val equipment: Flow<List<EquipmentEntity>> = db.equipment().all()
    val bodyweight: Flow<List<BodyweightEntity>> = db.bodyweight().all()
    val programmes: Flow<List<ProgrammeEntity>> = db.programmes().all()

    private val extraEquipment = listOf("Treadmill", "Resistance band", "Plyo box", "Barbell plates", "Dumbbell rack")

    // ---------- seeding ----------
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (db.exercises().count() == 0) {
            val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = ArrayList<ExerciseEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ExerciseEntity(
                        id = o.getString("id"), name = o.getString("name"),
                        category = o.getString("category"), primaryMuscle = o.getString("primaryMuscle"),
                        secondaryMuscles = o.optString("secondaryMuscles", ""),
                        equipment = o.getString("equipment"), difficulty = o.getString("difficulty"),
                        instructions = o.getString("instructions"), mistakes = o.optString("mistakes", ""),
                        tips = o.optString("tips", "")
                    )
                )
            }
            db.exercises().insertAll(list)
        }
        val names = (db.exercises().allNow().flatMap { it.equipmentList } + extraEquipment)
            .filter { it != "Bodyweight" }.toSortedSet()
        db.equipment().insertAllIgnore(names.map { EquipmentEntity(it, false, false) })
    }

    // ---------- profile ----------
    suspend fun saveProfile(p: ProfileData) = store.save(p)
    suspend fun currentProfile() = store.current()

    // ---------- exercises ----------
    suspend fun setFavourite(id: String, fav: Boolean) = db.exercises().setFavourite(id, fav)
    suspend fun addCustomExercise(e: ExerciseEntity) = db.exercises().insert(e)
    suspend fun deleteCustomExercise(id: String) = db.exercises().deleteCustom(id)
    suspend fun exercise(id: String) = db.exercises().byId(id)

    // ---------- equipment ----------
    suspend fun setEquipment(name: String, available: Boolean) = db.equipment().setAvailable(name, available)
    suspend fun addCustomEquipment(name: String) =
        db.equipment().insertAllIgnore(listOf(EquipmentEntity(name.trim(), true, true)))
    suspend fun deleteCustomEquipment(name: String) = db.equipment().deleteCustom(name)

    // ---------- sessions ----------
    fun sessionsBetween(from: Long, to: Long) = db.sessions().between(from, to)
    fun setsBetween(from: Long, to: Long) = db.setLogs().between(from, to)
    suspend fun session(id: Long) = db.sessions().byId(id)
    suspend fun addSession(s: SessionEntity): Long = db.sessions().insert(s)
    suspend fun updateSession(s: SessionEntity) = db.sessions().update(s)

    suspend fun deleteSession(id: Long) {
        db.setLogs().deleteForSession(id)
        db.sessionExercises().deleteForSession(id)
        db.sessions().delete(id)
    }

    /** Removes the not-yet-done sessions of one programme (done sessions and their logs are kept). */
    suspend fun deleteProgramme(id: Long) {
        db.sessions().futurePlannedIds(id, Calc.today()).forEach { deleteSession(it) }
        db.programmes().delete(id)
    }

    /** Saves a generated programme. Optionally clears the future sessions of any earlier programmes first. */
    suspend fun savePlan(
        programme: ProgrammeEntity,
        plan: List<Pair<SessionEntity, List<SessionExerciseEntity>>>,
        replaceOld: Boolean
    ) = db.withTransaction {
        if (replaceOld) {
            db.programmes().allNow().forEach { old ->
                db.sessions().futurePlannedIds(old.id, Calc.today()).forEach { deleteSession(it) }
                db.programmes().delete(old.id)
            }
        }
        val pid = db.programmes().insert(programme)
        plan.forEach { (s, exs) ->
            val sid = db.sessions().insert(s.copy(programmeId = pid))
            exs.forEachIndexed { i, e ->
                db.sessionExercises().insert(e.copy(sessionId = sid, orderIdx = i))
            }
        }
    }

    suspend fun moveSession(id: Long, newDay: Long) {
        val s = db.sessions().byId(id) ?: return
        db.sessions().update(s.copy(dateEpochDay = newDay))
    }

    suspend fun sessionExercises(sessionId: Long) = db.sessionExercises().forSession(sessionId)
    suspend fun addSessionExercise(e: SessionExerciseEntity): Long = db.sessionExercises().insert(e)
    suspend fun updateSessionExercise(e: SessionExerciseEntity) = db.sessionExercises().update(e)

    suspend fun removeSessionExercise(id: Long) {
        db.setLogs().deleteForSessionExercise(id)
        db.sessionExercises().delete(id)
    }

    suspend fun clearLogsFor(seId: Long) = db.setLogs().deleteForSessionExercise(seId)

    // ---------- set logs ----------
    suspend fun logSet(l: SetLogEntity): Long = db.setLogs().insert(l)
    suspend fun deleteSet(id: Long) = db.setLogs().delete(id)
    suspend fun setsForSession(sessionId: Long) = db.setLogs().forSession(sessionId)
    suspend fun history(exerciseId: String, excludeSession: Long) = db.setLogs().historyFor(exerciseId, excludeSession)

    // ---------- bodyweight ----------
    suspend fun addBodyweight(kg: Double) =
        db.bodyweight().insert(BodyweightEntity(dateEpochDay = Calc.today(), kg = kg))
    suspend fun deleteBodyweight(b: BodyweightEntity) = db.bodyweight().delete(b)

    // ---------- backup ----------
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Forge")
        root.put("version", 1)

        val p = store.current()
        root.put("profile", JSONObject().apply {
            put("onboarded", p.onboarded); put("name", p.name); put("age", p.age); put("sex", p.sex)
            put("heightCm", p.heightCm); put("weightKg", p.weightKg); put("bodyFat", p.bodyFat)
            put("experience", p.experience); put("fitnessLevel", p.fitnessLevel); put("runningExp", p.runningExp)
            put("pb5k", p.pb5k); put("pb10k", p.pb10k); put("pbHalf", p.pbHalf); put("pbMarathon", p.pbMarathon)
            put("squat1rm", p.squat1rm); put("bench1rm", p.bench1rm)
            put("deadlift1rm", p.deadlift1rm); put("ohp1rm", p.ohp1rm)
            put("trainingDays", p.trainingDays.sorted().joinToString(","))
            put("preferredTime", p.preferredTime); put("sessionMin", p.sessionMin); put("sleepHours", p.sleepHours)
            put("primaryGoal", p.primaryGoal); put("secondaryGoals", p.secondaryGoals.joinToString(","))
            put("injuries", p.injuries)
        })

        root.put("exercises", JSONArray().also { a ->
            db.exercises().allNow().forEach { e ->
                a.put(JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("category", e.category)
                    put("primaryMuscle", e.primaryMuscle); put("secondaryMuscles", e.secondaryMuscles)
                    put("equipment", e.equipment); put("difficulty", e.difficulty)
                    put("instructions", e.instructions); put("mistakes", e.mistakes); put("tips", e.tips)
                    put("isCustom", e.isCustom); put("isFavourite", e.isFavourite)
                })
            }
        })
        root.put("equipment", JSONArray().also { a ->
            db.equipment().allNow().forEach { e ->
                a.put(JSONObject().apply {
                    put("name", e.name); put("available", e.available); put("isCustom", e.isCustom)
                })
            }
        })
        root.put("sessions", JSONArray().also { a ->
            db.sessions().allNow().forEach { s ->
                a.put(JSONObject().apply {
                    put("id", s.id); put("dateEpochDay", s.dateEpochDay); put("title", s.title)
                    put("discipline", s.discipline); put("durationMin", s.durationMin); put("status", s.status)
                    put("notes", s.notes); put("difficulty", s.difficulty); put("startedAt", s.startedAt)
                    put("finishedAt", s.finishedAt); put("distanceKm", s.distanceKm); put("durationSec", s.durationSec)
                    put("reason", s.reason); put("programmeId", s.programmeId)
                })
            }
        })
        root.put("sessionExercises", JSONArray().also { a ->
            db.sessionExercises().allNow().forEach { e ->
                a.put(JSONObject().apply {
                    put("id", e.id); put("sessionId", e.sessionId); put("exerciseId", e.exerciseId)
                    put("orderIdx", e.orderIdx); put("targetSets", e.targetSets); put("repsMin", e.repsMin)
                    put("repsMax", e.repsMax); put("targetRir", e.targetRir); put("restSec", e.restSec)
                    put("targetWeightKg", e.targetWeightKg); put("setPlan", e.setPlan); put("note", e.note)
                })
            }
        })
        root.put("setLogs", JSONArray().also { a ->
            db.setLogs().allNow().forEach { l ->
                a.put(JSONObject().apply {
                    put("id", l.id); put("sessionId", l.sessionId); put("sessionExerciseId", l.sessionExerciseId)
                    put("exerciseId", l.exerciseId); put("setIndex", l.setIndex); put("weightKg", l.weightKg)
                    put("reps", l.reps); put("rir", l.rir); put("completedAt", l.completedAt)
                    put("dateEpochDay", l.dateEpochDay)
                })
            }
        })
        root.put("bodyweight", JSONArray().also { a ->
            db.bodyweight().allNow().forEach { b ->
                a.put(JSONObject().apply {
                    put("id", b.id); put("dateEpochDay", b.dateEpochDay); put("kg", b.kg)
                })
            }
        })
        root.put("programmes", JSONArray().also { a ->
            db.programmes().allNow().forEach { g ->
                a.put(JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("goal", g.goal); put("split", g.split)
                    put("method", g.method); put("startDay", g.startDay); put("weeks", g.weeks)
                    put("createdAt", g.createdAt)
                })
            }
        })
        root.toString(1)
    }

    /** Replaces everything in the app with the contents of a Forge backup. Throws if the file is not a backup. */
    suspend fun importJson(text: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        require(root.optString("app") == "Forge") { "This is not a Forge backup file." }

        val exercises = ArrayList<ExerciseEntity>()
        root.getJSONArray("exercises").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                exercises.add(
                    ExerciseEntity(
                        o.getString("id"), o.getString("name"), o.getString("category"),
                        o.getString("primaryMuscle"), o.optString("secondaryMuscles", ""),
                        o.getString("equipment"), o.getString("difficulty"), o.getString("instructions"),
                        o.optString("mistakes", ""), o.optString("tips", ""),
                        o.optBoolean("isCustom", false), o.optBoolean("isFavourite", false)
                    )
                )
            }
        }
        val equipment = ArrayList<EquipmentEntity>()
        root.getJSONArray("equipment").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                equipment.add(EquipmentEntity(o.getString("name"), o.getBoolean("available"), o.optBoolean("isCustom", false)))
            }
        }
        val sessions = ArrayList<SessionEntity>()
        root.getJSONArray("sessions").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                sessions.add(
                    SessionEntity(
                        o.getLong("id"), o.getLong("dateEpochDay"), o.getString("title"), o.getString("discipline"),
                        o.getInt("durationMin"), o.getString("status"), o.optString("notes", ""),
                        o.optInt("difficulty", 0), o.optLong("startedAt", 0), o.optLong("finishedAt", 0),
                        o.optDouble("distanceKm", 0.0), o.optInt("durationSec", 0),
                        o.optString("reason", ""), o.optLong("programmeId", 0)
                    )
                )
            }
        }
        val sessionExercises = ArrayList<SessionExerciseEntity>()
        root.getJSONArray("sessionExercises").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                sessionExercises.add(
                    SessionExerciseEntity(
                        o.getLong("id"), o.getLong("sessionId"), o.getString("exerciseId"), o.getInt("orderIdx"),
                        o.getInt("targetSets"), o.getInt("repsMin"), o.getInt("repsMax"),
                        o.getInt("targetRir"), o.getInt("restSec"),
                        o.optDouble("targetWeightKg", 0.0), o.optString("setPlan", ""), o.optString("note", "")
                    )
                )
            }
        }
        val logs = ArrayList<SetLogEntity>()
        root.getJSONArray("setLogs").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                logs.add(
                    SetLogEntity(
                        o.getLong("id"), o.getLong("sessionId"), o.getLong("sessionExerciseId"),
                        o.getString("exerciseId"), o.getInt("setIndex"), o.getDouble("weightKg"),
                        o.getInt("reps"), o.getInt("rir"), o.getLong("completedAt"), o.getLong("dateEpochDay")
                    )
                )
            }
        }
        val weights = ArrayList<BodyweightEntity>()
        root.getJSONArray("bodyweight").let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                weights.add(BodyweightEntity(o.getLong("id"), o.getLong("dateEpochDay"), o.getDouble("kg")))
            }
        }

        val programmes = ArrayList<ProgrammeEntity>()
        root.optJSONArray("programmes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                programmes.add(
                    ProgrammeEntity(
                        o.getLong("id"), o.getString("name"), o.getString("goal"), o.getString("split"),
                        o.getString("method"), o.getLong("startDay"), o.getInt("weeks"), o.getLong("createdAt")
                    )
                )
            }
        }

        // Everything parsed fine, so it is now safe to wipe and restore.
        db.clearAllTables()
        db.exercises().insertAll(exercises)
        db.equipment().insertAllReplace(equipment)
        db.sessions().insertAll(sessions)
        db.sessionExercises().insertAll(sessionExercises)
        db.setLogs().insertAll(logs)
        db.bodyweight().insertAll(weights)
        db.programmes().insertAll(programmes)

        val po = root.getJSONObject("profile")
        fun s(k: String) = po.optString(k, "")
        store.save(
            ProfileData(
                onboarded = po.optBoolean("onboarded", true), name = s("name"), age = s("age"), sex = s("sex"),
                heightCm = s("heightCm"), weightKg = s("weightKg"), bodyFat = s("bodyFat"),
                experience = s("experience").ifEmpty { "Intermediate" },
                fitnessLevel = s("fitnessLevel").ifEmpty { "Average" },
                runningExp = s("runningExp").ifEmpty { "None" },
                pb5k = s("pb5k"), pb10k = s("pb10k"), pbHalf = s("pbHalf"), pbMarathon = s("pbMarathon"),
                squat1rm = s("squat1rm"), bench1rm = s("bench1rm"), deadlift1rm = s("deadlift1rm"), ohp1rm = s("ohp1rm"),
                trainingDays = s("trainingDays").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
                preferredTime = s("preferredTime").ifEmpty { "Morning" },
                sessionMin = s("sessionMin").ifEmpty { "60" }, sleepHours = s("sleepHours"),
                primaryGoal = s("primaryGoal").ifEmpty { "Hypertrophy" },
                secondaryGoals = s("secondaryGoals").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                injuries = s("injuries")
            )
        )
    }
}
