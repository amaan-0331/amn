package dev.amaan.amn.widgets.core

import dev.amaan.amn.widgets.data.PrayerBundle
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerSceneTest {
    private val table = PrayerTimetable(PrayerBundle.parse(File("src/main/assets/prayer/uae.json").readText()).zones)

    /** Dubai on 24 Oct 2026 at [hhmm] UAE time: 05:04 06:18 12:06 15:19 17:48 19:02. */
    private fun at(hhmm: String) = Instant.parse("2026-10-24T${hhmm}:00+04:00")
    private fun moment(hhmm: String) = PrayerMoment.of(table, 69, at(hhmm))

    @Test
    fun `sky follows the day's times`() {
        assertEquals(
            listOf(PrayerSky.ISHA, PrayerSky.FAJR, PrayerSky.MORNING, PrayerSky.DHUHR, PrayerSky.ASR, PrayerSky.MAGHRIB, PrayerSky.ISHA),
            listOf("02:00", "05:04", "06:18", "12:06", "15:19", "17:48", "19:02").map { moment(it).sky },
        )
    }

    @Test
    fun `sun crosses from sunrise to maghrib, moon from maghrib to sunrise`() {
        assertEquals(0f, moment("06:18").orb)
        assertEquals(.5f, moment("12:03").orb, .01f)
        assertEquals(0f, moment("17:48").orb)
        // Night is 17:48 to 06:18 (750 min); midnight is 372 min in.
        assertEquals(372f / 750, moment("00:00").orb, .001f)
        assertEquals(Prayer.DHUHR, moment("06:18").next.prayer)
    }

    @Test
    fun `redraws at the next time or quarter hour, whichever is first`() {
        assertEquals(at("10:15"), PrayerMoment.nextRedraw(table, 69, at("10:02")))
        assertEquals(at("06:18"), PrayerMoment.nextRedraw(table, 69, at("06:16")))
        assertEquals(at("06:30"), PrayerMoment.nextRedraw(table, 69, at("06:18")))
        assertEquals(at("19:02"), PrayerMoment.nextRedraw(table, 69, at("19:01")))
    }

    @Test
    fun `the light never leaves text unreadable`() {
        for (sky in PrayerSky.entries) for (dark in listOf(false, true)) {
            val pal = Palettes.prayer(sky, dark)
            assertTrue(Palettes.contrast(pal.accent, pal.base.top) >= Palettes.MIN_CONTRAST, "$sky accent")
            for (f in listOf(0f, .25f, .5f, .75f, 1f)) {
                val orb = PrayerDesign.orb(f, PrayerDesign.W)
                val light = SkyLight.of(sky).readable(pal.base, orb)
                val worst = PrayerDesign.textPoints().minOf { (x, y) -> Palettes.contrast(pal.base.ink, light.skyAt(pal.base, x, y, orb)) }
                assertTrue(worst >= Palettes.MIN_CONTRAST || light.alpha == 0f, "$sky dark=$dark f=$f: $worst")
            }
        }
    }

    @Test
    fun `the light only dims where it must`() {
        // Morning's warm light behind dark text only helps, so it stays at full strength.
        val pal = Palettes.prayer(PrayerSky.MORNING, false)
        val orb = PrayerDesign.orb(.1f, PrayerDesign.W)
        assertEquals(.62f, SkyLight.of(PrayerSky.MORNING).readable(pal.base, orb).alpha)
    }
}
