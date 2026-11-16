package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.Calc
import com.doolan.forge.data.EquipmentEntity
import com.doolan.forge.data.ProfileData

val GOALS = listOf("HYROX", "Hypertrophy", "Strength", "Marathon & Running")

@Composable
fun BasicsSection(p: ProfileData, onChange: (ProfileData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("First name", p.name, { onChange(p.copy(name = it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Age", p.age, { onChange(p.copy(age = it)) }, Modifier.weight(1f), number = true)
            Field("Height", p.heightCm, { onChange(p.copy(heightCm = it)) }, Modifier.weight(1f), number = true, suffix = "cm")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Weight", p.weightKg, { onChange(p.copy(weightKg = it)) }, Modifier.weight(1f), number = true, suffix = "kg")
            Field("Body fat (optional)", p.bodyFat, { onChange(p.copy(bodyFat = it)) }, Modifier.weight(1f), number = true, suffix = "%")
        }
        SectionTitle("Sex (optional)")
        ChipChoice(listOf("Male", "Female", "Prefer not to say"), p.sex) {
            onChange(p.copy(sex = if (p.sex == it) "" else it))
        }
    }
}

@Composable
fun ExperienceSection(p: ProfileData, onChange: (ProfileData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Training experience")
        ChipChoice(listOf("Beginner", "Intermediate", "Advanced"), p.experience) { onChange(p.copy(experience = it)) }
        SectionTitle("Current fitness level")
        ChipChoice(listOf("Low", "Average", "High"), p.fitnessLevel) { onChange(p.copy(fitnessLevel = it)) }
        SectionTitle("Running experience")
        ChipChoice(listOf("None", "Beginner", "Regular runner", "Competitive"), p.runningExp) {
            onChange(p.copy(runningExp = it))
        }
        SectionTitle("Recent race times (optional, e.g. 24:30 or 3:45:00)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("5K", p.pb5k, { onChange(p.copy(pb5k = it)) }, Modifier.weight(1f))
            Field("10K", p.pb10k, { onChange(p.copy(pb10k = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Half marathon", p.pbHalf, { onChange(p.copy(pbHalf = it)) }, Modifier.weight(1f))
            Field("Marathon", p.pbMarathon, { onChange(p.copy(pbMarathon = it)) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun StrengthSection(p: ProfileData, onChange: (ProfileData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your best lifts or a rough estimate of a one-rep max. Leave blank if you don't know.", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Squat", p.squat1rm, { onChange(p.copy(squat1rm = it)) }, Modifier.weight(1f), number = true, suffix = "kg")
            Field("Bench", p.bench1rm, { onChange(p.copy(bench1rm = it)) }, Modifier.weight(1f), number = true, suffix = "kg")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Deadlift", p.deadlift1rm, { onChange(p.copy(deadlift1rm = it)) }, Modifier.weight(1f), number = true, suffix = "kg")
            Field("Overhead press", p.ohp1rm, { onChange(p.copy(ohp1rm = it)) }, Modifier.weight(1f), number = true, suffix = "kg")
        }
    }
}

@Composable
fun ScheduleSection(p: ProfileData, onChange: (ProfileData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Days you can train")
        ChipMulti((1..7).map { Calc.weekdayShort(it) }, p.trainingDays.map { Calc.weekdayShort(it) }.toSet()) { label ->
            val n = (1..7).first { Calc.weekdayShort(it) == label }
            onChange(p.copy(trainingDays = if (n in p.trainingDays) p.trainingDays - n else p.trainingDays + n))
        }
        SectionTitle("Preferred time")
        ChipChoice(listOf("Early morning", "Morning", "Lunch", "Afternoon", "Evening"), p.preferredTime) {
            onChange(p.copy(preferredTime = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Average session", p.sessionMin, { onChange(p.copy(sessionMin = it)) }, Modifier.weight(1f), number = true, suffix = "min")
            Field("Sleep per night", p.sleepHours, { onChange(p.copy(sleepHours = it)) }, Modifier.weight(1f), number = true, suffix = "h")
        }
    }
}

@Composable
fun GoalsSection(p: ProfileData, onChange: (ProfileData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Primary goal")
        ChipChoice(GOALS, p.primaryGoal) {
            onChange(p.copy(primaryGoal = it, secondaryGoals = p.secondaryGoals - it))
        }
        SectionTitle("Also training for (optional)")
        ChipMulti(GOALS.filter { it != p.primaryGoal }, p.secondaryGoals) { g ->
            onChange(p.copy(secondaryGoals = if (g in p.secondaryGoals) p.secondaryGoals - g else p.secondaryGoals + g))
        }
        SectionTitle("Injuries or limitations (optional)")
        Field("Anything to avoid or work around", p.injuries, { onChange(p.copy(injuries = it)) }, singleLine = false)
    }
}

@Composable
fun EquipmentSection(
    equipment: List<EquipmentEntity>,
    onToggle: (String, Boolean) -> Unit,
    onAddCustom: (String) -> Unit,
    onDeleteCustom: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Switch on what you can use. Workouts will only suggest exercises that match. Bodyweight exercises are always allowed.",
            color = Muted, fontSize = 13.sp
        )
        equipment.forEach { e ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(e.name + if (e.isCustom) "  (custom)" else "", modifier = Modifier.weight(1f))
                if (e.isCustom) {
                    IconButton(onClick = { onDeleteCustom(e.name) }) { Icon(Icons.Default.Close, "Remove ${e.name}") }
                }
                Switch(checked = e.available, onCheckedChange = { onToggle(e.name, it) })
            }
        }
        SectionTitle("Add your own equipment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Equipment name", newName, { newName = it }, Modifier.weight(1f))
            OutlinedButton(onClick = {
                onAddCustom(newName)
                newName = ""
            }, enabled = newName.isNotBlank()) { Text("Add") }
        }
    }
}
