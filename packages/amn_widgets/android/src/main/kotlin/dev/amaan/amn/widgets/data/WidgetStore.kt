package dev.amaan.amn.widgets.data

import android.content.Context
import android.text.format.DateFormat

/** Settings storage. Kotlin owns it; Flutter reads and writes only through the amn/widgets channel. */
class WidgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("amn_widgets", Context.MODE_PRIVATE)

    fun weather(id: Int): WeatherSettings {
        val d = WeatherSettings()
        return WeatherSettings(
            useGps = prefs.getBoolean("w.$id.gps", d.useGps),
            cityName = prefs.getString("w.$id.city", d.cityName) ?: d.cityName,
            lat = prefs.getString("w.$id.lat", null)?.toDoubleOrNull() ?: d.lat,
            lon = prefs.getString("w.$id.lon", null)?.toDoubleOrNull() ?: d.lon,
        )
    }

    fun saveWeather(id: Int, s: WeatherSettings) = prefs.edit()
        .putBoolean("w.$id.gps", s.useGps).putString("w.$id.city", s.cityName)
        .putString("w.$id.lat", s.lat.toString()).putString("w.$id.lon", s.lon.toString())
        .apply()

    fun clock(id: Int): ClockSettings = ClockSettings(
        cityName = prefs.getString("c.$id.city", null),
        countryName = prefs.getString("c.$id.country", null),
        timeZone = prefs.getString("c.$id.tz", null),
        lat = prefs.getString("c.$id.lat", null)?.toDoubleOrNull(),
        lon = prefs.getString("c.$id.lon", null)?.toDoubleOrNull(),
        // New clocks start from the phone's 12/24h setting.
        h24 = prefs.getBoolean("c.$id.h24", DateFormat.is24HourFormat(appContext)),
        seconds = prefs.getBoolean("c.$id.secs", false),
        showWeather = prefs.getBoolean("c.$id.wx", true),
    )

    fun saveClock(id: Int, s: ClockSettings) = prefs.edit()
        .putString("c.$id.city", s.cityName).putString("c.$id.country", s.countryName)
        .putString("c.$id.tz", s.timeZone)
        .putString("c.$id.lat", s.lat?.toString()).putString("c.$id.lon", s.lon?.toString())
        .putBoolean("c.$id.h24", s.h24).putBoolean("c.$id.secs", s.seconds).putBoolean("c.$id.wx", s.showWeather)
        .apply()

    fun units() = Units(prefs.getBoolean("units.f", false), prefs.getBoolean("units.in", false))

    fun saveUnits(u: Units) = prefs.edit().putBoolean("units.f", u.fahrenheit).putBoolean("units.in", u.inches).apply()

    fun remove(id: Int) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("w.$id.") || it.startsWith("c.$id.") }.forEach(e::remove)
        e.apply()
    }
}
