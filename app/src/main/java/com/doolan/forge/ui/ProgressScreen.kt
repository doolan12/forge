package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doolan.forge.data.Calc

private val RANGES = listOf("Week" to 7, "Month" to 30, "3 months" to 90, "6 months" to 180, "Year" to 365)

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val exercises by vm.exercises.collectAsState()
    val bodyweight by vm.bodyweight.collectAsState()

    var rangeLabel by remember { mutableStateOf("Month") }
    var chosenExercise by remember { mutableStateOf<String?>(null) }
    val days = RANGES.first { it.first == rangeLabel }.second
    val today = remember { Calc.today() }
    val from = today - days + 1

    val sessions by remember(days) { vm.sessionsBetween(from, today) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sets by remember(days) { vm.setsBetween(from, today) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val exById = exercises.associateBy { it.id }
    val weeks = (days / 7.0).coerceAtLeast(1.0)

    val doneCount = sessions.count { it.discipline != "REST" && it.status == "DONE" }
    val missedCount = sessions.count {
        it.discipline != "REST" && (it.status == "SKIPPED" || (it.status == "PLANNED" && it.dateEpochDay < today))
    }
    val volume = sets.sumOf { it.weightKg * it.reps }
    val runKm = sessions.filter { it.status == "DONE" }.sumOf { it.distanceKm }

    // sets per muscle in range
    val perMuscle = sets
        .groupBy { exById[it.exerciseId]?.primaryMuscle ?: "Other" }
        .map { (m, l) -> m to l.size }
        .sortedByDescending { it.second }

    // exercises with logs, most-logged first
    val loggedExercises = sets.groupBy { it.exerciseId }
        .map { (id, l) -> id to l.size }
        .sortedByDescending { it.second }
        .mapNotNull { (id, _) -> exById[id] }
        .take(12)
    val activeId = chosenExercise?.takeIf { id -> loggedExercises.any { it.id == id } } ?: loggedExercises.firstOrNull()?.id
    val exSets = sets.filter { it.exerciseId == activeId }
    val dailyBest = exSets.groupBy { it.dateEpochDay }
        .map { (d, l) -> d to (l.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: 0.0) }
        .filter { it.second > 0 }
        .sortedBy { it.first }

    // personal records (best estimated 1RM per exercise in range)
    val prs = sets.groupBy { it.exerciseId }
        .mapNotNull { (id, l) ->
            val best = l.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: return@mapNotNull null
            val e = Calc.e1rm(best.weightKg, best.reps)
            if (e <= 0) null else Triple(exById[id]?.name ?: id, best, e)
        }
        .sortedByDescending { it.third }
        .take(6)

    val weightsInRange = bodyweight.filter { it.dateEpochDay in from..today }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle("Progress", "Built only from what you have logged")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RANGES.map { it.first }) { r ->
                FilterChip(selected = r == rangeLabel, onClick = { rangeLabel = r }, label = { Text(r) })
            }
        }

        SectionTitle("Summary")
        ForgeCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("$doneCount", "sessions done")
                Stat("${sets.size}", "sets logged")
                Stat(if (volume >= 1000) String.format("%.1fk", volume / 1000) else "${volume.toInt()}", "kg volume")
            }
            Gap(12)
            val total = doneCount + missedCount
            Text(
                if (total == 0) "No sessions in this period yet."
                else "Consistency: ${(doneCount * 100 / total)}% ($doneCount done, $missedCount missed or skipped)",
                color = Muted, fontSize = 13.sp
            )
            if (runKm > 0) {
                Text("Running: ${String.format("%.1f", runKm)} km (${String.format("%.1f", runKm / weeks)} km per week)", color = Muted, fontSize = 13.sp)
            }
        }

        SectionTitle("Sets per muscle group")
        ForgeCard {
            if (perMuscle.isEmpty()) {
                Text("Log some sets and they will show up here.", color = Muted, fontSize = 13.sp)
            } else {
                BarList(perMuscle.map { (m, n) -> Triple(m, n.toFloat(), "$n") })
                Gap(6)
                Text("Average per week: ${perMuscle.joinToString(", ") { (m, n) -> "$m ${String.format("%.1f", n / weeks)}" }}", color = Muted, fontSize = 12.sp)
            }
        }

        SectionTitle("Strength progression (estimated 1RM)")
        ForgeCard {
            if (loggedExercises.isEmpty()) {
                Text("No lifts logged in this period.", color = Muted, fontSize = 13.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(loggedExercises, key = { it.id }) { e ->
                        FilterChip(selected = e.id == activeId, onClick = { chosenExercise = e.id }, label = { Text(e.name) })
                    }
                }
                Gap(8)
                if (dailyBest.size >= 2) {
                    LineChart(
                        values = dailyBest.map { it.second },
                        firstLabel = Calc.dateLabel(dailyBest.first().first),
                        lastLabel = Calc.dateLabel(dailyBest.last().first),
                        unit = "kg"
                    )
                } else if (dailyBest.size == 1) {
                    Text(
                        "Best so far: ${Calc.kg(Math.round(dailyBest[0].second * 10) / 10.0)} kg est. 1RM on ${Calc.dateLabel(dailyBest[0].first)}. Log it again on another day to see a trend.",
                        color = Muted, fontSize = 13.sp
                    )
                }
            }
        }

        SectionTitle("Personal records in this period")
        ForgeCard {
            if (prs.isEmpty()) {
                Text("No records yet.", color = Muted, fontSize = 13.sp)
            } else {
                prs.forEach { (name, set, e) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            Text("${Calc.kg(set.weightKg)} kg x ${set.reps} on ${Calc.dateLabel(set.dateEpochDay)}", color = Muted, fontSize = 12.sp)
                        }
                        Text("${Calc.kg(Math.round(e * 10) / 10.0)} kg", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionTitle("Body weight")
        ForgeCard {
            if (weightsInRange.isEmpty()) {
                Text("Log your weight from the Profile tab to see the trend.", color = Muted, fontSize = 13.sp)
            } else {
                val first = weightsInRange.first()
                val last = weightsInRange.last()
                Text("${Calc.kg(last.kg)} kg now (${if (last.kg - first.kg >= 0) "+" else ""}${Calc.kg(Math.round((last.kg - first.kg) * 10) / 10.0)} kg over this period)", fontWeight = FontWeight.SemiBold)
                if (weightsInRange.size >= 2) {
                    Gap(6)
                    LineChart(
                        values = weightsInRange.map { it.kg },
                        firstLabel = Calc.dateLabel(first.dateEpochDay),
                        lastLabel = Calc.dateLabel(last.dateEpochDay),
                        unit = "kg"
                    )
                }
            }
        }
        Gap(24)
    }
}
