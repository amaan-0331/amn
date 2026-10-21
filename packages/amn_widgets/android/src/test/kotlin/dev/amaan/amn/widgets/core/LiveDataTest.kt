package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDataTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    /** A day-by-day forecast starting yesterday (2026-10-20), hourly from 00:00 that day. */
    private fun raw(fetchedAt: Instant): RawForecast {
        val days = listOf("2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24")
        val hours = days.flatMap { d -> (0 until 24).map { "%sT%02d:00".format(d, it) } }
        return RawForecast(
            timeZone = "Europe/Berlin",
            currentTempC = 17.3, currentFeelsC = 16.1, currentCode = 2,
            hourlyTime = hours,
            hourlyTemp = hours.indices.map { 10.0 + it % 24 / 3.0 },
            hourlyPop = hours.indices.map { if (it in 56..58) 80 else 10 },
            hourlyPrecip = hours.map { 0.0 },
            hourlyCode = hours.indices.map { if (it in 56..58) 61 else 3 },
            dailyTime = days,
            dailyCode = listOf(3, 2, 61, 1, 0),
            dailyMax = listOf(16.0, 20.0, 15.0, 17.0, 18.0),
            dailyMin = listOf(8.0, 11.0, 9.0, 8.0, 7.0),
            dailyPop = listOf(10, 25, 80, 10, 0),
            dailyPrecip = listOf(0.0, 0.2, 6.0, 0.0, 0.0),
            dailySnow = listOf(0.0, 0.0, 0.0, 0.0, 0.0),
            dailySunrise = days.map { "${it}T07:31" },
            dailySunset = days.map { "${it}T18:12" },
            fetchedAtEpochMs = fetchedAt.toEpochMilli(),
        )
    }

    @Test fun buildsFromCurrentHour() {
        val now = Instant.parse("2026-10-21T12:20:00Z") // 14:20 in Berlin
        val f = assertNotNull(ForecastBuilder.build(raw(now), now))
        assertEquals(14, f.hourly.first().hour)
        assertEquals(17.3, f.nowTempC)
        assertEquals(Condition.PARTLY, f.condition)
        assertEquals(16.0, f.yesterdayHighC)
        assertEquals(20.0, f.today.highC)
        assertEquals(listOf("Thu", "Fri", "Sat"), f.days.map { it.weekday })
        assertEquals(7 * 60 + 31, f.sunriseMin)
        // Rain tomorrow morning at 80%; nothing wet in the next 12 hours.
        assertTrue("Rain tomorrow" in (0L..20L).map { Summary.of(f, true, seed = it).text })
    }

    @Test fun olderCacheUsesHourlyValues() {
        val fetched = Instant.parse("2026-10-21T08:05:00Z")
        val now = Instant.parse("2026-10-21T13:10:00Z") // 15:10 in Berlin
        val f = assertNotNull(ForecastBuilder.build(raw(fetched), now))
        assertEquals(15, f.hourly.first().hour)
        assertEquals(10.0 + 15 / 3.0, f.nowTempC)
        assertEquals(Condition.CLOUDY, f.condition)
    }

    @Test fun tooOldCacheIsRejected() {
        val r = raw(Instant.parse("2026-10-21T08:00:00Z"))
        assertNull(ForecastBuilder.build(r, Instant.parse("2026-10-24T20:00:00Z")))
    }

    @Test fun sunTimesBerlinMidsummer() {
        val s = assertNotNull(SunTimes.compute(LocalDate.of(2026, 6, 21), 52.52, 13.41, berlin))
        assertTrue(abs(s.sunriseMin - (4 * 60 + 43)) <= 5, "sunrise ${s.sunriseMin}")
        assertTrue(abs(s.sunsetMin - (21 * 60 + 33)) <= 5, "sunset ${s.sunsetMin}")
    }

    @Test fun polarNight() {
        assertNull(SunTimes.compute(LocalDate.of(2026, 12, 21), 78.22, 15.65, ZoneId.of("Arctic/Longyearbyen")))
    }

    @Test fun clockNextUpdateIsNextBoundary() {
        val sun = { _: LocalDate -> SunDay(7 * 60, 18 * 60) }
        // 10:00 in Tokyo: next boundary is dusk starting at 17:00 Tokyo, unless phone midnight comes first.
        val now = Instant.parse("2026-10-21T01:00:00Z")
        val next = ClockTimeline.nextUpdate(now, ZoneId.of("Asia/Tokyo"), berlin, sun)
        assertEquals(Instant.parse("2026-10-21T08:00:00Z"), next)
        assertEquals(Phase.DAY, ClockTimeline.phaseAt(now, ZoneId.of("Asia/Tokyo"), sun))
        // 21:30 in Berlin is 04:30 in Tokyo: Tokyo dawn (06:30 = 21:30Z) comes before Berlin midnight (22:00Z).
        val late = Instant.parse("2026-10-21T19:30:00Z")
        assertEquals(Instant.parse("2026-10-21T21:30:00Z"), ClockTimeline.nextUpdate(late, ZoneId.of("Asia/Tokyo"), berlin, sun))
    }
}
