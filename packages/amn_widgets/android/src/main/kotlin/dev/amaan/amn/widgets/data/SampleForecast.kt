package dev.amaan.amn.widgets.data

import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.DayPoint
import dev.amaan.amn.widgets.core.Forecast
import dev.amaan.amn.widgets.core.HourPoint
import kotlin.math.PI
import kotlin.math.cos

/**
 * Stand-in forecast (partly cloudy, matching the design page) until the
 * Open-Meteo client and the 30-minute refresh job land.
 * TODO: replace with the cached Open-Meteo forecast for the widget's location.
 */
object SampleForecast {
    private const val LO = 11.0
    private const val HI = 20.0

    fun at(hour: Int, nowMs: Long = System.currentTimeMillis()): Forecast {
        val pops = listOf(10, 10, 15, 20, 20, 25, 25, 20, 15, 15, 10, 10)
        val hourly = (0 until 12).map { i ->
            val h = (hour + i) % 24
            HourPoint(h, tempAt(h), pops[i], 0.0, Condition.PARTLY)
        }
        return Forecast(
            nowTempC = tempAt(hour),
            feelsLikeC = tempAt(hour) - 1,
            condition = Condition.PARTLY,
            hourly = hourly,
            today = DayPoint(Condition.PARTLY, HI, LO, 25, 0.2),
            yesterdayHighC = 19.0,
            days = listOf(
                DayPoint(Condition.CLOUDY, 15.0, 9.0, 30, 0.0),
                DayPoint(Condition.RAIN, 14.0, 8.0, 70, 4.0),
                DayPoint(Condition.PARTLY, 16.0, 9.0, 20, 0.0),
            ),
            sunriseMin = 6 * 60 + 30,
            sunsetMin = 18 * 60,
            fetchedAtEpochMs = nowMs - 12 * 60_000,
        )
    }

    /** Coldest at 05:00, warmest at 15:00. */
    private fun tempAt(h: Int): Double {
        val x = ((h - 5) % 24 + 24) % 24
        val d = if (x <= 10) (1 - cos(PI * x / 10)) / 2 else (1 + cos(PI * (x - 10) / 14)) / 2
        return LO + (HI - LO) * d
    }
}
