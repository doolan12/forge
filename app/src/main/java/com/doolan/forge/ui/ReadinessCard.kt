package com.doolan.forge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import com.doolan.forge.data.HealthSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doolan.forge.data.Calc
import com.doolan.forge.data.CheckInEntity
import com.doolan.forge.data.ReadinessCalc
import com.doolan.forge.data.SessionEntity

@Composable
fun ReadinessCard(vm: AppViewModel, today: Long, todays: List<SessionEntity>) {
    val checkIns by vm.checkIns.collectAsState()
    val profile by vm.profile.collectAsState()
    val recent by remember { vm.sessionsBetween(today - 27, today) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var confirmEase by remember { mutableStateOf<SessionEntity?>(null) }

    val todayCheck = checkIns.lastOrNull { it.dateEpochDay == today }
    val target = profile?.sleepHours?.toDoubleOrNull() ?: 8.0
    val readiness = todayCheck?.let { ReadinessCalc.score(it, target, checkIns) }
    val load = ReadinessCalc.load(recent, today)

    ForgeCard {
        if (todayCheck == null || readiness == null) {
            Text("Daily check-in", fontWeight = FontWeight.SemiBold)
            Text(
                "Takes 20 seconds. Log how you slept and feel, and the app gives you a readiness score from your own answers.",
                color = Muted, fontSize = 13.sp
            )
            Gap(10)
            Button(onClick = { showDialog = true }) { Text("Check in") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Readiness", color = Muted, fontSize = 12.sp)
                    Text(
                        "${readiness.score}",
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = if (readiness.low) Warn else Accent
                    )
                }
                Tag(readiness.label, if (readiness.low) Warn else Accent)
            }
            Gap(6)
            Text(readiness.advice, fontSize = 14.sp)
            readiness.factors.forEach {
                Text("- $it", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            val easable = todays.firstOrNull {
                it.status == "PLANNED" && it.discipline != "REST" && !it.reason.contains("Eased on")
            }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (readiness.low && easable != null) {
                    Button(onClick = { confirmEase = easable }) { Text("Ease today") }
                }
                OutlinedButton(onClick = { showDialog = true }) { Text("Edit check-in") }
            }
            val scores = checkIns.takeLast(14).map { ReadinessCalc.score(it, target, checkIns).score.toDouble() }
            if (scores.size >= 3) {
                Gap(8)
                LineChart(scores, "${scores.size} check-ins ago", "today", "")
            }
        }
        if (load != null) {
            Gap(8)
            Text(
                "Training load this week is ${load.label} (${String.format("%.1f", load.ratio)}x your usual). Load is how hard you rated each session times its length.",
                color = Muted, fontSize = 12.sp
            )
        }
    }

    if (showDialog) {
        CheckInDialog(
            initial = todayCheck, today = today,
            onSave = { vm.saveCheckIn(it) }, onDismiss = { showDialog = false }
        )
    }
    confirmEase?.let { s ->
        ConfirmDialog(
            title = "Ease today's session?",
            text = "${s.title} gets one fewer set on each exercise and one more rep in reserve (runs and HYROX get an easy alternative at the top). Nothing is added to other days.",
            confirmLabel = "Ease it",
            onConfirm = { vm.easeSession(s.id) },
            onDismiss = { confirmEase = null }
        )
    }
}

@Composable
private fun Rating(label: String, low: String, high: String, value: Int, onChange: (Int) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    Text("1 = $low, 5 = $high", color = Muted, fontSize = 12.sp)
    ChipChoice((1..5).map { it.toString() }, value.toString()) { onChange(it.toInt()) }
}

@Composable
fun CheckInDialog(initial: CheckInEntity?, today: Long, onSave: (CheckInEntity) -> Unit, onDismiss: () -> Unit) {
    var sleep by remember { mutableStateOf(initial?.let { Calc.kg(it.sleepHours) } ?: "") }
    var quality by remember { mutableStateOf(initial?.sleepQuality ?: 3) }
    var sore by remember { mutableStateOf(initial?.soreness ?: 2) }
    var stress by remember { mutableStateOf(initial?.stress ?: 2) }
    var energy by remember { mutableStateOf(initial?.energy ?: 3) }
    var hr by remember { mutableStateOf(initial?.restingHr?.takeIf { it > 0 }?.toString() ?: "") }
    val hours = sleep.toDoubleOrNull()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hcMessage by remember { mutableStateOf<String?>(null) }

    fun fillFromHealth() {
        scope.launch {
            try {
                val r = HealthSource.read(ctx)
                r.sleepHours?.let { sleep = Calc.kg(it) }
                r.restingHr?.let { hr = it.toString() }
                hcMessage = if (r.sleepHours == null && r.restingHr == null)
                    "Health Connect has no sleep or heart rate saved in the last day or two."
                else "Filled in from Health Connect. Check the numbers and change them if they look wrong."
            } catch (e: Exception) {
                hcMessage = "Could not read Health Connect."
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthSource.PERMISSIONS)) fillFromHealth() else hcMessage = "Permission was not given."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily check-in") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = {
                    val problem = HealthSource.problem(ctx)
                    if (problem != null) hcMessage = problem
                    else scope.launch {
                        try {
                            if (HealthSource.hasPermissions(ctx)) fillFromHealth() else launcher.launch(HealthSource.PERMISSIONS)
                        } catch (e: Exception) {
                            hcMessage = "Could not read Health Connect."
                        }
                    }
                }) { Text("Fill from Health Connect") }
                hcMessage?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                Gap(8)
                Field("Hours slept last night", sleep, { sleep = it }, number = true, suffix = "h")
                Rating("Sleep quality", "poor", "great", quality) { quality = it }
                Rating("Muscle soreness", "none", "very sore", sore) { sore = it }
                Rating("Stress", "low", "high", stress) { stress = it }
                Rating("Energy", "flat", "great", energy) { energy = it }
                Gap(10)
                Field("Resting heart rate (optional)", hr, { hr = it }, number = true, suffix = "bpm")
            }
        },
        confirmButton = {
            TextButton(
                enabled = hours != null && hours in 0.0..16.0,
                onClick = {
                    onSave(
                        CheckInEntity(
                            dateEpochDay = today, sleepHours = hours ?: 0.0, sleepQuality = quality,
                            soreness = sore, stress = stress, energy = energy,
                            restingHr = hr.toDoubleOrNull()?.toInt()?.takeIf { it in 30..120 } ?: 0
                        )
                    )
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
