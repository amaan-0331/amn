package dev.amaan.amn.widgets.data

import android.content.Context
import android.util.Log
import dev.amaan.amn.widgets.core.RawForecast

/** Last good forecast per weather widget. Widgets always draw from here, never from the network. */
class ForecastCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("amn_forecast", Context.MODE_PRIVATE)

    data class Entry(val raw: RawForecast, val placeName: String?)

    fun load(id: Int): Entry? {
        val json = prefs.getString("f.$id.json", null) ?: return null
        val at = prefs.getLong("f.$id.at", 0)
        return runCatching { Entry(OpenMeteo.parse(json, at), prefs.getString("f.$id.place", null)) }
            .onFailure { Log.w("AmnWidgets", "Bad cached forecast for $id", it) }
            .getOrNull()
    }

    fun fetchedAt(id: Int): Long? = prefs.getLong("f.$id.at", 0).takeIf { it > 0 }

    fun placeName(id: Int): String? = prefs.getString("f.$id.place", null)

    fun save(id: Int, json: String, fetchedAt: Long, placeName: String?) = prefs.edit()
        .putString("f.$id.json", json).putLong("f.$id.at", fetchedAt).putString("f.$id.place", placeName)
        .apply()

    fun remove(id: Int) = prefs.edit().remove("f.$id.json").remove("f.$id.at").remove("f.$id.place").apply()
}
