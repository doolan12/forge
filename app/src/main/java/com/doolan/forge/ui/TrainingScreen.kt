package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.doolan.forge.data.ExerciseEntity
import com.doolan.forge.data.SessionEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainingScreen(vm: AppViewModel, openWorkout: (Long) -> Unit, openGenerator: () -> Unit) {
    val exercises by vm.exercises.collectAsState()
    val available by vm.availableEquipment.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val programmes by vm.programmes.collectAsState()
    var deleteProgramme by remember { mutableStateOf<com.doolan.forge.data.ProgrammeEntity?>(null) }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var onlyMine by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<ExerciseEntity?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExerciseEntity?>(null) }

    val shown = exercises.filter { e ->
        (query.isBlank() || e.name.contains(query, ignoreCase = true)) &&
            (filter == "All" || (filter == "Favourites" && e.isFavourite) || (filter == "Custom" && e.isCustom) || e.category == filter) &&
            (!onlyMine || e.usableWith(available))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle("Training", "Start a session and browse the exercise library")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                vm.addSession(SessionEntity(dateEpochDay = Calc.today(), title = "Workout", discipline = "GYM")) { id ->
                    openWorkout(id)
                }
            }) { Text("Start empty workout") }
            OutlinedButton(onClick = { showPlan = true }) { Text("Plan a session") }
        }

        Gap(8)
        Button(onClick = openGenerator, modifier = Modifier.fillMaxWidth()) { Text("Build a programme") }
        programmes.forEach { g ->
            ForgeCard(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold)
                        Text("${g.weeks} weeks from ${Calc.dateLabel(g.startDay)}", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { deleteProgramme = g }) { Text("Remove", color = Danger) }
                }
            }
        }

        SectionTitle("Exercise library (${shown.size})")
        Field("Search exercises", query, { query = it })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Switch(checked = onlyMine, onCheckedChange = { onlyMine = it })
            Text("  Only my equipment", fontSize = 14.sp)
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Favourites", "Custom") + CATEGORIES) { c ->
                androidx.compose.material3.FilterChip(
                    selected = filter == c, onClick = { filter = c }, label = { Text(c) }
                )
            }
        }
        Gap(6)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TextButton(onClick = { showCustom = true }) { Text("+ Add custom exercise") }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (onlyMine) "No exercises match your equipment and filters. Turn off 'Only my equipment' or update your equipment in Profile."
                        else "No exercises match.",
                        color = Muted, fontSize = 13.sp
                    )
                }
            }
            items(shown, key = { it.id }) { e ->
                ForgeCard(onClick = { detail = e }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${e.primaryMuscle} | ${e.equipmentList.joinToString(", ")}",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        if (e.isCustom) {
                            TextButton(onClick = { deleteTarget = e }) { Text("Delete") }
                        }
                        IconButton(onClick = { vm.setFavourite(e.id, !e.isFavourite) }) {
                            Icon(
                                if (e.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                                if (e.isFavourite) "Remove favourite" else "Favourite",
                                tint = if (e.isFavourite) Warn else Muted
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { ex ->
        ExerciseDetailDialog(
            ex = ex,
            alternatives = exercises.filter { it.id != ex.id && it.primaryMuscle == ex.primaryMuscle && it.usableWith(available) },
            onDismiss = { detail = null }
        )
    }
    if (showPlan) {
        PlanSessionDialog(initialDay = Calc.today(), onCreate = { vm.addSession(it) }, onDismiss = { showPlan = false })
    }
    if (showCustom) {
        CustomExerciseDialog(
            availableEquipment = equipment.map { it.name },
            onCreate = { vm.addCustomExercise(it) },
            onDismiss = { showCustom = false }
        )
    }
    deleteProgramme?.let { g ->
        ConfirmDialog(
            title = "Remove programme?",
            text = "Upcoming sessions from ${g.name} are removed from your calendar. Sessions you already completed stay.",
            confirmLabel = "Remove",
            onConfirm = { vm.deleteProgramme(g.id) },
            onDismiss = { deleteProgramme = null }
        )
    }
    deleteTarget?.let { e ->
        ConfirmDialog(
            title = "Delete custom exercise?",
            text = "${e.name} will be removed from the library. Past logs stay in your history.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteCustomExercise(e.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
