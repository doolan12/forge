package com.doolan.forge.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.Calc
import com.doolan.forge.data.Demo
import com.doolan.forge.data.DemoImage
import com.doolan.forge.data.ExerciseEntity
import com.doolan.forge.data.SessionEntity

val DISCIPLINES = listOf("GYM", "RUN", "HYROX", "OTHER")
val CATEGORIES = listOf(
    "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quadriceps", "Hamstrings", "Glutes",
    "Calves", "Core", "Compound", "HYROX", "Running drills", "Mobility"
)
val MUSCLES = listOf(
    "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Core",
    "Forearms", "Cardio"
)

fun disciplineLabel(d: String): String = when (d) {
    "GYM" -> "Gym"
    "RUN" -> "Run"
    "HYROX" -> "HYROX"
    "REST" -> "Rest day"
    else -> "Other"
}

/** Pick an exercise. Shows only exercises that can be done with the user's equipment unless switched off. */
@Composable
fun ExercisePickerDialog(
    title: String,
    exercises: List<ExerciseEntity>,
    available: Set<String>,
    sameMuscle: String? = null,
    excludeId: String? = null,
    onPick: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var onlyMine by remember { mutableStateOf(true) }
    var sameOnly by remember { mutableStateOf(sameMuscle != null) }

    val shown = exercises.filter { e ->
        e.id != excludeId &&
            (query.isBlank() || e.name.contains(query, ignoreCase = true)) &&
            (!onlyMine || e.usableWith(available)) &&
            (!sameOnly || sameMuscle == null || e.primaryMuscle == sameMuscle)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Field("Search", query, { query = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = onlyMine, onCheckedChange = { onlyMine = it })
                    Text("  Only my equipment", fontSize = 14.sp)
                }
                if (sameMuscle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = sameOnly, onCheckedChange = { sameOnly = it })
                        Text("  Same muscle ($sameMuscle)", fontSize = 14.sp)
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        "Nothing matches. Try turning off 'Only my equipment' or check your equipment list in Profile.",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
                    )
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.id }) { e ->
                        ClickableRow(onClick = { onPick(e); onDismiss() }) {
                            Column(Modifier.padding(vertical = 10.dp)) {
                                Text(e.name, fontWeight = FontWeight.SemiBold)
                                Text("${e.primaryMuscle} | ${e.equipmentList.joinToString(", ")}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ExerciseDetailDialog(
    ex: ExerciseEntity,
    alternatives: List<ExerciseEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var demo by remember { mutableStateOf<DemoImage?>(null) }
    var demoDone by remember { mutableStateOf(false) }
    LaunchedEffect(ex.name) {
        demo = Demo.find(ex.name)
        demoDone = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ex.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(ex.category)
                    Tag(ex.difficulty, Blue)
                }
                BodyText("Primary: ${ex.primaryMuscle}")
                if (ex.secondaryList.isNotEmpty()) BodyText("Secondary: ${ex.secondaryList.joinToString(", ")}", muted = true)
                BodyText("Equipment: ${ex.equipmentList.joinToString(", ")}", muted = true)
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(Demo.youtubeUrl(ex.name)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) { }
                }) { Text("Watch a video demo") }
                demo?.let { d ->
                    Image(
                        bitmap = d.bitmap.asImageBitmap(),
                        contentDescription = "Demo of ${d.matchedName}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    )
                    BodyText("Picture: ${d.matchedName} (wger.de, CC-BY-SA). Check it matches the exercise.", muted = true)
                }
                if (!demoDone) BodyText("Looking for a picture...", muted = true)
                if (ex.stepList.isNotEmpty()) {
                    SectionTitle("How to do it")
                    ex.stepList.forEachIndexed { i, s -> BodyText("${i + 1}. $s") }
                }
                if (ex.mistakeList.isNotEmpty()) {
                    SectionTitle("Common mistakes")
                    ex.mistakeList.forEach { BodyText("- $it") }
                }
                if (ex.tipList.isNotEmpty()) {
                    SectionTitle("Technique tips")
                    ex.tipList.forEach { BodyText("- $it") }
                }
                if (alternatives.isNotEmpty()) {
                    SectionTitle("Alternatives (your equipment)")
                    alternatives.take(6).forEach { BodyText("- ${it.name}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Create a session on a chosen day. */
@Composable
fun PlanSessionDialog(
    initialDay: Long,
    onCreate: (SessionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var discipline by remember { mutableStateOf("GYM") }
    var day by remember { mutableLongStateOf(initialDay) }
    var minutes by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan a session") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name (e.g. Upper body, Easy run)", title, { title = it })
                ChipChoice(DISCIPLINES.map { disciplineLabel(it) }, disciplineLabel(discipline)) { label ->
                    discipline = DISCIPLINES.first { disciplineLabel(it) == label }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { day -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Previous day") }
                    Text("${Calc.dayNameFull(day)} ${Calc.dateLabel(day)}", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { day += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Next day") }
                }
                Field("Planned duration", minutes, { minutes = it }, number = true, suffix = "min")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = title.ifBlank { disciplineLabel(discipline) }
                onCreate(
                    SessionEntity(
                        dateEpochDay = day, title = name, discipline = discipline,
                        durationMin = minutes.toDoubleOrNull()?.toInt() ?: 60
                    )
                )
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CustomExerciseDialog(
    availableEquipment: List<String>,
    onCreate: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Chest") }
    var muscle by remember { mutableStateOf("Chest") }
    var equipment by remember { mutableStateOf(setOf<String>()) }
    var steps by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Exercise name", name, { name = it })
                SectionTitle("Category")
                ChipChoice(CATEGORIES, category) { category = it }
                SectionTitle("Main muscle worked")
                ChipChoice(MUSCLES, muscle) { muscle = it }
                SectionTitle("Equipment needed (none = bodyweight)")
                ChipMulti(availableEquipment, equipment) { e ->
                    equipment = if (e in equipment) equipment - e else equipment + e
                }
                Field("How to do it (optional)", steps, { steps = it }, singleLine = false)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val id = "custom_" + System.currentTimeMillis()
                    onCreate(
                        ExerciseEntity(
                            id = id, name = name.trim(), category = category, primaryMuscle = muscle,
                            secondaryMuscles = "",
                            equipment = if (equipment.isEmpty()) "Bodyweight" else equipment.joinToString("|"),
                            difficulty = "Custom", instructions = steps.trim().replace("|", "/"),
                            mistakes = "", tips = "", isCustom = true
                        )
                    )
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
