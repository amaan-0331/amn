package dev.amaan.amn.widgets.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryTest {
    private val ab = mapOf('k' to Condition.CLEAR, 'p' to Condition.PARTLY, 'c' to Condition.CLOUDY, 'r' to Condition.RAIN,
        't' to Condition.STORM, 's' to Condition.SNOW, 'f' to Condition.FOG)

    /** Mirrors the sample forecasts in the design page. */
    private fun fx(
        hour: Int, hi: Double, yHi: Double, pops: String, conds: String, tomorrow: DayPoint,
        temp: Double = 10.0, feels: Double = 9.0, temps: List<Double>? = null, more: List<DayPoint> = emptyList(),
        sunrise: Int = 390, sunset: Int = 1080,
    ): Forecast {
        val p = pops.split(' ').map { it.toInt() }
        val c = conds.split(' ').map { ab.getValue(it[0]) }
        val hourly = (0 until 12).map { HourPoint((hour + it) % 24, temps?.get(it) ?: temp, p[it], 0.0, c[it]) }
        return Forecast(temp, feels, c[0], hourly, DayPoint(c[0], hi, hi - 10, 0, 0.0), yHi, listOf(tomorrow) + more, sunrise, sunset, 0)
    }

    private val dry = "0 0 0 0 0 0 0 0 0 0 0 0"
    private val clouds = "c c c c c c c c c c c c"
    private val clear = { h: Int -> fx(h, 26.0, 21.0, "0 0 0 0 5 5 5 5 0 0 0 0", "k k k k k k k k k k k k", DayPoint(Condition.CLEAR, 25.0, 13.0, 10, 0.0)) }
    private val partly = { h: Int -> fx(h, 20.0, 19.0, "10 10 15 20 20 25 25 20 15 15 10 10", "p p p p p p p p p p p p", DayPoint(Condition.CLOUDY, 15.0, 9.0, 30, 0.0)) }
    private val cloudy = { h: Int -> fx(h, 15.0, 15.0, "20 25 30 30 35 40 40 40 45 45 45 45", clouds, DayPoint(Condition.RAIN, 13.0, 8.0, 80, 6.0)) }
    private val rain = { h: Int -> fx(h, 13.0, 15.0, "90 85 70 30 10 5 5 5 10 10 10 10", "r r r c c p p p c c c c", DayPoint(Condition.CLOUDY, 14.0, 7.0, 20, 0.0)) }
    private val storm = { h: Int -> fx(h, 29.0, 31.0, "80 90 80 40 20 10 10 10 10 10 10 10", "t t t r c c p p p p p p", DayPoint(Condition.RAIN, 26.0, 18.0, 60, 4.0)) }
    private val snow = { h: Int -> fx(h, 0.0, 4.0, "80 85 85 90 90 85 85 80 80 80 75 75", "s s s s s s s s s s s s", DayPoint(Condition.SNOW, 1.0, -5.0, 60, 3.0)) }
    private val fog = { h: Int -> fx(h, 12.0, 11.0, "5 5 5 5 5 10 10 10 10 10 10 10", "f f f c p p p p p p p p", DayPoint(Condition.PARTLY, 12.0, 6.0, 10, 0.0)) }

    /** The canonical line (seed 0). */
    private fun line(f: Forecast, h24: Boolean = true) = Summary.of(f, h24).text

    /** Every line the rotation can produce. */
    private fun lines(f: Forecast, maxLen: Int = 24) = (0L..40L).map { Summary.of(f, true, maxLen = maxLen, seed = it).text }.toSet()

    // Urgent rules: fixed priority, seed 0 gives the plain phrasing.
    @Test fun storms() = assertEquals("Storms until 13:00", line(storm(10)))
    @Test fun stormsTonight() = assertEquals("Storms tonight", line(storm(23)))
    @Test fun dryBy() = assertEquals("Dry by 13:00", line(rain(10)))
    @Test fun dryBy12h() = assertEquals("Dry by 1 PM", line(rain(10), h24 = false))
    @Test fun rainIntoTonight() = assertEquals("Rain into tonight", line(rain(22)))
    @Test fun snowAllDay() = assertEquals("Snow all day", line(snow(9)))
    @Test fun snowIntoTonight() = assertEquals("Snow into tonight", line(snow(16)))
    @Test fun fogClears() = assertEquals("Fog clears by 11:00", line(fog(8)))

    @Test fun rainLater() {
        val f = fx(9, 15.0, 15.0, "10 10 20 60 70 40 10 10 10 10 10 10", "c c c r r c c c c c c c", DayPoint(Condition.CLOUDY, 15.0, 9.0, 10, 0.0))
        assertEquals("Rain from 12:00", line(f))
        assertTrue(lines(f).all { it.contains("12:00") }, "urgent lines keep the time: ${lines(f)}")
    }

    @Test fun urgentPhrasingsVary() = assertTrue(lines(rain(10)).size >= 3, "${lines(rain(10))}")

    @Test fun scorcher() {
        val f = fx(11, 42.0, 41.0, dry, "k k k k k k k k k k k k", DayPoint(Condition.CLEAR, 41.0, 30.0, 0, 0.0), temp = 38.0, feels = 39.0)
        assertEquals("Scorcher: 42° today", line(f))
        assertEquals("Scorcher: 108° today", Summary.of(f, true, deg = { "${Math.round(it * 9 / 5 + 32)}°" }).text)
    }

    @Test fun frost() {
        val temps = listOf(3.0, 2.0, 1.0, 0.0, -1.0, -2.0, -2.0, -3.0, -3.0, -2.0, -1.0, 0.0)
        val f = fx(17, 6.0, 6.0, dry, clouds, DayPoint(Condition.CLOUDY, 6.0, -3.0, 0, 0.0), temp = 3.0, feels = 2.0, temps = temps)
        assertEquals("Freezing tonight", line(f))
    }

    // Flavor rules: rotated, so check the set of lines.
    @Test fun hotterTodayIsACandidate() = assertTrue("Hotter today" in lines(clear(10)), "${lines(clear(10))}")
    @Test fun coolerTomorrowIsACandidate() = assertTrue("Cooler tomorrow" in lines(partly(10)), "${lines(partly(10))}")
    @Test fun rainTomorrowIsACandidate() = assertTrue("Rain tomorrow" in lines(cloudy(10)), "${lines(cloudy(10))}")

    @Test fun rotationGivesVariety() {
        // Clear, mild, dry, hotter than yesterday: several rules apply at once.
        val all = lines(clear(10))
        assertTrue(all.size >= 5, "only ${all.size}: $all")
    }

    @Test fun sameSeedSameLine() = assertEquals(Summary.of(clear(10), true, seed = 7), Summary.of(clear(10), true, seed = 7))

    @Test fun eveningSkipsTodayVsYesterday() {
        val tomorrow = DayPoint(Condition.CLOUDY, 18.0, 9.0, 30, 0.0)
        val morning = lines(fx(10, 24.0, 20.0, dry, clouds, tomorrow))
        val evening = lines(fx(19, 24.0, 20.0, dry, clouds, tomorrow))
        assertTrue("Hotter today" in morning, "$morning")
        assertTrue(evening.none { it.contains("yesterday") || it == "Hotter today" }, "$evening")
        assertTrue("Cooler tomorrow" in evening, "$evening")
    }

    @Test fun sunsetSoon() {
        val f = fx(17, 25.0, 25.0, dry, "k k k k k k k k k k k k", DayPoint(Condition.CLEAR, 25.0, 15.0, 30, 0.0), temp = 22.0, feels = 22.0, sunset = 17 * 60 + 52)
        assertTrue("Sunset at 17:52" in lines(f), "${lines(f)}")
    }

    @Test fun clearNight() {
        val f = fx(22, 25.0, 25.0, dry, "k k k k k k k k k k k k", DayPoint(Condition.CLEAR, 25.0, 15.0, 30, 0.0), temp = 18.0, feels = 18.0)
        assertTrue("Clear skies tonight" in lines(f), "${lines(f)}")
    }

    @Test fun fallback() {
        val f = fx(10, 15.0, 15.0, dry, clouds, DayPoint(Condition.CLOUDY, 16.0, 9.0, 30, 0.0))
        assertEquals("Like yesterday", line(f))
        assertEquals(setOf("Like yesterday", "Grey but dry", "Overcast, calm"), lines(f))
    }

    @Test fun everyLineFitsItsSize() {
        val all = listOf(clear(10), partly(10), cloudy(10), rain(10), storm(10), snow(9), fog(8), clear(22), rain(22), storm(14), partly(17))
        for (max in listOf(16, 17, 22, 24)) for (f in all) for (text in lines(f, max)) {
            assertTrue(text.length <= max || lines(f, max).size == 1, "\"$text\" is longer than $max")
        }
    }

    @Test fun smallSizesPickTheSameUrgentRule() {
        val urgent = listOf(rain(10), rain(22), storm(10), storm(23), snow(9), snow(16), fog(8),
            fx(9, 15.0, 15.0, "10 10 20 60 70 40 10 10 10 10 10 10", "c c c r r c c c c c c c", DayPoint(Condition.CLOUDY, 15.0, 9.0, 10, 0.0)))
        for (f in urgent) for (h24 in listOf(true, false)) {
            val big = Summary.explain(f, h24, maxLen = 24).winner
            val small = Summary.explain(f, h24, maxLen = 16).winner
            assertEquals(big, small, "at 16 chars: ${Summary.of(f, h24, maxLen = 16).text}")
        }
    }

    @Test fun wmoMapping() {
        assertEquals(Condition.CLEAR, Condition.fromWmo(1))
        assertEquals(Condition.RAIN, Condition.fromWmo(53))
        assertEquals(Condition.SNOW, Condition.fromWmo(86))
        assertEquals(Condition.STORM, Condition.fromWmo(99))
        assertEquals(Condition.FOG, Condition.fromWmo(48))
    }
}
