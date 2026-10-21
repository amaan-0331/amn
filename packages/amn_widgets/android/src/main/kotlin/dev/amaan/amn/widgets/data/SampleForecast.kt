package dev.amaan.amn.widgets.data

import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.DayPoint
import dev.amaan.amn.widgets.core.Forecast
import dev.amaan.amn.widgets.core.HourPoint
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

/**
 * Sample forecasts, one per condition (the same ones as the design page).
 * Used by the edit-page preview before the first update, and by the widget lab.
 */
object SampleForecast {
    private class Fixture(
        val lo: Double, val hi: Double, val yHi: Double, val feelsDelta: Double,
        pops: String, mm: String, conds: String,
        val todayPop: Int, val todayMm: Double, val todaySnowCm: Double,
        val days: List<Triple<Condition, Pair<Double, Double>, Int>>,
    ) {
        val pops = pops.split(' ').map { it.toInt() }
        val mm = mm.split(' ').map { it.toDouble() }
        val conds = conds.split(' ').map { AB.getValue(it[0]) }
    }

    private val AB = mapOf(
        'k' to Condition.CLEAR, 'p' to Condition.PARTLY, 'c' to Condition.CLOUDY, 'r' to Condition.RAIN,
        't' to Condition.STORM, 's' to Condition.SNOW, 'f' to Condition.FOG,
    )
    private const val DRY = "0 0 0 0 0 0 0 0 0 0 0 0"
    private fun d(c: Condition, hi: Double, lo: Double, pop: Int) = Triple(c, hi to lo, pop)

    private val fixtures = mapOf(
        Condition.CLEAR to Fixture(14.0, 26.0, 21.0, 1.0, "0 0 0 0 5 5 5 5 0 0 0 0", DRY, "k k k k k k k k k k k k", 5, 0.0, 0.0,
            listOf(d(Condition.CLEAR, 25.0, 13.0, 10), d(Condition.PARTLY, 22.0, 12.0, 20), d(Condition.CLOUDY, 19.0, 11.0, 40))),
        Condition.PARTLY to Fixture(11.0, 20.0, 19.0, -1.0, "10 10 15 20 20 25 25 20 15 15 10 10", DRY, "p p p p p p p p p p p p", 25, 0.2, 0.0,
            listOf(d(Condition.CLOUDY, 15.0, 9.0, 30), d(Condition.RAIN, 14.0, 8.0, 70), d(Condition.PARTLY, 16.0, 9.0, 20))),
        Condition.CLOUDY to Fixture(9.0, 15.0, 15.0, -2.0, "20 25 30 30 35 40 40 40 45 45 45 45", DRY, "c c c c c c c c c c c c", 40, 0.6, 0.0,
            listOf(d(Condition.RAIN, 13.0, 8.0, 80), d(Condition.RAIN, 12.0, 7.0, 65), d(Condition.CLOUDY, 14.0, 8.0, 30))),
        Condition.RAIN to Fixture(8.0, 13.0, 15.0, -3.0, "90 85 70 30 10 5 5 5 10 10 10 10", "1.8 1.2 0.6 0.1 0 0 0 0 0 0 0 0", "r r r c c p p p c c c c", 95, 6.4, 0.0,
            listOf(d(Condition.CLOUDY, 14.0, 7.0, 20), d(Condition.PARTLY, 15.0, 8.0, 10), d(Condition.CLEAR, 17.0, 9.0, 0))),
        Condition.STORM to Fixture(19.0, 29.0, 31.0, 3.0, "80 90 80 40 20 10 10 10 10 10 10 10", "6 12 8 1 0 0 0 0 0 0 0 0", "t t t r c c p p p p p p", 90, 25.0, 0.0,
            listOf(d(Condition.RAIN, 26.0, 18.0, 60), d(Condition.PARTLY, 28.0, 19.0, 20), d(Condition.CLEAR, 30.0, 20.0, 0))),
        Condition.SNOW to Fixture(-6.0, 0.0, 4.0, -5.0, "80 85 85 90 90 85 85 80 80 80 75 75", "0.6 0.8 0.8 1 1 0.9 0.8 0.7 0.7 0.6 0.6 0.5", "s s s s s s s s s s s s", 90, 7.0, 4.9,
            listOf(d(Condition.SNOW, 1.0, -5.0, 60), d(Condition.CLOUDY, 2.0, -4.0, 30), d(Condition.PARTLY, 3.0, -3.0, 10))),
        Condition.FOG to Fixture(5.0, 12.0, 11.0, -2.0, "5 5 5 5 5 10 10 10 10 10 10 10", DRY, "f f f c p p p p p p p p", 10, 0.0, 0.0,
            listOf(d(Condition.PARTLY, 12.0, 6.0, 10), d(Condition.CLEAR, 13.0, 5.0, 0), d(Condition.CLOUDY, 11.0, 6.0, 30))),
    )

    /** Partly cloudy at [hour]. */
    fun at(hour: Int, nowMs: Long = System.currentTimeMillis()): Forecast = of(Condition.PARTLY, hour, nowMs)

    fun of(condition: Condition, hour: Int, nowMs: Long = System.currentTimeMillis()): Forecast {
        val fx = fixtures.getValue(condition)
        fun tempAt(h: Int): Double {
            val x = ((h - 5) % 24 + 24) % 24
            val k = if (x <= 10) (1 - cos(PI * x / 10)) / 2 else (1 + cos(PI * (x - 10) / 14)) / 2
            return Math.round((fx.lo + (fx.hi - fx.lo) * k) * 10) / 10.0
        }
        val hourly = (0 until 12).map { i ->
            val h = (hour + i) % 24
            HourPoint(h, tempAt(h), fx.pops[i], fx.mm[i], fx.conds[i])
        }
        val today = LocalDate.now()
        return Forecast(
            nowTempC = tempAt(hour),
            feelsLikeC = tempAt(hour) + fx.feelsDelta,
            condition = fx.conds[0],
            hourly = hourly,
            today = DayPoint(fx.conds[0], fx.hi, fx.lo, fx.todayPop, fx.todayMm, fx.todaySnowCm),
            yesterdayHighC = fx.yHi,
            // Five days ahead: the fixture's three, then two milder ones.
            days = (fx.days + listOf(d(Condition.PARTLY, fx.hi + 1, fx.lo + 1, 20), d(Condition.CLEAR, fx.hi + 2, fx.lo + 1, 0))).mapIndexed { i, (c, hl, pop) ->
                DayPoint(c, hl.first, hl.second, pop, 0.0, weekday = today.plusDays(i + 1L).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
            },
            sunriseMin = 6 * 60 + 30,
            sunsetMin = 18 * 60,
            fetchedAtEpochMs = nowMs - 12 * 60_000,
        )
    }
}
