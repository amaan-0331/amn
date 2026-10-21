package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Open-Meteo response as plain lists (parsed on Android, built here so it can be tested).
 * Times are local to [timeZone], in ISO form without an offset ("2026-10-21T14:00").
 */
data class RawForecast(
    val timeZone: String,
    val currentTempC: Double,
    val currentFeelsC: Double,
    val currentCode: Int,
    val hourlyTime: List<String>,
    val hourlyTemp: List<Double>,
    val hourlyPop: List<Int>,
    val hourlyPrecip: List<Double>,
    val hourlyCode: List<Int>,
    val dailyTime: List<String>,
    val dailyCode: List<Int>,
    val dailyMax: List<Double>,
    val dailyMin: List<Double>,
    val dailyPop: List<Int>,
    val dailyPrecip: List<Double>,
    val dailySnow: List<Double>,
    val dailySunrise: List<String>,
    val dailySunset: List<String>,
    val fetchedAtEpochMs: Long,
)

object ForecastBuilder {
    const val HOURS = 12

    /**
     * The forecast as seen at [now]. Needs yesterday (past_days=1), today, and 12 hours ahead;
     * returns null when the cached data no longer covers that.
     */
    fun build(raw: RawForecast, now: Instant): Forecast? {
        val zone = runCatching { ZoneId.of(raw.timeZone) }.getOrDefault(ZoneId.of("UTC"))
        val local = LocalDateTime.ofInstant(now, zone)
        val hourKey = local.truncatedTo(ChronoUnit.HOURS)
        val times = raw.hourlyTime.map(LocalDateTime::parse)
        val start = times.indexOfFirst { it == hourKey }
        if (start < 0 || start + HOURS > times.size) return null

        val hourly = (start until start + HOURS).map { i ->
            HourPoint(times[i].hour, raw.hourlyTemp[i], raw.hourlyPop.getOrElse(i) { 0 }, raw.hourlyPrecip.getOrElse(i) { 0.0 }, Condition.fromWmo(raw.hourlyCode[i]))
        }
        val di = raw.dailyTime.indexOf(local.toLocalDate().toString())
        if (di < 1 || di + 1 >= raw.dailyTime.size) return null

        fun day(i: Int) = DayPoint(
            condition = Condition.fromWmo(raw.dailyCode[i]),
            highC = raw.dailyMax[i],
            lowC = raw.dailyMin[i],
            pop = raw.dailyPop.getOrElse(i) { 0 },
            precipMm = raw.dailyPrecip.getOrElse(i) { 0.0 },
            snowCm = raw.dailySnow.getOrElse(i) { 0.0 },
            weekday = LocalDate.parse(raw.dailyTime[i]).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
        )

        // "Current" values are only current within the hour they were fetched.
        val fetchedHour = LocalDateTime.ofInstant(Instant.ofEpochMilli(raw.fetchedAtEpochMs), zone).truncatedTo(ChronoUnit.HOURS)
        val fresh = fetchedHour == hourKey
        val nowTemp = if (fresh) raw.currentTempC else hourly[0].tempC
        val feelsDelta = raw.currentFeelsC - raw.currentTempC

        return Forecast(
            nowTempC = nowTemp,
            feelsLikeC = nowTemp + feelsDelta,
            condition = if (fresh) Condition.fromWmo(raw.currentCode) else hourly[0].condition,
            hourly = hourly,
            today = day(di),
            yesterdayHighC = raw.dailyMax[di - 1],
            days = (di + 1 until minOf(di + 6, raw.dailyTime.size)).map(::day), // up to 5 days ahead
            sunriseMin = minutesOf(raw.dailySunrise.getOrNull(di)) ?: (6 * 60 + 30),
            sunsetMin = minutesOf(raw.dailySunset.getOrNull(di)) ?: (18 * 60),
            fetchedAtEpochMs = raw.fetchedAtEpochMs,
        )
    }

    /** Local hour of day "now" in the forecast's zone. */
    fun localNow(raw: RawForecast, now: Instant): LocalDateTime =
        LocalDateTime.ofInstant(now, runCatching { ZoneId.of(raw.timeZone) }.getOrDefault(ZoneId.of("UTC")))

    private fun minutesOf(iso: String?): Int? = iso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }?.let { it.hour * 60 + it.minute }
}
