package dev.amaan.amn.widgets.core

/** One hour of forecast. [hour] is the local hour of day (0-23). */
data class HourPoint(
    val hour: Int,
    val tempC: Double,
    val pop: Int,
    val precipMm: Double,
    val condition: Condition,
)

data class DayPoint(
    val condition: Condition,
    val highC: Double,
    val lowC: Double,
    val pop: Int,
    val precipMm: Double,
    val snowCm: Double = 0.0,
)

/**
 * Everything a weather widget shows.
 * [hourly] starts at the current hour and holds at least 12 points.
 * [days] starts with tomorrow.
 */
data class Forecast(
    val nowTempC: Double,
    val feelsLikeC: Double,
    val condition: Condition,
    val hourly: List<HourPoint>,
    val today: DayPoint,
    val yesterdayHighC: Double,
    val days: List<DayPoint>,
    val sunriseMin: Int,
    val sunsetMin: Int,
    val fetchedAtEpochMs: Long,
)
