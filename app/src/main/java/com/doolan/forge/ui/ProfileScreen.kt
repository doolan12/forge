package com.doolan.forge.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.doolan.forge.data.Calc
import com.doolan.forge.data.ProfileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val saved by vm.profile.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val bodyweight by vm.bodyweight.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf<ProfileData?>(null) }
    LaunchedEffect(saved) {
        if (draft == null && saved != null) draft = saved
    }
    var weightInput by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = vm.exportJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    }
                    toast("Backup saved.")
                } catch (e: Exception) {
                    toast("Backup failed: ${e.message}")
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    val d = draft
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle("Profile", "Your details, equipment, backups")

        if (d == null) {
            Text("Loading...", color = Muted)
        } else {
            SectionTitle("About you")
            BasicsSection(d) { draft = it }
            SectionTitle("Experience")
            ExperienceSection(d) { draft = it }
            SectionTitle("Strength levels")
            StrengthSection(d) { draft = it }
            SectionTitle("Your week")
            ScheduleSection(d) { draft = it }
            SectionTitle("Goals")
            GoalsSection(d) { draft = it }
            Gap(12)
            Button(
                onClick = {
                    vm.saveProfile(d)
                    toast("Profile saved.")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save profile") }
        }

        SectionTitle("Equipment")
        EquipmentSection(
            equipment = equipment,
            onToggle = { n, a -> vm.setEquipment(n, a) },
            onAddCustom = { vm.addCustomEquipment(it) },
            onDeleteCustom = { vm.deleteCustomEquipment(it) }
        )

        SectionTitle("Body weight log")
        ForgeCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Today's weight", weightInput, { weightInput = it }, Modifier.weight(1f), number = true, suffix = "kg")
                OutlinedButton(
                    enabled = weightInput.toDoubleOrNull() != null,
                    onClick = {
                        weightInput.toDoubleOrNull()?.let { vm.addBodyweight(it) }
                        weightInput = ""
                    }
                ) { Text("Log") }
            }
            bodyweight.takeLast(8).reversed().forEach { b ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${Calc.dateLabel(b.dateEpochDay)}   ${Calc.kg(b.kg)} kg", fontSize = 14.sp)
                    TextButton(onClick = { vm.deleteBodyweight(b) }) { Text("Delete", color = Danger) }
                }
            }
        }

        SectionTitle("Backup")
        ForgeCard {
            Text(
                "Export saves all your profile, programmes, workouts and logs to a file you choose. Import restores a backup and replaces what is on this phone now.",
                color = Muted, fontSize = 13.sp
            )
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("forge-backup-${java.time.LocalDate.now()}.json") }) { Text("Export backup") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import backup") }
            }
        }

        SectionTitle("About")
        Text("Forge 0.1.0 (Stage 2). Everything is stored on this phone and works offline.", color = Muted, fontSize = 13.sp)
        Gap(32)
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace everything?",
            text = "Importing a backup will replace all data currently in Forge with the contents of that file.",
            confirmLabel = "Import",
            onConfirm = {
                scope.launch {
                    try {
                        val text = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        } ?: throw IllegalStateException("Could not read the file.")
                        vm.importJson(text)
                        draft = vm.repo.currentProfile()
                        toast("Backup restored.")
                    } catch (e: Exception) {
                        toast("Import failed: ${e.message}")
                    }
                }
            },
            onDismiss = { pendingImport = null }
        )
    }
}
