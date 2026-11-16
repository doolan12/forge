package com.doolan.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.Calc
import com.doolan.forge.data.ExerciseEntity
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(wvm: WorkoutViewModel, app: AppViewModel, onClose: () -> Unit) {
    val exercises by app.exercises.collectAsState()
    val available by app.availableEquipment.collectAsState()
    val s = wvm.session

    var showAdd by remember { mutableStateOf(false) }
    var swapTarget by remember { mutableStateOf<ExBlock?>(null) }
    var removeTarget by remember { mutableStateOf<ExBlock?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(wvm.prMessage) {
        if (wvm.prMessage != null) {
            delay(4500)
            wvm.prMessage = null
        }
    }
    LaunchedEffect(wvm.message) {
        if (wvm.message != null) {
            delay(3500)
            wvm.message = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        if (!wvm.loaded) {
            Text("Loading...", modifier = Modifier.padding(24.dp), color = Muted)
            return@Column
        }
        if (s == null) {
            Text("This session could not be found.", modifier = Modifier.padding(24.dp))
            Button(onClick = onClose) { Text("Back") }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${Calc.dayNameFull(s.dateEpochDay)} ${Calc.dateLabel(s.dateEpochDay)} | ${disciplineLabel(s.discipline)}", color = Muted, fontSize = 13.sp)
            }
            if (s.status == "DONE") Tag("Completed") else if (s.status == "SKIPPED") Tag("Skipped", Warn)
        }

        wvm.prMessage?.let {
            ForgeCard(Modifier.padding(vertical = 4.dp)) { Text(it, color = Accent, fontWeight = FontWeight.Bold) }
        }
        wvm.message?.let {
            ForgeCard(Modifier.padding(vertical = 4.dp)) { Text(it, color = Warn) }
        }
        if (wvm.restRemaining > 0) {
            ForgeCard(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rest", color = Muted, fontSize = 12.sp)
                        Text(Calc.clock(wvm.restRemaining), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    TextButton(onClick = { wvm.addRest(15) }) { Text("+15s") }
                    TextButton(onClick = { wvm.skipRest() }) { Text("Skip") }
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.discipline == "RUN") {
                item {
                    ForgeCard {
                        Text("Run session", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When you finish, enter your distance and time. Live GPS tracking is not part of this version.",
                            color = Muted, fontSize = 13.sp
                        )
                    }
                }
            } else if (wvm.blocks.isEmpty()) {
                item {
                    ForgeCard {
                        Text("No exercises yet", fontWeight = FontWeight.SemiBold)
                        Text("Add exercises from the library. Only ones that fit your equipment are shown by default.", color = Muted, fontSize = 13.sp)
                    }
                }
            }

            items(wvm.blocks, key = { it.se.id }) { block ->
                ForgeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                block.exercise.name, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                "${block.se.targetSets} x ${block.se.repsMin}-${block.se.repsMax} | RIR ${block.se.targetRir} | rest ${block.se.restSec}s",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { detail = block.exercise }) { Text("Info") }
                        if (block.rows.none { it.done }) {
                            IconButton(onClick = { swapTarget = block }) { Icon(Icons.Default.SwapHoriz, "Swap exercise") }
                        }
                        IconButton(onClick = { removeTarget = block }) { Icon(Icons.Default.Delete, "Remove exercise") }
                    }
                    if (block.previous.isNotBlank()) {
                        Text("Last time: ${block.previous}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Gap(8)
                    block.rows.forEachIndexed { i, row ->
                        SetRowView(
                            number = i + 1, row = row,
                            onToggle = { wvm.toggleSet(block, row) },
                            onRemove = { wvm.removeSet(block, row) }
                        )
                    }
                    OutlinedButton(onClick = { wvm.addSet(block) }, modifier = Modifier.padding(top = 6.dp)) { Text("Add set") }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    if (s.discipline != "RUN") {
                        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
                    }
                    if (s.status == "DONE") {
                        OutlinedButton(onClick = { wvm.markPlanned { } }, modifier = Modifier.fillMaxWidth()) { Text("Mark as not completed") }
                    } else {
                        Button(onClick = { showFinish = true }, modifier = Modifier.fillMaxWidth()) { Text("Finish workout") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ExercisePickerDialog(
            title = "Add exercise", exercises = exercises, available = available,
            onPick = { wvm.addExercise(it) }, onDismiss = { showAdd = false }
        )
    }
    swapTarget?.let { b ->
        ExercisePickerDialog(
            title = "Swap ${b.exercise.name}", exercises = exercises, available = available,
            sameMuscle = b.exercise.primaryMuscle, excludeId = b.exercise.id,
            onPick = { wvm.swapExercise(b, it) }, onDismiss = { swapTarget = null }
        )
    }
    removeTarget?.let { b ->
        ConfirmDialog(
            title = "Remove exercise?",
            text = "This removes ${b.exercise.name} and any sets you logged for it in this session.",
            confirmLabel = "Remove",
            onConfirm = { wvm.removeBlock(b) },
            onDismiss = { removeTarget = null }
        )
    }
    detail?.let { ex ->
        ExerciseDetailDialog(
            ex = ex,
            alternatives = exercises.filter { it.id != ex.id && it.primaryMuscle == ex.primaryMuscle && it.usableWith(available) },
            onDismiss = { detail = null }
        )
    }
    if (showFinish && s != null) {
        FinishDialog(
            isRun = s.discipline == "RUN",
            initialNotes = s.notes,
            onFinish = { diff, notes, km, sec ->
                wvm.finish(diff, notes, km, sec) { onClose() }
            },
            onDismiss = { showFinish = false }
        )
    }
}

@Composable
private fun SetRowView(number: Int, row: SetRow, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (row.done) AccentSoft else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$number", modifier = Modifier.width(22.dp).padding(start = 6.dp), color = Muted)
        CompactField(row.weight, { v -> row.weight = v.replace(',', '.').filter { c -> c.isDigit() || c == '.' } }, "kg", Modifier.weight(1f))
        CompactField(row.reps, { v -> row.reps = v.filter { c -> c.isDigit() }.take(3) }, "reps", Modifier.weight(1f))
        CompactField(row.rir, { v -> row.rir = v.filter { c -> c.isDigit() }.take(2) }, "RIR", Modifier.weight(0.8f))
        IconButton(onClick = onToggle) {
            Icon(Icons.Default.Check, if (row.done) "Undo set" else "Complete set", tint = if (row.done) Accent else Muted)
        }
    }
    if (!row.done) {
        // Removing an empty extra set
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.width(14.dp))
                Text(" remove set", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun FinishDialog(
    isRun: Boolean,
    initialNotes: String,
    onFinish: (Int, String, Double, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var difficulty by remember { mutableFloatStateOf(6f) }
    var notes by remember { mutableStateOf(initialNotes) }
    var km by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isRun) {
                    Field("Distance", km, { km = it }, number = true, suffix = "km")
                    Field("Time", minutes, { minutes = it }, number = true, suffix = "min")
                }
                Text("How hard was it? ${difficulty.toInt()} / 10", fontWeight = FontWeight.SemiBold)
                Slider(value = difficulty, onValueChange = { difficulty = it }, valueRange = 1f..10f, steps = 8)
                Field("Notes (optional)", notes, { notes = it }, singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val distance = km.toDoubleOrNull() ?: 0.0
                val sec = ((minutes.toDoubleOrNull() ?: 0.0) * 60).toInt()
                onFinish(difficulty.toInt(), notes.trim(), distance, sec)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
