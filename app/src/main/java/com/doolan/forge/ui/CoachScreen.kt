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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doolan.forge.data.ChatMsg
import com.doolan.forge.data.Calc
import com.doolan.forge.data.Coach
import com.doolan.forge.data.CoachAction
import com.doolan.forge.data.DEFAULT_MODEL
import com.doolan.forge.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Turn(val msg: ChatMsg, val actions: List<CoachAction> = emptyList())

class CoachViewModel(private val repo: Repository) : ViewModel() {
    val turns = mutableStateListOf<Turn>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var lastFailed by mutableStateOf<String?>(null)
    var preview by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    val apiKey = repo.store.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val model = repo.store.modelName.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MODEL)

    fun saveKey(key: String, model: String) {
        viewModelScope.launch { repo.store.saveCoach(key, model) }
    }

    fun showPreview() {
        viewModelScope.launch { preview = Coach.buildContext(repo) }
    }

    fun ask(text: String) {
        if (loading || text.isBlank()) return
        error = null
        lastFailed = null
        notice = null
        turns.add(Turn(ChatMsg("user", text.trim())))
        loading = true
        viewModelScope.launch {
            try {
                val ctx = Coach.buildContext(repo)
                val history = turns.map { it.msg }.takeLast(10).dropWhile { it.role != "user" }
                val reply = Coach.send(apiKey.value, model.value, ctx, history)
                val today = Calc.today()
                val checked = reply.actions.mapNotNull { a ->
                    val s = repo.db.sessions().byId(a.sessionId) ?: return@mapNotNull null
                    if (s.status != "PLANNED" || s.dateEpochDay < today) return@mapNotNull null
                    if (a.kind == "move_session" && (a.date == null || a.date < today || a.date > today + 14)) return@mapNotNull null
                    Coach.describe(a, s)
                }
                turns.add(Turn(ChatMsg("assistant", reply.text.ifBlank { "(no text)" }), checked))
            } catch (e: java.io.IOException) {
                fail(text, "No connection. The coach needs internet; the rest of the app works offline.")
            } catch (e: Exception) {
                fail(text, e.message ?: "Something went wrong.")
            } finally {
                loading = false
            }
        }
    }

    private fun fail(text: String, message: String) {
        if (turns.isNotEmpty() && turns.last().msg.role == "user") turns.removeAt(turns.lastIndex)
        error = message
        lastFailed = text
    }

    fun apply(turnIndex: Int, action: CoachAction) {
        viewModelScope.launch {
            val s = repo.db.sessions().byId(action.sessionId)
            if (s == null || s.status != "PLANNED") {
                notice = "That session has changed, so nothing was done."
            } else {
                when (action.kind) {
                    "ease_session" -> repo.easeSession(s.id)
                    "skip_session" -> repo.updateSession(s.copy(status = "SKIPPED"))
                    else -> action.date?.let { repo.moveSession(s.id, it) }
                }
                notice = "Done: ${action.label}"
            }
            remove(turnIndex, action)
        }
    }

    fun dismiss(turnIndex: Int, action: CoachAction) = remove(turnIndex, action)

    private fun remove(turnIndex: Int, action: CoachAction) {
        if (turnIndex in turns.indices) {
            val t = turns[turnIndex]
            turns[turnIndex] = t.copy(actions = t.actions.filter { it != action })
        }
    }
}

@Composable
fun CoachScreen(cvm: CoachViewModel, onBack: () -> Unit) {
    val key by cvm.apiKey.collectAsState()
    val model by cvm.model.collectAsState()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(cvm.turns.size, cvm.loading) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) { ScreenTitle("AI coach", "Answers from your logged data only") }
            if (key.isNotBlank()) TextButton(onClick = { editing = !editing }) { Text("Settings") }
        }

        if (key.isBlank() || editing) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                KeySetup(key, model, hasKey = key.isNotBlank(), onSave = { k, m -> cvm.saveKey(k, m); editing = false })
                Gap(8)
                PrivacyCard { cvm.showPreview() }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                if (cvm.turns.isEmpty()) {
                    ForgeCard {
                        Text("Ask about your training", fontWeight = FontWeight.SemiBold)
                        Text(
                            "The coach only sees what you have logged in Forge. It explains and suggests. It cannot change anything unless you tap Apply on a proposal.",
                            color = Muted, fontSize = 13.sp
                        )
                    }
                    Gap(8)
                    ChipMulti(
                        listOf("Review my last week", "Am I recovering well?", "Why is my next session set this way?", "What should I focus on this week?"),
                        emptySet()
                    ) { cvm.ask(it) }
                    Gap(8)
                    PrivacyCard { cvm.showPreview() }
                }
                cvm.turns.forEachIndexed { idx, t ->
                    ForgeCard(Modifier.padding(top = 8.dp)) {
                        Text(if (t.msg.role == "user") "You" else "Coach", color = if (t.msg.role == "user") Muted else Accent, fontSize = 12.sp)
                        Text(t.msg.text, fontSize = 14.sp)
                        t.actions.forEach { a ->
                            Gap(8)
                            ForgeCard {
                                Text("Proposed change", color = Warn, fontSize = 12.sp)
                                Text(a.label, fontWeight = FontWeight.SemiBold)
                                if (a.why.isNotBlank()) Text(a.why, color = Muted, fontSize = 13.sp)
                                Gap(6)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { cvm.apply(idx, a) }) { Text("Apply") }
                                    OutlinedButton(onClick = { cvm.dismiss(idx, a) }) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                }
                if (cvm.loading) Text("Thinking...", color = Muted, modifier = Modifier.padding(top = 10.dp))
                cvm.notice?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                cvm.error?.let { msg ->
                    Text(msg, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    cvm.lastFailed?.let { f -> OutlinedButton(onClick = { cvm.ask(f) }) { Text("Try again") } }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text("Ask the coach") }, modifier = Modifier.weight(1f), maxLines = 3
                )
                Button(enabled = input.isNotBlank() && !cvm.loading, onClick = { cvm.ask(input); input = "" }) { Text("Send") }
            }
        }
    }

    cvm.preview?.let { text ->
        AlertDialog(
            onDismissRequest = { cvm.preview = null },
            title = { Text("What gets sent") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, fontSize = 12.sp) } },
            confirmButton = { TextButton(onClick = { cvm.preview = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun KeySetup(currentKey: String, currentModel: String, hasKey: Boolean, onSave: (String, String) -> Unit) {
    var k by remember { mutableStateOf(currentKey) }
    var m by remember { mutableStateOf(currentModel) }
    ForgeCard {
        Text(if (hasKey) "Coach settings" else "Set up your coach", fontWeight = FontWeight.SemiBold)
        Text(
            "The coach uses your own Anthropic API key, so you pay Anthropic directly for what you use. Create a key at console.anthropic.com, then paste it here. It is stored only on this phone and is not included in backups.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
        )
        OutlinedTextField(
            value = k, onValueChange = { k = it }, label = { Text("API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Gap(8)
        Field("Model", m, { m = it })
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = k.isNotBlank(), onClick = { onSave(k, m) }) { Text("Save") }
            if (hasKey) OutlinedButton(onClick = { onSave("", m) }) { Text("Remove key") }
        }
    }
}

@Composable
private fun PrivacyCard(onPreview: () -> Unit) {
    ForgeCard {
        Text("What is shared", fontWeight = FontWeight.SemiBold)
        Text(
            "Only when you send a message, a summary of your profile, recent sessions, lifting numbers, check-ins, body weight and nutrition is sent to Anthropic using your key. Nothing is sent in the background.",
            color = Muted, fontSize = 13.sp
        )
        OutlinedButton(onClick = onPreview, modifier = Modifier.padding(top = 6.dp)) { Text("See exactly what is sent") }
    }
}
