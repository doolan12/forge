package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.Calc
import com.doolan.forge.data.Hyrox
import com.doolan.forge.data.HyroxLog
import com.doolan.forge.data.HyroxResultEntity

@Composable
fun HyroxScreen(vm: AppViewModel, onBack: () -> Unit) {
    val results by vm.hyroxResults.collectAsState()
    var division by remember { mutableStateOf(Hyrox.DIVISIONS[1].name) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<HyroxResultEntity?>(null) }
    val pred = HyroxLog.predict(results, division)
    val mine = results.filter { it.division == division }.sortedByDescending { it.dateEpochDay * 100000 + it.id }

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
            ScreenTitle("HYROX splits", "Log times, see your predicted finish")
        }

        SectionTitle("Division")
        ChipChoice(Hyrox.DIVISIONS.map { it.name }, division) { division = it }

        SectionTitle("Predicted finish")
        ForgeCard {
            if (pred.loggedCount == 0) {
                Text("Nothing logged yet.", fontWeight = FontWeight.SemiBold)
                Text(
                    "Log a race, a simulation or just a few stations. Once all 16 segments have a time, you get a finish estimate.",
                    color = Muted, fontSize = 13.sp
                )
            } else if (pred.complete) {
                Text(HyroxLog.fmt(pred.totalSec), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Estimated finish from your last 3 times on each segment.", color = Muted, fontSize = 13.sp)
                pred.fadePct?.let {
                    Gap(6)
                    Text(
                        if (it > 3) "Run 8 was $it% slower than Run 1 in your last full effort. Easy runs and compromised running work will help."
                        else "Your running held up well from Run 1 to Run 8.",
                        fontSize = 13.sp
                    )
                }
                if (pred.biggest.isNotEmpty()) {
                    Gap(6)
                    Text("Biggest stations: ${pred.biggest.joinToString(", ")}", color = Muted, fontSize = 13.sp)
                }
            } else {
                Text("${pred.loggedCount} of 16 segments logged", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Log the missing segments to get a finish estimate. Logged so far add up to ${HyroxLog.fmt(pred.totalSec)}.",
                    color = Muted, fontSize = 13.sp
                )
            }
            Gap(6)
            Text(
                "Times from partial sessions are done fresh, so they are lengthened by 5% (runs) and 8% (stations). That is a rough guess. A real race will differ with crowding, heat and transitions.",
                color = Muted, fontSize = 11.sp
            )
        }

        Gap(8)
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Log a result") }

        if (pred.loggedCount > 0) {
            SectionTitle("Your reference times")
            ForgeCard {
                HyroxLog.SEGMENTS.forEachIndexed { i, seg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(seg.label, color = if (seg.isRun) Muted else androidx.compose.ui.graphics.Color.Unspecified, fontSize = 14.sp)
                        Text(if (pred.refs[i] > 0) HyroxLog.fmt(pred.refs[i]) else "-", fontSize = 14.sp)
                    }
                }
            }
        }

        SectionTitle("History")
        if (mine.isEmpty()) Text("No results for $division yet.", color = Muted, fontSize = 13.sp)
        mine.forEach { r ->
            val v = HyroxLog.decode(r.splits)
            val n = v.count { it > 0 }
            ForgeCard(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.kind} | ${Calc.dayName(r.dateEpochDay)} ${Calc.dateLabel(r.dateEpochDay)}", fontWeight = FontWeight.SemiBold)
                        Text(
                            HyroxLog.total(r)?.let { "Total ${HyroxLog.fmt(it)}" } ?: "$n of 16 segments, ${HyroxLog.fmt(v.sum())} logged",
                            color = Muted, fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { deleteId = r }) { Text("Remove", color = Danger) }
                }
            }
        }
        Gap(24)
    }

    if (showAdd) {
        AddHyroxDialog(
            division = division,
            onSave = { vm.addHyrox(it); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }
    deleteId?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Remove this result?") },
            text = { Text("It will no longer count towards your predicted finish.") },
            confirmButton = { TextButton(onClick = { vm.deleteHyrox(r.id); deleteId = null }) { Text("Remove", color = Danger) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddHyroxDialog(division: String, onSave: (HyroxResultEntity) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("Simulation") }
    var day by remember { mutableLongStateOf(Calc.today()) }
    val texts = remember { mutableStateListOf(*Array(16) { "" }) }
    val parsed = texts.map { HyroxLog.parse(it) }
    val bad = texts.indices.any { texts[it].isNotBlank() && parsed[it] == null }
    val any = parsed.any { it != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a result") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChipChoice(HyroxLog.KINDS, kind) { kind = it }
                Text(
                    when (kind) {
                        "Race" -> "A real race. Fill in every segment."
                        "Simulation" -> "A full or half simulation. Leave out what you did not do."
                        else -> "A few stations or runs on their own. Leave the rest blank."
                    },
                    color = Muted, fontSize = 12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { day -= 1 }) { Text("<") }
                    Text("${Calc.dayName(day)} ${Calc.dateLabel(day)}", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { if (day < Calc.today()) day += 1 }) { Text(">") }
                }
                Text("Times as m:ss (for example 4:35)", color = Muted, fontSize = 12.sp)
                HyroxLog.SEGMENTS.forEachIndexed { i, seg ->
                    Field(seg.label, texts[i], { texts[i] = it })
                }
                if (bad) Text("Some times could not be read. Use m:ss.", color = Warn, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = any && !bad, onClick = {
                onSave(
                    HyroxResultEntity(
                        dateEpochDay = day, division = division, kind = kind,
                        splits = HyroxLog.encode(parsed.map { it ?: 0 })
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
