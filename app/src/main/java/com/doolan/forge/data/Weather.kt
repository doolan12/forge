package com.doolan.forge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class Place(val name: String, val lat: Double, val lon: Double)

data class DayWeather(
    val epochDay: Long,
    val maxC: Double,
    val minC: Double,
    val rainPct: Int,
    val windKmh: Double,
    val uv: Double
)

/** Free weather from Open-Meteo (no account or key). Only the place the user picked is sent. */
object Weather {

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("Weather service error $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json"))
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() }.joinToString(", ")
            Place(label, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    suspend fun forecast(p: Place): List<DayWeather> = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,uv_index_max" +
            "&timezone=auto&forecast_days=7"
        val daily = JSONObject(get(url)).getJSONObject("daily")
        val times = daily.getJSONArray("time")
        (0 until times.length()).map { i ->
            DayWeather(
                epochDay = LocalDate.parse(times.getString(i)).toEpochDay(),
                maxC = daily.getJSONArray("temperature_2m_max").optDouble(i, Double.NaN),
                minC = daily.getJSONArray("temperature_2m_min").optDouble(i, Double.NaN),
                rainPct = daily.getJSONArray("precipitation_probability_max").optInt(i, 0),
                windKmh = daily.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0),
                uv = daily.getJSONArray("uv_index_max").optDouble(i, 0.0)
            )
        }
    }

    fun summary(d: DayWeather): String {
        val temp = if (d.maxC.isNaN()) "" else "${d.maxC.toInt()}° / ${if (d.minC.isNaN()) "-" else d.minC.toInt().toString()}°, "
        return "${temp}rain ${d.rainPct}%, wind ${d.windKmh.toInt()} km/h, UV ${d.uv.toInt()}"
    }

    /** Plain advice for an outdoor run. Returns null when the day looks fine. */
    fun advice(d: DayWeather): String? {
        val tips = ArrayList<String>()
        if (!d.maxC.isNaN()) {
            if (d.maxC >= 35) tips.add("Very hot. Think about the treadmill or a cooler day, and take it easy if you do go out.")
            else if (d.maxC >= 30) tips.add("Hot. Run early or late, carry water and expect to be slower than usual.")
        }
        if (d.rainPct >= 70) tips.add("Rain is likely, so watch slippery surfaces.")
        if (d.windKmh >= 40) tips.add("Strong wind.")
        if (d.uv >= 8) tips.add("High UV, so sunscreen and a cap.")
        return if (tips.isEmpty()) null else tips.joinToString(" ")
    }
}
