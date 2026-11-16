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

@Composable
fun GeneratorScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val available by vm.availableEquipment.collectAsState()
    val existing by vm.programmes.collectAsState()

    var goal by remember { mutableStateOf(if (profile?.primaryGoal == "Strength") "Strength" else "Hypertrophy") }
    var split by remember { mutableStateOf("Push/Pull/Legs") }
    var method by remember { mutableStateOf("5/3/1") }
    var days by remember { mutableStateOf(3) }
    var weeks by remember { mutableStateOf(8) }
    var startNext by remember { mutableStateOf(true) }
    var avoid by remember { mutableStateOf(setOf<String>()) }
    var replaceOld by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<GenResult?>(null) }
    var saving by remember { mutableStateOf(false) }

    val range = Generator.daysRange(goal, split)
    val shownDays = days.coerceIn(range.first, range.last)
    val avoidable = exercises.filter { it.id in Generator.AVOIDABLE }
    val p = profile

    fun options() = GenOptions(
        goal = goal, split = split, method = method, daysPerWeek = shownDays, weeks = weeks,
        startNextWeek = startNext, avoid = avoid
    )

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

        SectionTitle("Goal")
        ChipChoice(listOf("Hypertrophy", "Strength"), goal) { goal = it; result = null }

        if (goal == "Hypertrophy") {
            SectionTitle("Split")
            ChipChoice(Generator.HYP_SPLITS, split) { split = it; result = null }
        } else {
            SectionTitle("Method")
            ChipChoice(Generator.STRENGTH_METHODS, method) { method = it; result = null }
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

        SectionTitle("Sessions per week")
        ChipChoice(range.map { it.toString() }, shownDays.toString()) { days = it.toInt(); result = null }
        if (p != null) {
            Text(
                if (p.trainingDays.isEmpty()) "You have no training days set. Add them in Profile."
                else "Placed on your training days: ${p.trainingDays.sorted().joinToString(", ") { Calc.weekdayShort(it) }}",
                color = if (p.trainingDays.isEmpty()) Warn else Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        SectionTitle("Length")
        ChipChoice(listOf("4", "6", "8", "12"), weeks.toString()) { weeks = it.toInt(); result = null }
        Text("weeks. The last week of each 4 is an easier deload week.", color = Muted, fontSize = 13.sp)

        SectionTitle("Start")
        ChipChoice(listOf("Next week", "This week"), if (startNext) "Next week" else "This week") {
            startNext = it == "Next week"; result = null
        }

        SectionTitle("Exercises to avoid")
        ChipMulti(avoidable.map { it.name }, avoid.mapNotNull { id -> avoidable.firstOrNull { it.id == id }?.name }.toSet()) { name ->
            val ex = avoidable.first { it.name == name }
            avoid = if (ex.id in avoid) avoid - ex.id else avoid + ex.id
            result = null
        }
        Text("Pick anything that hurts or you can't do. A suitable replacement is chosen.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

        if (existing.isNotEmpty()) {
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = replaceOld, onCheckedChange = { replaceOld = it })
                Text("  Replace my current programme's upcoming sessions", fontSize = 14.sp)
            }
        }

        Gap(16)
        Button(
            onClick = { if (p != null) result = Generator.generate(options(), p, exercises, available, Calc.today()) },
            enabled = p != null && p.trainingDays.isNotEmpty() && exercises.isNotEmpty(),
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
                Text("No sessions could be built. Check your equipment list and training days.", color = Warn)
            } else {
                val firstWeek = res.sessions.minOf { it.weekNo }
                val preview = res.sessions.filter { it.weekNo == firstWeek }
                SectionTitle("Week $firstWeek preview (${res.sessions.size} sessions in total)")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    preview.forEach { g ->
                        ForgeCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(g.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${Calc.dayName(g.dayEpoch)} ${Calc.dateLabel(g.dayEpoch)}", color = Muted, fontSize = 13.sp)
                            }
                            Text("About ${g.minutes} min", color = Muted, fontSize = 12.sp)
                            Gap(6)
                            g.exercises.forEach { e ->
                                val wt = if (e.weightKg > 0) " @ ${Calc.kg(e.weightKg)} kg" else ""
                                Text(
                                    "${e.exercise.name}: ${e.sets} x ${if (e.repsMin == e.repsMax) "${e.repsMin}" else "${e.repsMin}-${e.repsMax}"}$wt, RIR ${e.rir}",
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
                        vm.savePlan(options(), res, replaceOld) { onSaved() }
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
