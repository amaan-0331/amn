package dev.amaan.amn.widgets.data

import dev.amaan.amn.widgets.core.RawForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Open-Meteo forecast API (free, no key, CC BY 4.0). */
object OpenMeteo {
    private const val BASE = "https://api.open-meteo.com/v1/forecast"

    fun url(lat: Double, lon: Double): String = String.format(
        Locale.US,
        "%s?latitude=%.4f&longitude=%.4f" +
            "&current=temperature_2m,apparent_temperature,weather_code" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
            "precipitation_sum,snowfall_sum,sunrise,sunset" +
            "&past_days=1&forecast_days=7&timezone=auto",
        BASE, lat, lon,
    )

    /** Raw JSON body. Throws on network or HTTP errors. */
    suspend fun fetch(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val conn = URL(url(lat, lon)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "AMN (personal app; github.com)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Open-Meteo HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String, fetchedAtEpochMs: Long): RawForecast {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val d = o.getJSONObject("daily")
        return RawForecast(
            timeZone = o.optString("timezone", "UTC"),
            currentTempC = cur.getDouble("temperature_2m"),
            currentFeelsC = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            currentCode = cur.optInt("weather_code", 3),
            hourlyTime = h.getJSONArray("time").strings(),
            hourlyTemp = h.getJSONArray("temperature_2m").doubles(),
            hourlyPop = h.getJSONArray("precipitation_probability").ints(),
            hourlyPrecip = h.getJSONArray("precipitation").doubles(),
            hourlyCode = h.getJSONArray("weather_code").ints(3),
            dailyTime = d.getJSONArray("time").strings(),
            dailyCode = d.getJSONArray("weather_code").ints(3),
            dailyMax = d.getJSONArray("temperature_2m_max").doubles(),
            dailyMin = d.getJSONArray("temperature_2m_min").doubles(),
            dailyPop = d.getJSONArray("precipitation_probability_max").ints(),
            dailyPrecip = d.getJSONArray("precipitation_sum").doubles(),
            dailySnow = d.getJSONArray("snowfall_sum").doubles(),
            dailySunrise = d.getJSONArray("sunrise").strings(),
            dailySunset = d.getJSONArray("sunset").strings(),
            fetchedAtEpochMs = fetchedAtEpochMs,
        )
    }

    // Open-Meteo sends null for missing values; treat them as 0 (or the given default).
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONArray.doubles() = (0 until length()).map { if (isNull(it)) 0.0 else getDouble(it) }
    private fun JSONArray.ints(default: Int = 0) = (0 until length()).map { if (isNull(it)) default else getInt(it) }
}
