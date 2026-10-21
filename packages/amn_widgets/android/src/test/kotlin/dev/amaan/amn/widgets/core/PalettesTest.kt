package dev.amaan.amn.widgets.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import java.time.Instant
import java.time.ZoneId

class PalettesTest {
    @Test fun everyStateReadable() {
        for (c in Condition.entries) for (p in Phase.entries) for (dark in listOf(false, true)) {
            val pal = Palettes.of(c, p, dark)
            val worst = minOf(Palettes.contrast(pal.ink, pal.top), Palettes.contrast(pal.ink, pal.bottom))
            assertTrue(worst >= Palettes.MIN_CONTRAST, "$c $p dark=$dark is ${"%.2f".format(worst)}:1")
        }
    }

    @Test fun phases() {
        assertEquals(Phase.DAWN, TimeOfDay.phase(6 * 60 + 40, 6 * 60 + 30, 18 * 60))
        assertEquals(Phase.DAY, TimeOfDay.phase(12 * 60, 6 * 60 + 30, 18 * 60))
        assertEquals(Phase.DUSK, TimeOfDay.phase(17 * 60 + 30, 6 * 60 + 30, 18 * 60))
        assertEquals(Phase.NIGHT, TimeOfDay.phase(23 * 60, 6 * 60 + 30, 18 * 60))
    }

    @Test fun offsets() {
        val at = Instant.parse("2026-10-21T20:00:00Z")
        assertEquals("+7h · tomorrow", CityOffset.between(ZoneId.of("Asia/Tokyo"), ZoneId.of("Europe/Berlin"), at).label)
        assertEquals("−6h", CityOffset.between(ZoneId.of("America/New_York"), ZoneId.of("Europe/Berlin"), at).label)
        assertEquals("+3h 30m", CityOffset.between(ZoneId.of("Asia/Kolkata"), ZoneId.of("Europe/Berlin"), Instant.parse("2026-10-21T10:00:00Z")).label)
        assertEquals("Same time as you", CityOffset.between(ZoneId.of("Europe/Paris"), ZoneId.of("Europe/Berlin"), at).label)
    }
}
