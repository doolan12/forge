package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doolan.forge.data.ProfileData

private val STEP_TITLES = listOf(
    "About you", "Experience", "Strength levels", "Your week", "Goals", "Your equipment"
)
private val STEP_SUB = listOf(
    "Just the basics so your targets make sense.",
    "Helps set the right starting point.",
    "Optional, but helps with weights later.",
    "When can you realistically train?",
    "What are you working towards?",
    "Only exercises you can actually do will be suggested."
)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var p by remember { mutableStateOf(ProfileData()) }
    var step by remember { mutableIntStateOf(0) }
    val equipment by vm.equipment.collectAsState()
    val last = STEP_TITLES.lastIndex

    val canContinue = when (step) {
        3 -> p.trainingDays.isNotEmpty()
        else -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Text("Step ${step + 1} of ${STEP_TITLES.size}", color = Muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        ProgressBar((step + 1) / STEP_TITLES.size.toFloat())
        Gap(16)
        ScreenTitle(STEP_TITLES[step], STEP_SUB[step])

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                0 -> BasicsSection(p) { p = it }
                1 -> ExperienceSection(p) { p = it }
                2 -> StrengthSection(p) { p = it }
                3 -> {
                    ScheduleSection(p) { p = it }
                    if (p.trainingDays.isEmpty()) {
                        Text("Pick at least one day.", color = Warn, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                4 -> GoalsSection(p) { p = it }
                else -> EquipmentSection(
                    equipment = equipment,
                    onToggle = { n, a -> vm.setEquipment(n, a) },
                    onAddCustom = { vm.addCustomEquipment(it) },
                    onDeleteCustom = { vm.deleteCustomEquipment(it) }
                )
            }
            Gap(24)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(
                onClick = {
                    if (step < last) step += 1 else vm.saveProfile(p.copy(onboarded = true))
                },
                enabled = canContinue,
                modifier = Modifier.weight(1f)
            ) { Text(if (step < last) "Next" else "Finish") }
        }
    }
}
