package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.Calc
import com.doolan.forge.data.GenOptions
import com.doolan.forge.data.GenResult
import com.doolan.forge.data.Generator
import com.doolan.forge.data.HybridOptions
import com.doolan.forge.data.HybridPlanner
import com.doolan.forge.data.Hyrox
import com.doolan.forge.data.HyroxOptions
import com.doolan.forge.data.RunOptions
import com.doolan.forge.data.Running
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val GYM = "Gym"
private const val RUN = "Running"
private const val HYR = "HYROX"

private fun parseDate(text: String): Long? = try {
    LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("d/M/yyyy")).toEpochDay()
} catch (e: Exception) {
    null
}

@Composable
fun GeneratorScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val available by vm.availableEquipment.collectAsState()
    val existing by vm.programmes.collectAsState()

    var ready by remember { mutableStateOf(false) }
    var comps by remember { mutableStateOf(setOf(GYM)) }
    var primary by remember { mutableStateOf(GYM) }

    var goal by remember { mutableStateOf("Hypertrophy") }
    var split by remember { mutableStateOf("Push/Pull/Legs") }
    var method by remember { mutableStateOf("5/3/1") }
    var days by remember { mutableStateOf(3) }

    var distance by remember { mutableStateOf("10K") }
    var runs by remember { mutableStateOf(3) }
    var currentKm by remember { mutableStateOf("20") }

    var division by remember { mutableStateOf("Men Open") }
    var hxSessions by remember { mutableStateOf(2) }

    var eventText by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf(8) }
    var startNext by remember { mutableStateOf(true) }
    var avoid by remember { mutableStateOf(setOf<String>()) }
    var replaceOld by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<GenResult?>(null) }
    var saving by remember { mutableStateOf(false) }

    // start from what the user said in their profile
    LaunchedEffect(profile) {
        val pr = profile
        if (!ready && pr != null) {
            val all = (setOf(pr.primaryGoal) + pr.secondaryGoals)
            val c = HashSet<String>()
            if (all.contains("Hypertrophy") || all.contains("Strength")) c.add(GYM)
            if (all.any { it.startsWith("Marathon") }) c.add(RUN)
            if (all.contains("HYROX")) c.add(HYR)
            if (c.isEmpty()) c.add(GYM)
            comps = c
            primary = when {
                pr.primaryGoal == "HYROX" -> HYR
                pr.primaryGoal.startsWith("Marathon") -> RUN
                else -> GYM
            }
            if (primary !in c) primary = c.first()
            goal = if (pr.primaryGoal == "Strength" || (pr.primaryGoal != "Hypertrophy" && pr.secondaryGoals.contains("Strength"))) "Strength" else "Hypertrophy"
            division = if (pr.sex == "Female") "Women Open" else "Men Open"
            ready = true
        }
    }

    val p = profile
    val range = Generator.daysRange(goal, split)
    val shownDays = days.coerceIn(range.first, range.last)
    val avoidable = exercises.filter { it.id in Generator.AVOIDABLE }
    val hasRaceGoal = (primary == HYR && HYR in comps) || (primary == RUN && RUN in comps && distance != "General fitness")
    val eventDay: Long? = if (hasRaceGoal) parseDate(eventText) else null
    val eventBad = hasRaceGoal && eventText.isNotBlank() && eventDay == null

    fun hybrid(): HybridOptions {
        val g = if (GYM in comps) GenOptions(
            goal = goal, split = split, method = method, daysPerWeek = shownDays, weeks = weeks,
            startNextWeek = startNext, avoid = avoid
        ) else null
        val r = if (RUN in comps) RunOptions(distance, runs, currentKm.toDoubleOrNull() ?: 20.0) else null
        val h = if (HYR in comps) HyroxOptions(division, hxSessions) else null
        return HybridOptions(g, r, h, primary, weeks, startNext, eventDay)
    }

    fun planName(): String {
        val order = listOf(primary) + listOf(GYM, RUN, HYR).filter { it != primary }
        return order.filter { it in comps }.joinToString(" + ") {
            when (it) {
                GYM -> if (goal == "Strength") "Strength ($method)" else "Hypertrophy ($split)"
                RUN -> "Running ($distance)"
                else -> "HYROX ($division)"
            }
        }
    }

    fun changed() { result = null }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            ScreenTitle("Build a programme", "Uses your equipment, days and goals")
        }

        SectionTitle("What do you want to train?")
        ChipMulti(listOf(GYM, RUN, HYR), comps) { c ->
            val next = if (c in comps) comps - c else comps + c
            if (next.isNotEmpty()) {
                comps = next
                if (primary !in next) primary = next.first()
                changed()
            }
        }
        Text(
            "Pick more than one to train for a hybrid goal. The app keeps heavy leg days, long runs and hard sessions apart.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
        )

        if (comps.size > 1) {
            SectionTitle("Main focus")
            ChipChoice(listOf(GYM, RUN, HYR).filter { it in comps }, primary) { primary = it; changed() }
            Text(
                "Your main focus gets the freshest days and full volume. The others are trimmed first if you are short of days.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (GYM in comps) {
            SectionTitle("Gym goal")
            ChipChoice(listOf("Hypertrophy", "Strength"), goal) { goal = it; changed() }
            if (goal == "Hypertrophy") {
                SectionTitle("Split")
                ChipChoice(Generator.HYP_SPLITS, split) { split = it; changed() }
            } else {
                SectionTitle("Method")
                ChipChoice(Generator.STRENGTH_METHODS, method) { method = it; changed() }
                Text(
                    when (method) {
                        "5/3/1" -> "Four week cycles of heavy compound lifts at set percentages of your training max."
                        "DUP" -> "The same lift is trained heavy, medium and light on different days."
                        else -> "A few heavy sets of 5, adding weight week by week."
                    },
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                )
                if (p != null && listOf(p.squat1rm, p.bench1rm, p.deadlift1rm, p.ohp1rm).all { it.toDoubleOrNull() == null }) {
                    Text(
                        "Tip: add your best squat, bench, deadlift and press in Profile so the weights can be filled in for you.",
                        color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            SectionTitle("Gym sessions per week")
            ChipChoice(range.map { it.toString() }, shownDays.toString()) { days = it.toInt(); changed() }
        }

        if (RUN in comps) {
            SectionTitle("Running: target")
            ChipChoice(Running.DISTANCES, distance) { distance = it; changed() }
            SectionTitle("Runs per week")
            ChipChoice((1..6).map { it.toString() }, runs.toString()) { runs = it.toInt(); changed() }
            Gap(8)
            Field("Km you run in a normal week now", currentKm, { currentKm = it; changed() }, number = true, suffix = "km")
            if (p != null && Running.pacesFrom(p) == null) {
                Text(
                    "Tip: add a recent 5K, 10K, half or marathon time in Profile (like 24:30) and your paces are worked out for you.",
                    color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                )
            } else if (p != null) {
                Running.pacesFrom(p)?.let {
                    Text("Paces will be based on your ${it.basis}.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (HYR in comps) {
            SectionTitle("HYROX: division")
            ChipChoice(Hyrox.DIVISIONS.map { it.name }, division) { division = it; changed() }
            val d = Hyrox.division(division)
            Text(
                "Race weights: sled push ${d.pushKg} kg, sled pull ${d.pullKg} kg, farmers ${d.farmersKgEach} kg each hand, sandbag ${d.sandbagKg} kg, wall ball ${d.wallBallKg} kg. Sled weights include the sled.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
            SectionTitle("HYROX sessions per week")
            ChipChoice(listOf("1", "2", "3"), hxSessions.toString()) { hxSessions = it.toInt(); changed() }
        }

        if (p != null) {
            Text(
                if (p.trainingDays.isEmpty()) "You have no training days set. Add them in Profile."
                else "Sessions go on your training days: ${p.trainingDays.sorted().joinToString(", ") { Calc.weekdayShort(it) }}",
                color = if (p.trainingDays.isEmpty()) Warn else Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (hasRaceGoal) {
            SectionTitle("Event date (optional)")
            Field("Date as day/month/year, for example 14/6/2027", eventText, { eventText = it; changed() })
            Text(
                when {
                    eventBad -> "That date is not in the right format. Use day/month/year."
                    eventDay != null -> "The plan runs until your event, finishes with a taper and puts the event on your calendar."
                    else -> "Leave blank for a plan with no fixed event."
                },
                color = if (eventBad) Warn else Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (eventDay == null) {
            SectionTitle("Length")
            ChipChoice(listOf("4", "6", "8", "12", "16"), weeks.toString()) { weeks = it.toInt(); changed() }
            Text("weeks. The last week of each 4 is an easier week.", color = Muted, fontSize = 13.sp)
        }

        SectionTitle("Start")
        ChipChoice(listOf("Next week", "This week"), if (startNext) "Next week" else "This week") {
            startNext = it == "Next week"; changed()
        }

        if (GYM in comps) {
            SectionTitle("Exercises to avoid")
            ChipMulti(avoidable.map { it.name }, avoid.mapNotNull { id -> avoidable.firstOrNull { it.id == id }?.name }.toSet()) { name ->
                val ex = avoidable.first { it.name == name }
                avoid = if (ex.id in avoid) avoid - ex.id else avoid + ex.id
                changed()
            }
            Text("Pick anything that hurts or you can't do. A suitable replacement is chosen.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        if (existing.isNotEmpty()) {
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = replaceOld, onCheckedChange = { replaceOld = it })
                Text("  Replace my current programme's upcoming sessions", fontSize = 14.sp)
            }
        }

        Gap(16)
        Button(
            onClick = { if (p != null) result = HybridPlanner.build(hybrid(), p, exercises, available, Calc.today()) },
            enabled = p != null && p.trainingDays.isNotEmpty() && exercises.isNotEmpty() && !eventBad,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Preview programme") }

        val res = result
        if (res != null) {
            if (res.notes.isNotEmpty()) {
                SectionTitle("Things to know")
                ForgeCard {
                    res.notes.forEach { Text("- $it", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp)) }
                }
            }
            if (res.sessions.isEmpty()) {
                Gap(8)
                Text("No sessions could be built. Check your equipment list, training days and event date.", color = Warn)
            } else {
                val firstWeek = res.sessions.minOf { it.weekNo }
                val preview = res.sessions.filter { it.weekNo == firstWeek }
                SectionTitle("Week $firstWeek preview (${res.sessions.size} sessions in total)")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    preview.forEach { g ->
                        ForgeCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(g.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Text("${Calc.dayName(g.dayEpoch)} ${Calc.dateLabel(g.dayEpoch)}", color = Muted, fontSize = 13.sp)
                            }
                            Text("${disciplineLabel(g.discipline)} | about ${g.minutes} min", color = Muted, fontSize = 12.sp)
                            Gap(6)
                            if (g.workout.isNotBlank()) {
                                Text(g.workout, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            g.exercises.forEach { e ->
                                val wt = if (e.weightKg > 0) " @ ${Calc.kg(e.weightKg)} kg" else ""
                                Text(
                                    "${e.exercise.name}: ${e.sets} x ${if (e.repsMin == e.repsMax) "${e.repsMin}" else "${e.repsMin}-${e.repsMax}"}$wt",
                                    fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            Gap(6)
                            Text(g.reason, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Gap(12)
                Button(
                    onClick = {
                        saving = true
                        vm.savePlan(planName(), primary, "Main focus: $primary", res.sessions.maxOf { it.weekNo }, res, replaceOld) { onSaved() }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (saving) "Adding..." else "Add ${res.sessions.size} sessions to my calendar") }
                OutlinedButton(onClick = { result = null }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Change options") }
            }
        }
        Gap(32)
    }
}
