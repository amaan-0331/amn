package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a clock widget next needs redrawing: a time-of-day phase change in the city,
 * midnight in the city, or midnight on the phone (the "tomorrow"/"yesterday" label).
 * The time itself ticks on its own in a TextClock.
 */
object ClockTimeline {
    private val fallback = SunDay(6 * 60 + 30, 18 * 60)

    fun phaseAt(now: Instant, city: ZoneId, sun: (LocalDate) -> SunDay?): Phase {
        val local = now.atZone(city)
        val s = sun(local.toLocalDate()) ?: fallback
        return TimeOfDay.phase(local.hour * 60 + local.minute, s.sunriseMin, s.sunsetMin)
    }

    fun nextUpdate(now: Instant, city: ZoneId, phone: ZoneId, sun: (LocalDate) -> SunDay?): Instant {
        val today = now.atZone(city).toLocalDate()
        val candidates = mutableListOf<Instant>()
        for (d in listOf(today, today.plusDays(1))) {
            val s = sun(d) ?: fallback
            for (m in listOf(0, s.sunriseMin - 30, s.sunriseMin + 60, s.sunsetMin - 60, s.sunsetMin + 30)) {
                candidates += d.atStartOfDay(city).plusMinutes(m.toLong()).toInstant()
            }
        }
        candidates += now.atZone(phone).toLocalDate().plusDays(1).atStartOfDay(phone).toInstant()
        return candidates.filter { it.isAfter(now) }.min()
    }
}
