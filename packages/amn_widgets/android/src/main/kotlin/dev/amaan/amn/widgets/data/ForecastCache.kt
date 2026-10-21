package dev.amaan.amn.widgets.data

import android.content.Context
import android.util.Log
import dev.amaan.amn.widgets.core.RawForecast

/**
 * Last good forecast per widget. Widgets always draw from here, never from the network.
 * [namespace] keeps weather widgets ("f") and clock widgets ("cw") apart.
 */
class ForecastCache(context: Context, private val namespace: String = "f") {
    private val prefs = context.applicationContext.getSharedPreferences("amn_forecast", Context.MODE_PRIVATE)

    data class Entry(val raw: RawForecast, val placeName: String?)

    fun load(id: Int): Entry? {
        val json = prefs.getString("$namespace.$id.json", null) ?: return null
        val at = prefs.getLong("$namespace.$id.at", 0)
        return runCatching { Entry(OpenMeteo.parse(json, at), prefs.getString("$namespace.$id.place", null)) }
            .onFailure { Log.w("AmnWidgets", "Bad cached forecast for $id", it) }
            .getOrNull()
    }

    fun fetchedAt(id: Int): Long? = prefs.getLong("$namespace.$id.at", 0).takeIf { it > 0 }

    fun placeName(id: Int): String? = prefs.getString("$namespace.$id.place", null)

    fun save(id: Int, json: String, fetchedAt: Long, placeName: String?) = prefs.edit()
        .putString("$namespace.$id.json", json).putLong("$namespace.$id.at", fetchedAt).putString("$namespace.$id.place", placeName)
        .apply()

    fun remove(id: Int) = prefs.edit().remove("$namespace.$id.json").remove("$namespace.$id.at").remove("$namespace.$id.place").apply()
}
