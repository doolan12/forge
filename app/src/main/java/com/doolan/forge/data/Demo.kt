package com.doolan.forge.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A demo picture found on wger.de (community exercise database, CC-BY-SA). */
data class DemoImage(val matchedName: String, val bitmap: Bitmap)

object Demo {
    private const val BASE = "https://wger.de"

    fun youtubeUrl(name: String): String =
        "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$name proper form tutorial", "UTF-8")

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    /** Returns null on any problem (offline, no match, no picture). Never throws. */
    suspend fun find(name: String): DemoImage? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(name, "UTF-8")
            val c = open("$BASE/api/v2/exercise/search/?term=$q&language=english")
            val body = try {
                if (c.responseCode !in 200..299) return@withContext null
                c.inputStream.bufferedReader().readText()
            } finally { c.disconnect() }
            val list = JSONObject(body).optJSONArray("suggestions") ?: return@withContext null
            for (i in 0 until minOf(list.length(), 5)) {
                val s = list.optJSONObject(i) ?: continue
                val data = s.optJSONObject("data") ?: continue
                val img = data.optString("image", "")
                if (img.isBlank() || img == "null") continue
                val full = if (img.startsWith("http")) img else BASE + img
                val ic = open(full)
                val bmp = try {
                    if (ic.responseCode !in 200..299) null
                    else ic.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally { ic.disconnect() }
                if (bmp != null) {
                    val label = s.optString("value", "").ifBlank { data.optString("name", name) }
                    return@withContext DemoImage(label, bmp)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
