package com.doolan.forge.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doolan.forge.data.ReminderSettings
import com.doolan.forge.data.Reminders
import kotlinx.coroutines.launch

@Composable
fun RemindersSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.repo.store.reminders.collectAsState(initial = ReminderSettings())
    var trainTime by remember(saved.trainTime) { mutableStateOf(saved.trainTime) }
    var checkTime by remember(saved.checkTime) { mutableStateOf(saved.checkTime) }
    var denied by remember { mutableStateOf(false) }

    fun save(s: ReminderSettings) {
        scope.launch {
            vm.repo.store.saveReminders(s)
            Reminders.ensureChannel(ctx)
            Reminders.schedule(ctx, s, false)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
    }

    fun needPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) {
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val trainValid = Reminders.parseTime(trainTime) != null
    val checkValid = Reminders.parseTime(checkTime) != null

    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Training reminder", fontWeight = FontWeight.SemiBold)
                Text("Only on days you have a session planned", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = saved.trainOn, onCheckedChange = { on ->
                if (on) needPermission()
                save(saved.copy(trainOn = on, trainTime = if (trainValid) trainTime else saved.trainTime))
            })
        }
        Gap(6)
        Field("Time (24 hour, like 06:00)", trainTime, {
            trainTime = it
            if (Reminders.parseTime(it) != null) save(saved.copy(trainTime = Reminders.parseTime(it).toString()))
        })
        if (!trainValid) Text("Use hours and minutes like 06:30.", color = Warn, fontSize = 12.sp)

        Gap(14)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check-in reminder", fontWeight = FontWeight.SemiBold)
                Text("Only if you have not checked in yet today", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = saved.checkOn, onCheckedChange = { on ->
                if (on) needPermission()
                save(saved.copy(checkOn = on, checkTime = if (checkValid) checkTime else saved.checkTime))
            })
        }
        Gap(6)
        Field("Time (24 hour, like 07:00)", checkTime, {
            checkTime = it
            if (Reminders.parseTime(it) != null) save(saved.copy(checkTime = Reminders.parseTime(it).toString()))
        })
        if (!checkValid) Text("Use hours and minutes like 07:00.", color = Warn, fontSize = 12.sp)

        if (denied) Text("Notifications are blocked for Forge. Turn them on in your phone's app settings.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Android may deliver reminders a few minutes late to save battery. Reminders work offline.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
