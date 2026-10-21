package dev.amaan.amn.widgets.core

import kotlin.test.assertEquals
import kotlin.test.Test

class SummaryTest {
    private val ab = mapOf('k' to Condition.CLEAR, 'p' to Condition.PARTLY, 'c' to Condition.CLOUDY, 'r' to Condition.RAIN,
        't' to Condition.STORM, 's' to Condition.SNOW, 'f' to Condition.FOG)

    /** Mirrors the sample forecasts in the design page. */
    private fun fx(hour: Int, hi: Double, yHi: Double, pops: String, conds: String, tomorrow: DayPoint): Forecast {
        val p = pops.split(' ').map { it.toInt() }
        val c = conds.split(' ').map { ab.getValue(it[0]) }
        val hourly = (0 until 12).map { HourPoint((hour + it) % 24, 10.0, p[it], 0.0, c[it]) }
        return Forecast(10.0, 9.0, c[0], hourly, DayPoint(c[0], hi, hi - 10, 0, 0.0), yHi, listOf(tomorrow), 390, 1080, 0)
    }

    private val clear = { h: Int -> fx(h, 26.0, 21.0, "0 0 0 0 5 5 5 5 0 0 0 0", "k k k k k k k k k k k k", DayPoint(Condition.CLEAR, 25.0, 13.0, 10, 0.0)) }
    private val partly = { h: Int -> fx(h, 20.0, 19.0, "10 10 15 20 20 25 25 20 15 15 10 10", "p p p p p p p p p p p p", DayPoint(Condition.CLOUDY, 15.0, 9.0, 30, 0.0)) }
    private val cloudy = { h: Int -> fx(h, 15.0, 15.0, "20 25 30 30 35 40 40 40 45 45 45 45", "c c c c c c c c c c c c", DayPoint(Condition.RAIN, 13.0, 8.0, 80, 6.0)) }
    private val rain = { h: Int -> fx(h, 13.0, 15.0, "90 85 70 30 10 5 5 5 10 10 10 10", "r r r c c p p p c c c c", DayPoint(Condition.CLOUDY, 14.0, 7.0, 20, 0.0)) }
    private val storm = { h: Int -> fx(h, 29.0, 31.0, "80 90 80 40 20 10 10 10 10 10 10 10", "t t t r c c p p p p p p", DayPoint(Condition.RAIN, 26.0, 18.0, 60, 4.0)) }
    private val snow = { h: Int -> fx(h, 0.0, 4.0, "80 85 85 90 90 85 85 80 80 80 75 75", "s s s s s s s s s s s s", DayPoint(Condition.SNOW, 1.0, -5.0, 60, 3.0)) }
    private val fog = { h: Int -> fx(h, 12.0, 11.0, "5 5 5 5 5 10 10 10 10 10 10 10", "f f f c p p p p p p p p", DayPoint(Condition.PARTLY, 12.0, 6.0, 10, 0.0)) }

    private fun line(f: Forecast, h24: Boolean = true) = Summary.of(f).english(h24)

    @Test fun storms() = assertEquals("Storms until 13:00", line(storm(10)))
    @Test fun stormsTonight() = assertEquals("Storms tonight", line(storm(23)))
    @Test fun dryBy() = assertEquals("Dry by 13:00", line(rain(10)))
    @Test fun dryBy12h() = assertEquals("Dry by 1 PM", line(rain(10), h24 = false))
    @Test fun rainIntoTonight() = assertEquals("Rain into tonight", line(rain(22)))
    @Test fun snowAllDay() = assertEquals("Snow all day", line(snow(9)))
    @Test fun snowIntoTonight() = assertEquals("Snow into tonight", line(snow(16)))
    @Test fun fogClears() = assertEquals("Fog clears by 11:00", line(fog(8)))
    @Test fun rainTomorrow() = assertEquals("Rain tomorrow", line(cloudy(10)))
    @Test fun hotterToday() = assertEquals("Hotter today", line(clear(10)))
    @Test fun coolerTomorrow() = assertEquals("Cooler tomorrow", line(partly(10)))

    @Test fun rainLater() {
        val f = fx(9, 15.0, 15.0, "10 10 20 60 70 40 10 10 10 10 10 10", "c c c r r c c c c c c c", DayPoint(Condition.CLOUDY, 15.0, 9.0, 10, 0.0))
        assertEquals("Rain from 12:00", line(f))
    }

    @Test fun eveningPrefersTomorrow() {
        val tomorrow = DayPoint(Condition.CLOUDY, 18.0, 9.0, 10, 0.0)
        val conds = "c c c c c c c c c c c c"
        val pops = "0 0 0 0 0 0 0 0 0 0 0 0"
        assertEquals("Hotter today", line(fx(10, 24.0, 20.0, pops, conds, tomorrow)))
        assertEquals("Cooler tomorrow", line(fx(19, 24.0, 20.0, pops, conds, tomorrow)))
    }

    @Test fun fallback() {
        val f = fx(10, 15.0, 15.0, "0 0 0 0 0 0 0 0 0 0 0 0", "c c c c c c c c c c c c", DayPoint(Condition.CLOUDY, 16.0, 9.0, 10, 0.0))
        assertEquals("Like yesterday", line(f))
    }

    @Test fun wmoMapping() {
        assertEquals(Condition.CLEAR, Condition.fromWmo(1))
        assertEquals(Condition.RAIN, Condition.fromWmo(53))
        assertEquals(Condition.SNOW, Condition.fromWmo(86))
        assertEquals(Condition.STORM, Condition.fromWmo(99))
        assertEquals(Condition.FOG, Condition.fromWmo(48))
    }
}
