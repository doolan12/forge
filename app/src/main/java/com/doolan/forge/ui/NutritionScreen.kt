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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.doolan.forge.data.FoodEntryEntity
import com.doolan.forge.data.MeasurementEntity
import com.doolan.forge.data.Nutrition

@Composable
fun NutritionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val foods by vm.foods.collectAsState()
    val measurements by vm.measurements.collectAsState()
    val today = remember { Calc.today() }
    var day by remember { mutableStateOf(today) }
    var showAdd by remember { mutableStateOf(false) }
    var measure by remember { mutableStateOf("Waist") }
    var measureValue by remember { mutableStateOf("") }
    var deleteFood by remember { mutableStateOf<FoodEntryEntity?>(null) }

    val p = profile
    val targets = p?.let { Nutrition.targets(it) }
    val dayFoods = foods.filter { it.dateEpochDay == day }
    val kcal = dayFoods.sumOf { it.kcal }
    val protein = dayFoods.sumOf { it.proteinG }
    val carbs = dayFoods.sumOf { it.carbsG }
    val fat = dayFoods.sumOf { it.fatG }
    val recent = foods.sortedByDescending { it.dateEpochDay * 100000 + it.id }
        .distinctBy { it.name.lowercase() }.take(8)

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
            ScreenTitle("Nutrition", "Targets, food log and body measurements")
        }

        SectionTitle("Your goal")
        ChipChoice(Nutrition.GOALS, p?.nutritionGoal ?: "Maintain") { g ->
            if (p != null) vm.saveProfile(p.copy(nutritionGoal = g))
        }
        if (targets == null) {
            Text(
                "Add your age, sex, height and weight in Profile to get daily targets.",
                color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Gap(8)
            ForgeCard {
                Text("Daily targets", fontWeight = FontWeight.SemiBold)
                Gap(6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${targets.kcal}", "kcal")
                    Stat("${targets.proteinG} g", "protein")
                    Stat("${targets.carbsG} g", "carbs")
                    Stat("${targets.fatG} g", "fat")
                }
                Gap(6)
                Text("Estimated maintenance ${targets.tdee} kcal, resting ${targets.bmr} kcal.", color = Muted, fontSize = 12.sp)
                targets.notes.forEach { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        SectionTitle("Food log")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { day -= 1 }) { Text("< Previous") }
            Text(if (day == today) "Today" else "${Calc.dayName(day)} ${Calc.dateLabel(day)}", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { if (day < today) day += 1 }, enabled = day < today) { Text("Next >") }
        }
        ForgeCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("$kcal", if (targets != null) "of ${targets.kcal} kcal" else "kcal")
                Stat("${protein.toInt()} g", if (targets != null) "of ${targets.proteinG} g protein" else "protein")
            }
            if (targets != null) {
                Gap(8)
                ProgressBar((kcal / targets.kcal.toFloat()).coerceIn(0f, 1f))
                Gap(4)
                ProgressBar((protein / targets.proteinG).toFloat().coerceIn(0f, 1f), color = Blue)
            }
            Gap(6)
            Text("Carbs ${carbs.toInt()} g | Fat ${fat.toInt()} g", color = Muted, fontSize = 13.sp)
        }
        Gap(8)
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Add food") }
        if (recent.isNotEmpty()) {
            Text("Add again:", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            ChipMulti(recent.map { it.name }, emptySet()) { name ->
                recent.firstOrNull { it.name == name }?.let { vm.addFood(it.copy(id = 0, dateEpochDay = day)) }
            }
        }
        dayFoods.forEach { f ->
            ForgeCard(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold)
                        Text("${f.kcal} kcal | P ${f.proteinG.toInt()} C ${f.carbsG.toInt()} F ${f.fatG.toInt()}", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { deleteFood = f }) { Text("Remove", color = Danger) }
                }
            }
        }
        if (dayFoods.isEmpty()) {
            Text("Nothing logged for this day. You enter the numbers yourself, so there is no guessing from a food database.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        SectionTitle("Body measurements")
        ChipChoice(Nutrition.MEASURES, measure) { measure = it; measureValue = "" }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("$measure (${Nutrition.unit(measure)})", measureValue, { measureValue = it }, Modifier.weight(1f), number = true)
            Button(
                enabled = measureValue.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    vm.addMeasurement(MeasurementEntity(dateEpochDay = today, type = measure, value = measureValue.toDouble()))
                    measureValue = ""
                }
            ) { Text("Log") }
        }
        val series = measurements.filter { it.type == measure }
        ForgeCard(Modifier.padding(top = 8.dp)) {
            if (series.isEmpty()) {
                Text("Nothing logged for $measure yet.", color = Muted, fontSize = 13.sp)
            } else {
                val first = series.first()
                val last = series.last()
                Text("${Calc.kg(last.value)} ${Nutrition.unit(measure)} on ${Calc.dateLabel(last.dateEpochDay)}", fontWeight = FontWeight.SemiBold)
                if (series.size >= 2) {
                    Text(
                        "${if (last.value - first.value >= 0) "+" else ""}${Calc.kg(Math.round((last.value - first.value) * 10) / 10.0)} ${Nutrition.unit(measure)} since ${Calc.dateLabel(first.dateEpochDay)}",
                        color = Muted, fontSize = 12.sp
                    )
                    LineChart(series.map { it.value }, Calc.dateLabel(first.dateEpochDay), Calc.dateLabel(last.dateEpochDay), Nutrition.unit(measure))
                }
                Gap(4)
                series.takeLast(3).reversed().forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Calc.dateLabel(m.dateEpochDay)}: ${Calc.kg(m.value)} ${Nutrition.unit(measure)}", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { vm.deleteMeasurement(m.id) }) { Text("Delete", color = Danger) }
                    }
                }
            }
        }
        if (p != null) {
            fun latest(t: String) = measurements.lastOrNull { it.type == t }?.value
            val est = Nutrition.navyBodyFat(p, latest("Waist"), latest("Neck"), latest("Hip"))
            Gap(8)
            ForgeCard {
                Text("Body fat estimate", fontWeight = FontWeight.SemiBold)
                if (est != null) {
                    Text("About ${String.format("%.1f", est)}%", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        vm.addMeasurement(MeasurementEntity(dateEpochDay = today, type = "Body fat %", value = Math.round(est * 10) / 10.0))
                    }) { Text("Save as a body fat entry") }
                } else {
                    Text(
                        "Log your waist and neck${if (p.sex == "Female") " and hip" else ""} (and set your height and sex in Profile) for a tape-measure estimate.",
                        color = Muted, fontSize = 13.sp
                    )
                }
                Text("This uses the US Navy formula and can be off by several percent. Use it for trends, not as an exact figure.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Gap(32)
    }

    if (showAdd) {
        AddFoodDialog(day = day, onSave = { vm.addFood(it) }, onDismiss = { showAdd = false })
    }
    deleteFood?.let { f ->
        ConfirmDialog(
            title = "Remove food?", text = "Remove ${f.name} from this day?", confirmLabel = "Remove",
            onConfirm = { vm.deleteFood(f.id) }, onDismiss = { deleteFood = null }
        )
    }
}

@Composable
private fun AddFoodDialog(day: Long, onSave: (FoodEntryEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val p = protein.toDoubleOrNull() ?: 0.0
    val c = carbs.toDoubleOrNull() ?: 0.0
    val f = fat.toDoubleOrNull() ?: 0.0
    val k = kcal.toDoubleOrNull()?.toInt() ?: (p * 4 + c * 4 + f * 9).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add food") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name (for example Chicken and rice)", name, { name = it })
                Field("Calories", kcal, { kcal = it }, number = true, suffix = "kcal")
                Field("Protein", protein, { protein = it }, number = true, suffix = "g")
                Field("Carbs", carbs, { carbs = it }, number = true, suffix = "g")
                Field("Fat", fat, { fat = it }, number = true, suffix = "g")
                Text("Leave calories blank to work it out from the macros.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && k > 0,
                onClick = {
                    onSave(FoodEntryEntity(dateEpochDay = day, name = name.trim(), kcal = k, proteinG = p, carbsG = c, fatG = f))
                    onDismiss()
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
