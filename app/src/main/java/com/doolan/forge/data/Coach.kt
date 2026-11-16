package com.doolan.forge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

data class ChatMsg(val role: String, val text: String)

/** A change the coach suggests. Nothing happens until the user taps Apply. */
data class CoachAction(val kind: String, val sessionId: Long, val date: Long?, val label: String, val why: String)

data class CoachReply(val text: String, val actions: List<CoachAction>)

object Coach {
    private const val SYSTEM = """You are Forge Coach, the training coach inside the Forge fitness app. The person is a trainer who likes plain English, practical steps and real-world examples.

RULES
- Use ONLY the information in the DATA block. If something is not in the data, say it has not been logged and ask. Never invent numbers, sessions or history.
- Explain WHY, briefly. Keep answers short and easy to scan on a phone.
- Never add extra work to other days to make up for a missed or eased session. Missed sessions are simply missed.
- You are not a doctor. For sharp pain, chest pain, dizziness or anything worrying, tell them to stop and see a health professional.
- You cannot change the app yourself. Never say you have changed something. If you recommend a change to an upcoming PLANNED session, you may propose it so the user can confirm it.
- To propose changes, end your reply with one fenced block exactly like this (only when you really recommend it, at most 3 items, only sessions listed as PLANNED with an id in the DATA):
```forge-actions
[{"action":"ease_session","sessionId":12,"why":"short reason"},{"action":"skip_session","sessionId":13,"why":"short reason"},{"action":"move_session","sessionId":14,"date":"2026-11-20","why":"short reason"}]
```
- ease_session = one fewer set per exercise and one more rep in reserve. skip_session = mark it skipped. move_session = move it to a date within the next 14 days.
- Do not diagnose or give medical advice. Do not recommend extreme diets."""

    suspend fun send(apiKey: String, model: String, context: String, history: List<ChatMsg>): CoachReply =
        withContext(Dispatchers.IO) {
            val msgs = JSONArray()
            history.forEachIndexed { i, m ->
                val text = if (i == history.lastIndex && m.role == "user") "DATA:\n$context\n\nQUESTION:\n${m.text}" else m.text
                msgs.put(JSONObject().put("role", m.role).put("content", text))
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1200)
                .put("system", SYSTEM)
                .put("messages", msgs)
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 90000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val msg = try { JSONObject(raw).getJSONObject("error").getString("message") } catch (e: Exception) { raw.take(200) }
                    throw IllegalStateException(
                        when (code) {
                            401 -> "The API key was rejected. Check it in the coach settings."
                            429 -> "Too many requests or no credit left on that key. Try again later."
                            else -> "The coach could not answer ($code): $msg"
                        }
                    )
                }
                val content = JSONObject(raw).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.getString("text"))
                }
                parse(sb.toString())
            } finally {
                conn.disconnect()
            }
        }

    /** Splits the reply into text and proposed actions. */
    fun parse(full: String): CoachReply {
        val start = full.indexOf("```forge-actions")
        if (start < 0) return CoachReply(full.trim(), emptyList())
        val after = full.indexOf('\n', start)
        val end = full.indexOf("```", if (after >= 0) after else start + 16)
        val text = full.substring(0, start).trim()
        if (after < 0 || end < 0) return CoachReply(text, emptyList())
        val json = full.substring(after, end).trim()
        val actions = ArrayList<CoachAction>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until minOf(arr.length(), 3)) {
                val o = arr.getJSONObject(i)
                val kind = o.optString("action")
                if (kind !in listOf("ease_session", "skip_session", "move_session")) continue
                val date = if (kind == "move_session") try { LocalDate.parse(o.getString("date")).toEpochDay() } catch (e: Exception) { null } else null
                if (kind == "move_session" && date == null) continue
                actions.add(CoachAction(kind, o.getLong("sessionId"), date, "", o.optString("why")))
            }
        } catch (e: Exception) {
            // an unreadable action block is ignored; the text answer still shows
        }
        return CoachReply(text, actions)
    }

    private fun dayLabel(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        return "$d ${Calc.dayName(day)}"
    }

    /** Everything the coach is allowed to see: only data the user has logged in this app. */
    suspend fun buildContext(repo: Repository): String = withContext(Dispatchers.IO) {
        val db = repo.db
        val p = repo.currentProfile()
        val today = Calc.today()
        val sb = StringBuilder()
        sb.append("Today: ${dayLabel(today)}\n\nPROFILE\n")
        fun line(label: String, v: String) { if (v.isNotBlank()) sb.append("$label: $v\n") }
        line("Age", p.age); line("Sex", p.sex); line("Height cm", p.heightCm)
        line("Weight kg", p.weightKg); line("Training experience", p.experience); line("Running experience", p.runningExp)
        line("Primary goal", p.primaryGoal); line("Other goals", p.secondaryGoals.joinToString(", "))
        line("Training days", p.trainingDays.sorted().joinToString(", ") { Calc.weekdayShort(it) })
        line("Usual session minutes", p.sessionMin); line("Sleep target h", p.sleepHours); line("Injuries or limits", p.injuries)
        line("5K PB", p.pb5k); line("10K PB", p.pb10k); line("Half marathon PB", p.pbHalf); line("Marathon PB", p.pbMarathon)
        line("Squat 1RM kg", p.squat1rm); line("Bench 1RM kg", p.bench1rm); line("Deadlift 1RM kg", p.deadlift1rm); line("OHP 1RM kg", p.ohp1rm)
        line("Nutrition goal", p.nutritionGoal)

        val sessions = db.sessions().allNow()
        val past = sessions.filter { it.dateEpochDay in (today - 28)..(today - 1) && it.discipline != "REST" }
        sb.append("\nLAST 28 DAYS OF SESSIONS\n")
        if (past.isEmpty()) sb.append("none logged\n")
        past.forEach {
            sb.append("${dayLabel(it.dateEpochDay)} | ${it.discipline} | ${it.title} | ${it.status}")
            if (it.difficulty > 0) sb.append(" | felt ${it.difficulty}/10")
            if (it.distanceKm > 0) sb.append(" | ${String.format(Locale.US, "%.1f", it.distanceKm)} km in ${Calc.durationLabel(it.durationSec)}")
            sb.append("\n")
        }
        val todayAndNext = sessions.filter { it.dateEpochDay in today..(today + 7) && it.discipline != "REST" }
        sb.append("\nUPCOMING (today and next 7 days). Only PLANNED ones can be changed.\n")
        if (todayAndNext.isEmpty()) sb.append("nothing planned\n")
        todayAndNext.forEach {
            sb.append("id=${it.id} | ${dayLabel(it.dateEpochDay)} | ${it.discipline} | ${it.title} | ${it.status}")
            if (it.reason.isNotBlank()) sb.append(" | why scheduled: ${it.reason.take(220)}")
            sb.append("\n")
        }

        val sets = db.setLogs().allNow()
        sb.append("\nWEEKLY GYM VOLUME (sets, kg x reps), most recent 4 weeks\n")
        val ws = Calc.weekStart(today)
        for (k in 0..3) {
            val from = ws - 7L * k
            val inWeek = sets.filter { it.dateEpochDay in from..(from + 6) }
            sb.append("Week of ${LocalDate.ofEpochDay(from)}: ${inWeek.size} sets, ${inWeek.sumOf { it.weightKg * it.reps }.toInt()} kg total\n")
        }
        val names = db.exercises().allNow().associate { it.id to it.name }
        val best = sets.filter { it.dateEpochDay >= today - 90 && it.weightKg > 0 }
            .groupBy { it.exerciseId }
            .mapValues { (_, v) -> v.maxOf { Calc.e1rm(it.weightKg, it.reps) } }
            .entries.sortedByDescending { it.value }.take(8)
        sb.append("\nBEST ESTIMATED 1RM, LAST 90 DAYS\n")
        if (best.isEmpty()) sb.append("none logged\n")
        best.forEach { sb.append("${names[it.key] ?: it.key}: ${Calc.kg(Math.round(it.value * 10) / 10.0)} kg\n") }

        val checks = db.checkIns().allNow().filter { it.dateEpochDay >= today - 7 }
        sb.append("\nCHECK-INS, LAST 7 DAYS (1-5 scales; soreness and stress: higher is worse)\n")
        if (checks.isEmpty()) sb.append("none logged\n")
        checks.forEach {
            sb.append("${dayLabel(it.dateEpochDay)}: sleep ${Calc.kg(it.sleepHours)} h, sleep quality ${it.sleepQuality}, soreness ${it.soreness}, stress ${it.stress}, energy ${it.energy}")
            if (it.restingHr > 0) sb.append(", resting HR ${it.restingHr}")
            sb.append("\n")
        }

        val weights = db.bodyweight().allNow().takeLast(6)
        sb.append("\nBODY WEIGHT (latest entries)\n")
        if (weights.isEmpty()) sb.append("none logged\n")
        weights.forEach { sb.append("${LocalDate.ofEpochDay(it.dateEpochDay)}: ${Calc.kg(it.kg)} kg\n") }

        val foods = db.foods().allNow().filter { it.dateEpochDay in (today - 6)..today }
        sb.append("\nNUTRITION, LAST 7 DAYS\n")
        val byDay = foods.groupBy { it.dateEpochDay }
        if (byDay.isEmpty()) sb.append("none logged\n")
        else {
            sb.append("Days logged: ${byDay.size}. Average on logged days: ${byDay.values.map { d -> d.sumOf { it.kcal } }.average().toInt()} kcal, ${byDay.values.map { d -> d.sumOf { it.proteinG } }.average().toInt()} g protein\n")
        }
        Nutrition.targets(p)?.let { sb.append("Estimated targets: ${it.kcal} kcal, ${it.proteinG} g protein\n") }
        sb.toString()
    }

    /** Checks an action against the current session before it is shown. */
    fun describe(a: CoachAction, s: SessionEntity): CoachAction =
        a.copy(label = when (a.kind) {
            "ease_session" -> "Ease: ${s.title} (${Calc.dayName(s.dateEpochDay)} ${Calc.dateLabel(s.dateEpochDay)})"
            "skip_session" -> "Skip: ${s.title} (${Calc.dayName(s.dateEpochDay)} ${Calc.dateLabel(s.dateEpochDay)})"
            else -> "Move: ${s.title} to ${Calc.dayName(a.date ?: s.dateEpochDay)} ${Calc.dateLabel(a.date ?: s.dateEpochDay)}"
        })
}
