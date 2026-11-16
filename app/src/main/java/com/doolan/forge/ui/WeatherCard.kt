package com.doolan.forge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doolan.forge.data.Calc
import com.doolan.forge.data.DayWeather
import com.doolan.forge.data.Place
import com.doolan.forge.data.Repository
import com.doolan.forge.data.SessionEntity
import com.doolan.forge.data.Weather
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeatherViewModel(private val repo: Repository) : ViewModel() {
    val place = repo.store.place.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var days by mutableStateOf<List<DayWeather>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<Place>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    fun load(p: Place) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                days = Weather.forecast(p)
            } catch (e: Exception) {
                error = "Weather unavailable right now (needs internet)."
            } finally {
                loading = false
            }
        }
    }

    fun search(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            searching = true
            searchError = null
            try {
                results = Weather.search(q)
                if (results.isEmpty()) searchError = "No places found."
            } catch (e: Exception) {
                searchError = "Search failed (needs internet)."
            } finally {
                searching = false
            }
        }
    }

    fun choose(p: Place) {
        viewModelScope.launch {
            repo.store.savePlace(p)
            results = emptyList()
        }
    }

    fun clear() {
        viewModelScope.launch { repo.store.savePlace(null); days = emptyList() }
    }
}

@Composable
fun WeatherCard(vm: AppViewModel, today: Long, weekSessions: List<SessionEntity>) {
    val wvm: WeatherViewModel = viewModel(
        key = "weather",
        factory = viewModelFactory { initializer { WeatherViewModel(vm.repo) } }
    )
    val place by wvm.place.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val p = place

    LaunchedEffect(p) { if (p != null) wvm.load(p) }

    val runs = weekSessions.filter { it.discipline == "RUN" && it.status == "PLANNED" && it.dateEpochDay >= today }

    ForgeCard {
        Text("Weather for your runs", fontWeight = FontWeight.SemiBold)
        if (p == null) {
            Text(
                "Pick your town and the app shows the forecast for your planned runs. Only the place you choose is used. It never reads your GPS.",
                color = Muted, fontSize = 13.sp
            )
            Gap(8)
            Button(onClick = { showPicker = true }) { Text("Set location") }
        } else {
            Text(p.name, color = Muted, fontSize = 12.sp)
            val todayW = wvm.days.firstOrNull { it.epochDay == today }
            if (wvm.loading && wvm.days.isEmpty()) Text("Loading...", color = Muted, fontSize = 13.sp)
            wvm.error?.let { Text(it, color = Warn, fontSize = 13.sp) }
            if (runs.isEmpty() && todayW != null) {
                Text("Today: ${Weather.summary(todayW)}", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            runs.forEach { s ->
                val w = wvm.days.firstOrNull { it.epochDay == s.dateEpochDay }
                if (w != null) {
                    Text(
                        "${Calc.dayName(s.dateEpochDay)}: ${s.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(Weather.summary(w), color = Muted, fontSize = 12.sp)
                    Weather.advice(w)?.let { Text(it, color = Warn, fontSize = 13.sp) }
                }
            }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { p.let { wvm.load(it) } }) { Text("Refresh") }
                OutlinedButton(onClick = { showPicker = true }) { Text("Change") }
            }
            Text("Forecast from Open-Meteo.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (showPicker) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPicker = false; wvm.results = emptyList() },
            title = { Text("Your location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field("Town or suburb, for example Sydney", q, { q = it })
                    Button(onClick = { wvm.search(q) }, enabled = q.isNotBlank() && !wvm.searching) {
                        Text(if (wvm.searching) "Searching..." else "Search")
                    }
                    wvm.searchError?.let { Text(it, color = Warn, fontSize = 13.sp) }
                    wvm.results.forEach { r ->
                        TextButton(onClick = { wvm.choose(r); showPicker = false }) { Text(r.name) }
                    }
                }
            },
            confirmButton = {
                if (p != null) TextButton(onClick = { wvm.clear(); showPicker = false }) { Text("Remove location", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false; wvm.results = emptyList() }) { Text("Close") } }
        )
    }
}
