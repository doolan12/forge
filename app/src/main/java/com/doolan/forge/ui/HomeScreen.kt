package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.doolan.forge.data.SessionEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, openWorkout: (Long) -> Unit) {
    val profile by vm.profile.collectAsState()
    val today = remember { Calc.today() }
    val weekStart = remember { Calc.weekStart(today) }
    val weekEnd = weekStart + 6

    val sessions by remember { vm.sessionsBetween(weekStart, weekEnd) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sets by remember { vm.setsBetween(weekStart, weekEnd) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var showPlan by remember { mutableStateOf(false) }

    val todays = sessions.filter { it.dateEpochDay == today }
    val planned = sessions.count { it.discipline != "REST" && it.status != "SKIPPED" }
    val done = sessions.count { it.discipline != "REST" && it.status == "DONE" }
    val runKm = sessions.filter { it.status == "DONE" }.sumOf { it.distanceKm }
    val volume = sets.sumOf { it.weightKg * it.reps }
    val name = profile?.name.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle(
            if (name.isBlank()) "Today" else "Hi, $name",
            "${Calc.dayNameFull(today)} ${Calc.dateLabel(today)}"
        )

        SectionTitle("Today's training")
        if (todays.isEmpty()) {
            ForgeCard {
                Text("Nothing planned today", fontWeight = FontWeight.SemiBold)
                Text("Plan a session, or start an empty workout and add exercises as you go.", color = Muted, fontSize = 13.sp)
                Gap(10)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { showPlan = true }) { Text("Plan a session") }
                    OutlinedButton(onClick = {
                        vm.addSession(
                            SessionEntity(dateEpochDay = today, title = "Workout", discipline = "GYM")
                        ) { id -> openWorkout(id) }
                    }) { Text("Quick workout") }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todays.forEach { s -> TodayCard(s, openWorkout) }
            }
        }

        SectionTitle("This week")
        ForgeCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("$done / $planned", "sessions done")
                Stat("${sets.size}", "sets logged")
                Stat(if (volume >= 1000) String.format("%.1fk", volume / 1000) else "${volume.toInt()}", "kg volume")
            }
            Gap(12)
            ProgressBar(if (planned == 0) 0f else done / planned.toFloat())
            if (runKm > 0) {
                Gap(8)
                Text("Running: ${String.format("%.1f", runKm)} km this week", color = Muted, fontSize = 13.sp)
            }
        }

        SectionTitle("Goals")
        ForgeCard {
            val p = profile
            if (p != null) {
                Text("Primary: ${p.primaryGoal}", fontWeight = FontWeight.SemiBold)
                if (p.secondaryGoals.isNotEmpty()) {
                    Text("Also: ${p.secondaryGoals.joinToString(", ")}", color = Muted, fontSize = 13.sp)
                }
                if (p.trainingDays.isNotEmpty()) {
                    Text(
                        "Training days: ${p.trainingDays.sorted().joinToString(", ") { Calc.weekdayShort(it) }}",
                        color = Muted, fontSize = 13.sp
                    )
                }
            }
        }
        Gap(24)
    }

    if (showPlan) {
        PlanSessionDialog(initialDay = today, onCreate = { vm.addSession(it) }, onDismiss = { showPlan = false })
    }
}

@Composable
private fun TodayCard(s: SessionEntity, openWorkout: (Long) -> Unit) {
    ForgeCard(onClick = if (s.discipline == "REST") null else ({ openWorkout(s.id) })) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            when (s.status) {
                "DONE" -> Tag("Done")
                "SKIPPED" -> Tag("Skipped", Warn)
                else -> Tag(disciplineLabel(s.discipline), Blue)
            }
        }
        if (s.discipline != "REST") {
            Text("${s.durationMin} min planned", color = Muted, fontSize = 13.sp)
            if (s.status == "DONE" && s.distanceKm > 0) {
                Text(
                    "${String.format("%.2f", s.distanceKm)} km | ${Calc.paceLabel(s.distanceKm, s.durationSec)}",
                    color = Muted, fontSize = 13.sp
                )
            }
            Gap(8)
            Text(
                when (s.status) {
                    "DONE" -> "Tap to review"
                    else -> if (s.startedAt > 0) "Tap to continue" else "Tap to start"
                },
                color = Accent, fontWeight = FontWeight.SemiBold
            )
        } else {
            Text("Recover well today.", color = Muted, fontSize = 13.sp)
        }
    }
}
