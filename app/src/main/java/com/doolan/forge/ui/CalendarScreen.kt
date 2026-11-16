package com.doolan.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doolan.forge.data.Calc
import com.doolan.forge.data.SessionEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(vm: AppViewModel, openWorkout: (Long) -> Unit) {
    val profile by vm.profile.collectAsState()
    val today = remember { Calc.today() }

    var weekStart by remember { mutableLongStateOf(Calc.weekStart(today)) }
    var selected by remember { mutableLongStateOf(today) }
    var showPlan by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }

    val sessions by remember(weekStart) { vm.sessionsBetween(weekStart, weekStart + 6) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val daySessions = sessions.filter { it.dateEpochDay == selected }
    val planned = sessions.count { it.discipline != "REST" && it.status != "SKIPPED" }
    val done = sessions.count { it.discipline != "REST" && it.status == "DONE" }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle("Calendar", "Plan your week. Move, swap or skip sessions any time.")

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                weekStart -= 7
                selected = weekStart
            }) { Icon(Icons.Default.KeyboardArrowLeft, "Previous week") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${Calc.dateLabel(weekStart)} - ${Calc.dateLabel(weekStart + 6)}", fontWeight = FontWeight.SemiBold)
                Text("$done of $planned done", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = {
                weekStart += 7
                selected = weekStart
            }) { Icon(Icons.Default.KeyboardArrowRight, "Next week") }
        }

        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..6) {
                val day = weekStart + i
                val count = sessions.count { it.dateEpochDay == day && it.discipline != "REST" }
                val rest = sessions.any { it.dateEpochDay == day && it.discipline == "REST" }
                val isSel = day == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) Accent else CardDark)
                        .clickable { selected = day }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Calc.dayName(day), fontSize = 11.sp,
                        color = if (isSel) Color.Black else Muted
                    )
                    Text(
                        "${Calc.dayNumber(day)}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = if (isSel) Color.Black else if (day == today) Accent else Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (count > 0) "$count" else if (rest) "rest" else " ",
                        fontSize = 11.sp,
                        color = if (isSel) Color.Black else Muted
                    )
                }
            }
        }

        val p = profile
        if (p != null && p.trainingDays.isNotEmpty()) {
            Text(
                "Your training days: ${p.trainingDays.sorted().joinToString(", ") { Calc.weekdayShort(it) }}",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }

        SectionTitle("${Calc.dayNameFull(selected)} ${Calc.dateLabel(selected)}")
        if (daySessions.isEmpty()) {
            Text("Nothing planned for this day.", color = Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            daySessions.forEach { s ->
                SessionCard(
                    s = s, today = today,
                    onOpen = { openWorkout(s.id) },
                    onMove = { delta -> vm.moveSession(s.id, s.dateEpochDay + delta) },
                    onMoveToToday = { vm.moveSession(s.id, today) },
                    onSkip = { vm.skipSession(s) },
                    onDelete = { deleteTarget = s }
                )
            }
        }

        Gap(12)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { showPlan = true }) { Text("+ Add session") }
            OutlinedButton(onClick = {
                vm.addSession(SessionEntity(dateEpochDay = selected, title = "Rest day", discipline = "REST", durationMin = 0))
            }) { Text("+ Rest day") }
        }
        Gap(24)
    }

    if (showPlan) {
        PlanSessionDialog(initialDay = selected, onCreate = { vm.addSession(it) }, onDismiss = { showPlan = false })
    }
    deleteTarget?.let { s ->
        ConfirmDialog(
            title = "Delete this session?",
            text = "${s.title} and any sets logged in it will be removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteSession(s.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SessionCard(
    s: SessionEntity,
    today: Long,
    onOpen: () -> Unit,
    onMove: (Long) -> Unit,
    onMoveToToday: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit
) {
    val missed = s.status == "PLANNED" && s.dateEpochDay < today && s.discipline != "REST"
    ForgeCard(onClick = if (s.discipline == "REST") null else onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (s.discipline != "REST") {
                    Text("${disciplineLabel(s.discipline)} | ${s.durationMin} min", color = Muted, fontSize = 12.sp)
                }
            }
            when {
                s.status == "DONE" -> Tag("Done")
                s.status == "SKIPPED" -> Tag("Skipped", Warn)
                missed -> Tag("Missed", Danger)
                s.discipline == "REST" -> Tag("Rest", Blue)
                else -> Tag("Planned", Blue)
            }
        }
        if (s.status == "DONE" && s.distanceKm > 0) {
            Text(
                "${String.format("%.2f", s.distanceKm)} km | ${Calc.paceLabel(s.distanceKm, s.durationSec)}",
                color = Muted, fontSize = 12.sp
            )
        }
        if (missed) {
            Text(
                "Missed sessions are never stacked onto other days automatically. Move it only if you want to.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Move to previous day") }
            Text("Move", color = Muted, fontSize = 12.sp)
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Move to next day") }
            Box(Modifier.weight(1f))
            if (missed) {
                TextButton(onClick = onMoveToToday) { Text("To today") }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
        }
    }
}
