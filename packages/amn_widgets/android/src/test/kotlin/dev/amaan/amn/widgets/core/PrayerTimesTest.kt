package dev.amaan.amn.widgets.core

import dev.amaan.amn.widgets.data.PrayerBundle
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerTimesTest {
    private val bundle = PrayerBundle.parse(File("src/main/assets/prayer/uae.json").readText())
    private val table = PrayerTimetable(bundle.zones)

    @Test
    fun `bundle covers every zone and area`() {
        assertEquals(41, bundle.zones.size)
        assertEquals(319, bundle.areas.size)
        assertTrue(bundle.zones.values.all(PrayerTimetable::isValid))
        assertTrue(bundle.areas.all { it.zone in bundle.zones })
        assertEquals("Al-Karama", bundle.area(50326)?.name)
    }

    @Test
    fun `day index follows a leap year`() {
        assertEquals(0, PrayerTimetable.dayIndex(1, 1))
        assertEquals(59, PrayerTimetable.dayIndex(2, 29))
        assertEquals(60, PrayerTimetable.dayIndex(3, 1))
        assertEquals(365, PrayerTimetable.dayIndex(12, 31))
    }

    @Test
    fun `Dubai on 24 Oct 2026 matches awqaf gov ae`() {
        val day = table.day(69, LocalDate.of(2026, 10, 24))
        assertEquals(listOf("05:04", "06:18", "12:06", "15:19", "17:48", "19:02"), day.map { it.hhmm })
        // 05:04 in the UAE is 01:04 UTC.
        assertEquals(Instant.parse("2026-10-24T01:04:00Z"), day.first().at)
    }

    @Test
    fun `next skips sunrise and rolls into tomorrow after Isha`() {
        // 06:00 UAE: sunrise is next on the clock, but Dhuhr is the next prayer.
        assertEquals(Prayer.DHUHR, table.next(69, Instant.parse("2026-10-24T02:00:00Z")).prayer)
        // 20:00 UAE: tomorrow's Fajr.
        val n = table.next(69, Instant.parse("2026-10-24T16:00:00Z"))
        assertEquals(Prayer.FAJR, n.prayer)
        assertEquals(Instant.parse("2026-10-25T01:04:00Z"), n.at)
    }

    @Test
    fun `current is the last prayer begun, from yesterday before Fajr`() {
        // 13:00 UAE: Dhuhr's time.
        assertEquals(Prayer.DHUHR, table.current(69, Instant.parse("2026-10-24T09:00:00Z")).prayer)
        // 03:00 UAE: still yesterday's Isha.
        val c = table.current(69, Instant.parse("2026-10-23T23:00:00Z"))
        assertEquals(Prayer.ISHA, c.prayer)
        assertEquals(LocalDate.of(2026, 10, 23), PrayerTimetable.uaeDate(c.at))
    }
}
