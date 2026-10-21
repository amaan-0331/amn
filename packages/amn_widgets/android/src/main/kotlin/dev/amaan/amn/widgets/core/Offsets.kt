package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Time difference between the clock's city and the phone. */
data class CityOffset(val minutes: Int, val dayShift: Int) {
    /** "+7h · tomorrow", "−5h 30m", "Same time as you". */
    val label: String
        get() {
            val rel = when (dayShift) { 1 -> "tomorrow"; -1 -> "yesterday"; else -> null }
            if (minutes == 0) return if (rel == null) "Same time as you" else "Same time · $rel"
            val a = abs(minutes)
            val off = "${if (minutes > 0) "+" else "−"}${a / 60}h${if (a % 60 != 0) " ${a % 60}m" else ""}"
            return if (rel == null) off else "$off · $rel"
        }

    companion object {
        fun between(city: ZoneId, phone: ZoneId, at: Instant): CityOffset {
            val c = at.atZone(city)
            val p = at.atZone(phone)
            val minutes = (c.offset.totalSeconds - p.offset.totalSeconds) / 60
            val shift = c.toLocalDate().compareTo(p.toLocalDate()).coerceIn(-1, 1)
            return CityOffset(minutes, shift)
        }
    }
}
