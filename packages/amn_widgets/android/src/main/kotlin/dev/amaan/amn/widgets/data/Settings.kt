package dev.amaan.amn.widgets.data

import kotlin.math.roundToInt

/** Per-widget settings for a weather widget. */
data class WeatherSettings(
    val useGps: Boolean = true,
    val cityName: String = "Berlin",
    val lat: Double = 52.52,
    val lon: Double = 13.41,
) {
    fun toMap(): Map<String, Any> = mapOf("useGps" to useGps, "cityName" to cityName, "lat" to lat, "lon" to lon)

    companion object {
        fun from(m: Map<*, *>, base: WeatherSettings = WeatherSettings()) = WeatherSettings(
            useGps = m["useGps"] as? Boolean ?: base.useGps,
            cityName = m["cityName"] as? String ?: base.cityName,
            lat = (m["lat"] as? Number)?.toDouble() ?: base.lat,
            lon = (m["lon"] as? Number)?.toDouble() ?: base.lon,
        )
    }
}

/** Per-widget settings for a clock widget. A null [timeZone] means no city picked yet. */
data class ClockSettings(
    val cityName: String? = null,
    val countryName: String? = null,
    val timeZone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val h24: Boolean = true,
    val seconds: Boolean = false,
    /** Show the city's current weather (refreshed hourly). */
    val showWeather: Boolean = true,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "cityName" to cityName, "countryName" to countryName, "timeZone" to timeZone,
        "lat" to lat, "lon" to lon, "h24" to h24, "seconds" to seconds, "showWeather" to showWeather,
    )

    companion object {
        fun from(m: Map<*, *>, base: ClockSettings = ClockSettings()) = ClockSettings(
            cityName = m["cityName"] as? String ?: base.cityName,
            countryName = m["countryName"] as? String ?: base.countryName,
            timeZone = m["timeZone"] as? String ?: base.timeZone,
            lat = (m["lat"] as? Number)?.toDouble() ?: base.lat,
            lon = (m["lon"] as? Number)?.toDouble() ?: base.lon,
            h24 = m["h24"] as? Boolean ?: base.h24,
            seconds = m["seconds"] as? Boolean ?: base.seconds,
            showWeather = m["showWeather"] as? Boolean ?: base.showWeather,
        )
    }
}

/** App-wide units, shared by every weather widget. */
data class Units(val fahrenheit: Boolean = false, val inches: Boolean = false) {
    fun temp(celsius: Double): Int = (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()
    fun toMap(): Map<String, Any> = mapOf("fahrenheit" to fahrenheit, "inches" to inches)

    companion object {
        fun from(m: Map<*, *>, base: Units = Units()) = Units(
            fahrenheit = m["fahrenheit"] as? Boolean ?: base.fahrenheit,
            inches = m["inches"] as? Boolean ?: base.inches,
        )
    }
}

/** What a weather widget renders: its settings plus the app-wide units. Also the preview state. */
data class WeatherState(
    val settings: WeatherSettings,
    val units: Units,
    /** Set by the edit page's preview, which renders with a fake id but should show this widget's cached weather. */
    val previewWidgetId: Int? = null,
    /** Widget lab only: draw this sample condition at this hour instead of real data. */
    val debug: WidgetDebug? = null,
)

/** Widget lab overrides (debug builds). [condition] is ignored by the clock. */
data class WidgetDebug(val condition: dev.amaan.amn.widgets.core.Condition, val hour: Int, val h24: Boolean? = null, val seed: Long? = null)

/** Clock preview state: unsaved settings, plus optional lab overrides. */
data class ClockPreview(
    val settings: ClockSettings,
    val debug: WidgetDebug? = null,
    /** The clock being edited, so the preview can show its cached weather. */
    val previewWidgetId: Int? = null,
)
